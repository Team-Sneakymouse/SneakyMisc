package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.Direction
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.floor

data class DisplayRuntime(
    val name: String,
    var overtimeBehavior: OvertimeBehavior,
    val entityIds: List<UUID>,
)

data class RenderedDisplay(
    val display: DisplayRuntime,
    val digits: String,
)

object DClockDisplay {
    private data class CardinalTransform(
        val forward: Vector,
        val right: Vector,
        val yaw: Float,
    )

    private fun createItem(digit: Char): ItemStack {
        val item = ItemStack(Material.CLAY_BALL)
        item.itemMeta = item.itemMeta.apply {
            setCustomModelData(
                when (digit) {
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
                }
            )
        }
        return item
    }

    val digitItems = ('0'..'9').associateWith(::createItem)

    fun renderValue(display: DisplayRuntime, value: ClockValue): RenderedDisplay {
        val width = display.entityIds.size
        val adjusted = when (value) {
            is ClockValue.Literal -> value.value
            is ClockValue.TimeLeft -> when {
                value.secondsRemaining >= 0L -> value.secondsRemaining
                display.overtimeBehavior == OvertimeBehavior.FREEZE -> 0L
                else -> value.secondsRemaining.absoluteValue
            }
        }
        val digits = normalizeValue(adjusted, width)
        return RenderedDisplay(display, digits)
    }

    fun normalizeValue(value: Long, width: Int): String {
        val raw = value.toString()
        if (raw.length > width) {
            return "9".repeat(width)
        }
        return raw.padStart(width, '0')
    }

    fun updateDisplay(rendered: RenderedDisplay) {
        for ((index, uuid) in rendered.display.entityIds.withIndex()) {
            val entity = Bukkit.getEntity(uuid) as? ItemDisplay ?: continue
            val digit = rendered.digits.getOrNull(index) ?: continue
            entity.setItemStack(digitItems[digit]?.clone())
        }
    }

    private fun getCardinalTransform(player: Player): CardinalTransform {
        return when (Direction.getDirection(player.location)) {
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
