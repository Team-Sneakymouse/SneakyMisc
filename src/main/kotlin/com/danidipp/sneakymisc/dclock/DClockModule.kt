package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.dclock.commands.DClockCommandUtils
import com.danidipp.sneakymisc.dclock.commands.DClockRootCommand
import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.AsyncPocketbaseLifecycleEvent
import com.danidipp.sneakypocketbase.PocketbaseProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.io.File
import java.util.logging.Logger
import kotlin.math.floor

object FlooredLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlooredLong", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Long {
        val value = decoder.decodeDouble()
        if (!value.isFinite()) {
            throw SerializationException("Expected a finite numeric value, found $value")
        }

        val floored = floor(value)
        if (floored < Long.MIN_VALUE.toDouble() || floored >= 9_223_372_036_854_775_808.0) {
            throw SerializationException("Numeric value is outside the Long range: $value")
        }

        return floored.toLong()
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}

/** The Doom Clock's `settings` row (key [DClockModule.SETTINGS_KEY]); value is whole seconds left. */
@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class SettingRecord(
    val key: String = "",
    @Serializable(with = FlooredLongSerializer::class)
    val value: Long = 0L,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Seconds left from a `settings` record, or null if the record is another setting or has
         * no value. Other settings rows hold arbitrary JSON, so the key is checked before decoding.
         * Throws on a dclock row whose value is not a number.
         */
        fun dclockValue(recordJson: String): Long? {
            val record = json.parseToJsonElement(recordJson).jsonObject
            if (record["key"]?.jsonPrimitive?.content != DClockModule.SETTINGS_KEY) return null
            if (record["value"] == null || record["value"] == JsonNull) return null
            return json.decodeFromJsonElement(serializer(), JsonObject(record)).value
        }
    }
}

data class DeleteResult(
    val removed: Int,
    val orphaned: Int,
)

/**
 * The Doom Clock. PocketBase owns the value and counts it down (see the pocketbase repo's
 * dclock hooks); this module only shows it on in-world displays and placeholders, and forwards
 * `/dclock add|set` as rows in `dclock_adjustments`. While PocketBase is unreachable the last
 * value stays on display; it is fetched again once the settings subscription is ready.
 */
class DClockModule(private val logger: Logger) : SneakyModule(), Listener {
    companion object {
        val deps = listOf<String>("SneakyPocketbase")
        const val SETTINGS_KEY = "dclock"
        const val ADJUSTMENTS_COLLECTION = "dclock_adjustments"
    }

    override val commands: List<SneakyMiscCommand> = listOf(SneakyMiscCommand(DClockRootCommand(this).build(
        DClockCommandUtils(this)).build(),"Manage the Doom Clock and its displays"))
    override val listeners: List<Listener> = listOf(this)

    private val plugin = SneakyMisc.getInstance()
    private val configFile = File(plugin.dataFolder, "dclock.yml")

    // Both are replaced as a whole, never mutated, so async readers (PAPI, tab completion) are safe.
    @Volatile
    private var config = DClockConfig(emptyMap())

    @Volatile
    private var displays = emptyList<DisplayRuntime>()

    /** Last known seconds left, or null before the first fetch. */
    @Volatile
    var value: Long? = null
        private set

    init {
        loadConfig()

        PocketbaseProvider.getApi().whenReady {
            PocketbaseProvider.getApi().subscribe("settings")
            fetchValue()
        }

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { refreshAllDisplays() }, 0L, 20L)
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) DClockPlaceholders(this).register()
    }

    /** What %dclock_value% or %dclock_<display>% shows, or null when unknown. */
    fun placeholder(what: String): String? {
        val current = value ?: return null
        if (what == "value") return current.toString()
        val display = displays.firstOrNull { it.name == what } ?: return null
        return DClockDisplay.digits(current, display.entityIds.size)
    }

    fun reload() {
        loadConfig()
        fetchValue()
        refreshAllDisplays()
    }

    fun getDisplayNames(): List<String> = displays.map { it.name }.sorted()

    /** Why displays cannot be edited right now, or null if they can. */
    fun editBlockedReason(): String? =
        config.problems.takeIf { it.isNotEmpty() }?.let { "dclock.yml did not load cleanly; fix it and /dclock reload: ${it.joinToString("; ")}" }

    /** Adds [seconds] (may be negative or fractional) to the clock. Result is clamped at 0 by PocketBase. */
    fun add(seconds: Double, source: String) = adjust("add", seconds, source)

    fun set(seconds: Double, source: String) = adjust("set", seconds, source)

    private fun adjust(mode: String, amount: Double, source: String) {
        val record = buildJsonObject {
            put("mode", mode)
            put("amount", amount)
            put("source", source.take(200))
        }.toString()
        PocketbaseProvider.getApi().create(ADJUSTMENTS_COLLECTION, record).exceptionally { failure ->
            // Unwrap CompletionException. SneakyPocketbase's message says whether the create may have applied.
            val cause = generateSequence(failure) { it.cause }.first { it !is java.util.concurrent.CompletionException }
            logger.warning("DClock $mode $amount ($source) failed: ${cause.javaClass.simpleName}: ${cause.message}")
            null
        }
    }

    /** Call only after [editBlockedReason] returned null and the name matched [DClockConfig.DISPLAY_NAME]. */
    fun createDisplay(displayName: String, size: Int, player: Player): Boolean {
        if (config.displays.containsKey(displayName)) return false
        val digits = DClockDisplay.spawnDisplayEntities(player, size)
        saveAndReload(config.copy(displays = config.displays + (displayName to digits)))
        return true
    }

    /** Call only after [editBlockedReason] returned null. */
    fun deleteDisplay(displayName: String): DeleteResult? {
        val digits = config.displays[displayName] ?: return null
        val (removed, orphaned) = DClockDisplay.removeLoadedEntities(digits)
        saveAndReload(config.copy(displays = config.displays - displayName))
        return DeleteResult(removed, orphaned)
    }

    @EventHandler
    fun onPocketBaseUpdate(event: AsyncPocketbaseEvent) {
        if (event.collectionName != "settings") return
        if (event.action == AsyncPocketbaseEvent.Action.DELETE) return
        val seconds = runCatching { SettingRecord.dclockValue(event.recordJson) }.getOrElse {
            logger.warning("Failed to parse DClock value: ${it.message}")
            return
        }
        if (seconds != null) value = seconds
    }

    /** Realtime updates are missed while disconnected, so fetch again whenever the subscription becomes ready. */
    @EventHandler
    fun onPocketbaseLifecycle(event: AsyncPocketbaseLifecycleEvent) {
        if (event.current.isCollectionReady("settings") && !event.previous.isCollectionReady("settings")) fetchValue()
    }

    private fun fetchValue() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            runCatching {
                val records = PocketbaseProvider.getApi().getFullList("settings", 1, "", "key = \"$SETTINGS_KEY\"").join()
                val record = records.firstOrNull() ?: return@runCatching logger.warning("No '$SETTINGS_KEY' settings record")
                SettingRecord.dclockValue(record)?.let { value = it }
            }.onFailure { logger.warning("Failed to fetch DClock value: ${it.message}") }
        })
    }

    private fun saveAndReload(updated: DClockConfig) {
        updated.save(configFile)
        loadConfig()
        refreshAllDisplays()
    }

    private fun loadConfig() {
        val loaded = DClockConfig.load(configFile)
        for (problem in loaded.problems) logger.severe("dclock.yml: $problem (display edits are disabled until fixed)")
        config = loaded
        displays = loaded.displays.map { (name, digits) -> DisplayRuntime(name, digits) }
        logger.info("Loaded ${displays.size} DClock displays")
    }

    private fun refreshAllDisplays() {
        val current = value ?: return
        for (display in displays) DClockDisplay.updateDisplay(display, current)
    }
}
