package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.dclock.commands.DClockCommandUtils
import com.danidipp.sneakymisc.dclock.commands.DClockRootCommand
import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.BaseRecord
import com.danidipp.sneakypocketbase.PBRunnable
import com.danidipp.sneakypocketbase.SneakyPocketbase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.io.File
import java.util.logging.Logger

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class SettingRecord(
    val key: String = "",
    var value: Long = 0L,
) : BaseRecord()

data class ClockRuntime(
    val name: String,
    val source: ClockSource,
    val overtimeCommand: String?,
    val displays: MutableMap<String, DisplayRuntime>,
    var previousSecondsRemaining: Long? = null,
)

data class DeleteResult(
    val removed: Int,
    val orphaned: Int,
)

class DClockModule(private val logger: Logger) : SneakyModule(), Listener {
    companion object { val deps = listOf<String>("SneakyPocketbase", "MagicSpells") }
    sealed interface SetOvertimeBehaviorResult {
        data object NotFound : SetOvertimeBehaviorResult
        data object Ambiguous : SetOvertimeBehaviorResult
        data object Updated : SetOvertimeBehaviorResult
    }

    sealed interface SetClockOvertimeCommandResult {
        data object NotFound : SetClockOvertimeCommandResult
        data class Updated(val previousCommand: String?) : SetClockOvertimeCommandResult
    }

    override val commands: List<SneakyMiscCommand> = listOf(SneakyMiscCommand(DClockRootCommand(this).build(
        DClockCommandUtils(this)).build(),"Manage DClock countdown displays"))
    override val listeners: List<Listener> = listOf(this)

    private val plugin = SneakyMisc.getInstance()
    private val configFile = File(plugin.dataFolder, "dclock.yml")

    @Volatile
    private var config = DClockConfig(mutableMapOf())

    @Volatile
    private var clocks = linkedMapOf<String, ClockRuntime>()

    @Volatile
    private var pocketbaseSourcesByRecord = emptyMap<String, List<PocketBaseClockSource>>()

    init {
        ensureConfigFile()
        loadConfig()

        val sneakyPB = SneakyPocketbase.getInstance()
        sneakyPB.onPocketbaseLoaded { subscribeToPocketbase() }

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            refreshAllDisplays()
        }, 0L, 20L)
    }

    fun reload() {
        loadConfig()
        subscribeToPocketbase()
        refreshAllDisplays()
    }

    fun getClockNames(): List<String> {
        return config.clocks.keys.sorted()
    }

    fun getDisplayNames(clockName: String? = null): List<String> {
        return if (clockName == null) {
            config.clocks.values.flatMap { it.displays.keys }.distinct().sorted()
        } else {
            config.clocks[clockName]?.displays?.keys?.sorted() ?: emptyList()
        }
    }

    fun createClock(clockName: String, source: SourceConfig): Boolean {
        if (config.clocks.containsKey(clockName)) return false

        config.clocks[clockName] = ClockConfig(source, null, linkedMapOf<String, DisplayConfig>().toMutableMap())
        saveAndReload()
        return true
    }

    fun deleteClock(clockName: String): DeleteResult? {
        val clockConfig = config.clocks.remove(clockName) ?: return null
        val totals = clockConfig.displays.values.fold(DeleteResult(0, 0)) { acc, display ->
            val (removed, orphaned) = DClockDisplay.removeLoadedEntities(display.digits)
            DeleteResult(acc.removed + removed, acc.orphaned + orphaned)
        }
        saveAndReload()
        return totals
    }

    fun setClockOvertimeCommand(clockName: String, command: String?): SetClockOvertimeCommandResult {
        val clock = config.clocks[clockName] ?: return SetClockOvertimeCommandResult.NotFound
        val previousCommand = clock.overtimeCommand
        clock.overtimeCommand = command
        saveAndReload()
        return SetClockOvertimeCommandResult.Updated(previousCommand)
    }

    fun createDisplay(clockName: String, displayName: String, size: Int, player: Player): Boolean? {
        val clock = config.clocks[clockName] ?: return null
        if (clock.displays.containsKey(displayName)) {
            return false
        }

        val entityIds = DClockDisplay.spawnDisplayEntities(player, size)
        clock.displays[displayName] = DisplayConfig(
            overtimeBehavior = OvertimeBehavior.FREEZE,
            digits = entityIds.toMutableList(),
        )
        saveAndReload()
        return true
    }

    fun deleteDisplay(clockName: String, displayName: String): DeleteResult? {
        val clock = config.clocks[clockName] ?: return null
        val display = clock.displays.remove(displayName) ?: return null
        val (removed, orphaned) = DClockDisplay.removeLoadedEntities(display.digits)
        saveAndReload()
        return DeleteResult(removed, orphaned)
    }

    fun setDisplayOvertimeBehavior(displayName: String, behavior: OvertimeBehavior): SetOvertimeBehaviorResult {
        val matches = config.clocks.filter { (_, clock) -> clock.displays.containsKey(displayName) }
        if (matches.isEmpty()) return SetOvertimeBehaviorResult.NotFound
        if (matches.size > 1) return SetOvertimeBehaviorResult.Ambiguous

        val (clockName, clockConfig) = matches.entries.first()
        clockConfig.displays[displayName]?.overtimeBehavior = behavior
        saveAndReload()
        refreshDisplay(clockName, displayName)
        return SetOvertimeBehaviorResult.Updated
    }

    @EventHandler
    fun onPocketBaseUpdate(event: AsyncPocketbaseEvent) {
        if (event.collectionName != "settings") return

        val record = runCatching {
            event.data.parseRecord<SettingRecord>(Json { ignoreUnknownKeys = true })
        }.getOrElse {
            logger.warning("Failed to parse DClock settings update: ${it.message}")
            return
        }

        val sources = pocketbaseSourcesByRecord[record.recordId].orEmpty()
        if (sources.isEmpty()) return
        for (source in sources) {
            source.sourceValue = record.value
        }
    }

    private fun ensureConfigFile() {
        if (configFile.exists()) return
        configFile.parentFile.mkdirs()
        configFile.createNewFile()
    }

    private fun saveAndReload() {
        config.save(configFile)
        loadConfig()
        subscribeToPocketbase()
        refreshAllDisplays()
    }

    private fun loadConfig() {
        ensureConfigFile()
        val loadedConfig = DClockConfig.load(configFile, logger)
        config = loadedConfig

        val builtClocks = linkedMapOf<String, ClockRuntime>()
        val sourceIndex = linkedMapOf<String, MutableList<PocketBaseClockSource>>()

        for ((clockName, clockConfig) in loadedConfig.clocks) {
            val source = when (val sourceConfig = clockConfig.source) {
                is PocketBaseSourceConfig -> {
                    val pocketBaseSource = PocketBaseClockSource(sourceConfig.recordId, sourceConfig.valueMode)
                    sourceIndex.getOrPut(sourceConfig.recordId) { mutableListOf() }.add(pocketBaseSource)
                    pocketBaseSource
                }

                is MagicSpellsSourceConfig -> MagicSpellsClockSource(sourceConfig.variableName, sourceConfig.valueMode)
            }

            val displays = linkedMapOf<String, DisplayRuntime>()
            for ((displayName, displayConfig) in clockConfig.displays) {
                displays[displayName] = DisplayRuntime(
                    name = displayName,
                    overtimeBehavior = displayConfig.overtimeBehavior,
                    entityIds = displayConfig.digits.toList(),
                )
            }

            builtClocks[clockName] = ClockRuntime(
                name = clockName,
                source = source,
                overtimeCommand = clockConfig.overtimeCommand,
                displays = displays.toMutableMap(),
            )
        }

        clocks = builtClocks
        pocketbaseSourcesByRecord = sourceIndex.mapValues { it.value.toList() }
        logger.info("Loaded ${clocks.size} DClock clocks")
    }

    private fun subscribeToPocketbase() {
        val pb = SneakyPocketbase.getInstance()
        pb.unsubscribeAsync("settings")
        pb.subscribeAsync("settings")
        refreshPocketbaseCaches()
    }

    private fun refreshPocketbaseCaches() {
        val sourcesByRecordSnapshot = pocketbaseSourcesByRecord
        if (sourcesByRecordSnapshot.isEmpty()) return

        for (sources in sourcesByRecordSnapshot.values) {
            for (source in sources) {
                source.sourceValue = null
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, PBRunnable {
            val pb = SneakyPocketbase.getInstance()
            for ((recordId, sources) in sourcesByRecordSnapshot) {
                val record = runCatching {
                    pb.pb().records.getOne<SettingRecord>("settings", recordId)
                }.getOrElse {
                    logger.warning("Failed to fetch DClock settings record '$recordId': ${it.message}")
                    null
                }

                val value = record?.value
                for (source in sources) {
                    source.sourceValue = value
                }
            }
        })
    }

    private fun refreshAllDisplays() {
        val nowMillis = System.currentTimeMillis()
        for (clock in clocks.values) {
            val value = clock.source.currentValue(nowMillis) ?: continue
            maybeRunOvertimeCommand(clock, value)
            for (display in clock.displays.values) {
                DClockDisplay.updateDisplay(DClockDisplay.renderValue(display, value))
            }
        }
    }

    private fun refreshDisplay(clockName: String, displayName: String) {
        val clock = clocks[clockName] ?: return
        val display = clock.displays[displayName] ?: return
        val value = clock.source.currentValue(System.currentTimeMillis()) ?: return
        DClockDisplay.updateDisplay(DClockDisplay.renderValue(display, value))
    }

    private fun maybeRunOvertimeCommand(clock: ClockRuntime, value: ClockValue) {
        val secondsRemaining = (value as? ClockValue.TimeLeft)?.secondsRemaining ?: run {
            clock.previousSecondsRemaining = null
            return
        }
        val previous = clock.previousSecondsRemaining
        clock.previousSecondsRemaining = secondsRemaining

        if (previous == null || previous < 0L || secondsRemaining >= 0L) return

        val command = clock.overtimeCommand
            ?.trim()
            ?.removePrefix("/")
            ?.trim()
            .orEmpty()

        if (command.isBlank()) return

        runCatching {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }.onFailure {
            logger.warning("Failed to run DClock overtime command for '${clock.name}': ${it.message}")
        }
    }
}
