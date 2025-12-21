package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakypocketbase.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.logging.Logger
import kotlin.math.absoluteValue

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class SettingRecord(
    val key: String,
    var value: Long,
): BaseRecord()


class DClockModule(val logger: Logger): SneakyModule, Listener {
    override val commands: List<Command> = listOf(object : Command("dclock-setup") {
        init {
            description = "DClock setup command"
            usageMessage = "/dclock-setup"
            permission = "sneakymisc.dclock-setup"
        }
        fun createEntity(location: Location, id: Int) {
            val digit = location.world.spawn(location, ItemDisplay::class.java) {
                it.setItemStack(digitItems['0'])
                it.brightness = Display.Brightness(15, 15)
                it.transformation = Transformation(
                    /* translation    */ Vector3f(0.0f, 0.0f, 0.0f),
                    /* left rotation  */ AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f),
                    /* scale          */ Vector3f(2.5f, 2.5f, 2.5f),
                    /* right rotation */ AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
                )
                it.persistentDataContainer.set(DCLOCK_KEY, PersistentDataType.INTEGER, id)
            }
            digitEntities[id] = digit
        }
        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
            val world = Bukkit.getWorld("world") ?: return false
            val locations = mapOf(
//                9 to Location(world, 5413.75, 458.5, 4984.0),
                8 to Location(world, 5413.75, 458.5, 4985.0),
                7 to Location(world, 5413.75, 458.5, 4987.0),
                6 to Location(world, 5413.75, 458.5, 4989.0),
                5 to Location(world, 5413.75, 458.5, 4991.0),
                4 to Location(world, 5413.75, 458.5, 4993.0),
                3 to Location(world, 5413.75, 458.5, 4995.0),
                2 to Location(world, 5413.75, 458.5, 4997.0),
                1 to Location(world, 5413.75, 458.5, 4999.0),
                0 to Location(world, 5413.75, 458.5, 5001.0),
            )
            for ((i, location) in locations) {
                if (digitEntities.containsKey(i)) {
                    sender.sendMessage("Digit 1e$i already exists")
                    continue
                }
                if (!location.chunk.isLoaded) {
                    sender.sendMessage("Chunk not loaded")
                    break
                }
                createEntity(location, i)
            }
            return true
        }
    })
    override val listeners: List<Listener> = listOf(this)

    val DCLOCK_KEY = NamespacedKey(SneakyMisc.getInstance(), "dclock-digit")
    val DCLOCK_COLLECTION = "settings"
    val DCLOCK_RECORD_ID = "9195r6z2omp337a"
    val digitEntities = mutableMapOf<Int, ItemDisplay>()
    var targetTimestamp = 1735189200000L

    fun createItem(digit: Char): ItemStack {
        val item = ItemStack(Material.CLAY_BALL)
        item.itemMeta = item.itemMeta.apply {
            setCustomModelData(when(digit) {
                '0' -> 400
                '1' -> 401
                '2' -> 402
                '3' -> 403
                '4' -> 404
                '5' -> 405
                '6' -> 406
                '7' -> 407
                '8' -> 408
                '9' -> 409
                else -> 400
            })
        }
        return item
    }
    val digitItems = mapOf(
        '0' to createItem('0'),
        '1' to createItem('1'),
        '2' to createItem('2'),
        '3' to createItem('3'),
        '4' to createItem('4'),
        '5' to createItem('5'),
        '6' to createItem('6'),
        '7' to createItem('7'),
        '8' to createItem('8'),
        '9' to createItem('9')
    )

    init {
        val sneakyPB = SneakyPocketbase.getInstance()
        sneakyPB.onPocketbaseLoaded {
            logger.info("Pocketbase loaded, subscribing to record")
            sneakyPB.subscribeAsync("$DCLOCK_COLLECTION/$DCLOCK_RECORD_ID")
            Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), PBRunnable {
                val current = sneakyPB.pb().records.getOne<SettingRecord>(DCLOCK_COLLECTION, DCLOCK_RECORD_ID)
                targetTimestamp = current.value * 1000
                logger.info("Loaded target timestamp $targetTimestamp")
            })
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(SneakyMisc.getInstance(), {
            val now = System.currentTimeMillis()
            if (now >= targetTimestamp) {
                render("000000000")
                return@scheduleSyncRepeatingTask
            }
            val diff = targetTimestamp - now
            val seconds = diff / 1000
            render(seconds.toString())
        }, 0, 20)
    }

    fun render(seconds: String) {
        if (seconds.length > digitEntities.size) // render all nines
            return render('9'.toString().repeat(digitEntities.size))
        if (seconds.length < digitEntities.size) // pad with zeros
            return render(seconds.padStart(digitEntities.size, '0'))
        for ((index, itemDisplay) in digitEntities) {
            val digit = digitEntities.size - index - 1
            itemDisplay.setItemStack(digitItems[seconds[digit]] ?: digitItems['0'])
        }
    }

    @EventHandler
    fun onPBUpdate(event: AsyncPocketbaseEvent) {
        if (event.collectionName != DCLOCK_COLLECTION) return
        val record = event.data.parseRecord<SettingRecord>(Json { ignoreUnknownKeys = true })
        if (record.id != DCLOCK_RECORD_ID) return logger.warning("DClockModule: Received record with wrong ID: ${record.recordId}")

        targetTimestamp = record.value * 1000
        logger.fine("Updated dclock timestamp to $targetTimestamp")
    }

    @EventHandler
    fun onEntityLoad(event: ChunkLoadEvent) {
        val entities = event.chunk.entities
        for (entity in entities) {
            if (entity is ItemDisplay && entity.persistentDataContainer.has(DCLOCK_KEY, PersistentDataType.INTEGER)) {
                val digitId = entity.persistentDataContainer.get(DCLOCK_KEY, PersistentDataType.INTEGER)!!
                digitEntities[digitId] = entity
            }
        }
    }
    @EventHandler
    fun onEntityUnload(event: ChunkUnloadEvent) {
        val entities = event.chunk.entities
        for (entity in entities) {
            if (entity is ItemDisplay && entity.persistentDataContainer.has(DCLOCK_KEY, PersistentDataType.INTEGER)) {
                val digitId = entity.persistentDataContainer.get(DCLOCK_KEY, PersistentDataType.INTEGER)!!
                digitEntities.remove(digitId)
            }
        }
    }
}
