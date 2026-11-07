package com.danidipp.sneakymisc.dclock

import com.sneakyutils.util.Direction
import com.sneakyutils.util.PoseFacing
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.floor

/** One in-world clock display: a row of ItemDisplay digit entities, left to right. */
data class DisplayRuntime(
    val name: String,
    val entityIds: List<UUID>,
)

object DClockDisplay {
    private data class CardinalTransform(
        val forward: Vector,
        val right: Vector,
        val yaw: Float,
    )

    /**
     * The pack's own item definition for each digit (craftengine-resources: lom/items/legacy/clay_ball/400..409).
     * Using it directly instead of clay_ball + custom_model_data keeps the digits visible when any other
     * clay_ball route breaks the shared clay_ball definition on the client.
     */
    fun itemModel(digit: Char): NamespacedKey = NamespacedKey("lom", "legacy/clay_ball/${400 + (digit - '0')}")

    private fun createItem(digit: Char): ItemStack {
        val item = ItemStack(Material.CLAY_BALL)
        item.itemMeta = item.itemMeta.apply { setItemModel(itemModel(digit)) }
        return item
    }

    val digitItems by lazy { ('0'..'9').associateWith(::createItem) }

    /** Zero-padded to [width]; all nines when the value does not fit. The clock never goes below 0. */
    fun digits(value: Long, width: Int): String {
        val raw = maxOf(0L, value).toString()
        if (raw.length > width) return "9".repeat(width)
        return raw.padStart(width, '0')
    }

    fun updateDisplay(display: DisplayRuntime, value: Long) {
        val digits = digits(value, display.entityIds.size)
        for ((index, uuid) in display.entityIds.withIndex()) {
            val entity = Bukkit.getEntity(uuid) as? ItemDisplay ?: continue
            entity.setItemStack(digitItems[digits[index]]?.clone())
        }
    }

    private fun getCardinalTransform(player: Player): CardinalTransform {
        return when (PoseFacing.getDirection(player.location)) {
            Direction.NORTH -> CardinalTransform(
                forward = Vector(0, 0, -1),
                right = Vector(1, 0, 0),
                yaw = -90.0f,
            )

            Direction.EAST -> CardinalTransform(
                forward = Vector(1, 0, 0),
                right = Vector(0, 0, 1),
                yaw = 0.0f,
            )

            Direction.WEST -> CardinalTransform(
                forward = Vector(-1, 0, 0),
                right = Vector(0, 0, -1),
                yaw = 180.0f,
            )

            else -> CardinalTransform(
                forward = Vector(0, 0, 1),
                right = Vector(-1, 0, 0),
                yaw = 90.0f,
            )
        }
    }

    private fun snapToHalfBlock(value: Double): Double {
        return floor(value) + 0.5
    }

    fun spawnDisplayEntities(player: Player, size: Int): List<UUID> {
        val world = player.world
        val eyeLocation = player.eyeLocation
        val transform = getCardinalTransform(player)
        val base = eyeLocation.clone().add(transform.forward.clone().multiply(1.5)).apply {
            x = snapToHalfBlock(x)
            y = floor(y * 2.0) / 2.0
            z = snapToHalfBlock(z)
            yaw = transform.yaw
            pitch = 0.0f
        }
        val step = 1.0
        val centeredOffset = (size - 1) / 2.0
        val uuids = mutableListOf<UUID>()

        for (index in 0 until size) {
            val spawnLocation = base.clone().add(transform.right.clone().multiply((index - centeredOffset) * step)).apply {
                yaw = transform.yaw
                pitch = 0.0f
            }
            val entity = world.spawn(spawnLocation, ItemDisplay::class.java) { itemDisplay ->
                itemDisplay.isPersistent = true
                itemDisplay.setItemStack(digitItems['0']?.clone())
                itemDisplay.setGravity(false)
                itemDisplay.setRotation(transform.yaw, 0.0f)
                itemDisplay.transformation = Transformation(
                    Vector3f(),
                    AxisAngle4f(),
                    Vector3f(1.0f, 1.0f, 1.0f),
                    AxisAngle4f(),
                )
            }
            uuids += entity.uniqueId
        }

        return uuids
    }

    fun removeLoadedEntities(entityIds: Iterable<UUID>): Pair<Int, Int> {
        var removed = 0
        var orphaned = 0
        for (uuid in entityIds) {
            val entity = Bukkit.getEntity(uuid)
            if (entity == null) {
                orphaned++
                continue
            }
            entity.remove()
            removed++
        }
        return removed to orphaned
    }
}
