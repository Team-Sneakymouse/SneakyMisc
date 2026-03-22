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
    override val commands: List<Command> = listOf() //dclockcommand
    override val listeners: List<Listener> = listOf(this)

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
            logger.info("Pocketbase loaded, subscribing to settings collection")
            sneakyPB.subscribeAsync("settings")
            Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), PBRunnable {
                val current = sneakyPB.pb().records.getFullList<SettingRecord>("settings", 100)
                // TODO: init clock cache
            })
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(SneakyMisc.getInstance(), {
            // TODO: fetch clock seconds and update displays
        }, 0, 20)
    }

    @EventHandler
    fun onPBUpdate(event: AsyncPocketbaseEvent) {
        if (event.collectionName != "settings") return
        val record = event.data.parseRecord<SettingRecord>(Json { ignoreUnknownKeys = true })
        // TODO: check record id and update clock cache
    }
}
