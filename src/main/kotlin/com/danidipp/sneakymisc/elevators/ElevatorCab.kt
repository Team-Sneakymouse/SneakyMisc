package com.danidipp.sneakymisc.elevators

import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.math.floor
import kotlin.math.round

/** Fixed five-by-five interior, four blocks high. The anchor is the center at feet level. */
class ElevatorCab(val location: Location) {
    fun contains(position: Location): Boolean {
        if (position.world != location.world) return false
        val offset = position.toVector().subtract(location.toVector())
        return offset.x >= -2.5 && offset.x < 2.5 &&
            offset.z >= -2.5 && offset.z < 2.5 && offset.y >= 0.0 && offset.y < 4.0
    }

    fun players(): List<Player> = location.world.players.filter { contains(it.location) }

    fun destination(position: Location, target: ElevatorCab): Location {
        val yawDifference = target.location.yaw - location.yaw
        val offset = position.toVector().subtract(location.toVector())
            .rotateAroundY(-Math.toRadians(yawDifference.toDouble()))
        return target.location.clone().add(offset).apply {
            yaw = position.yaw + yawDifference
            pitch = position.pitch
        }
    }

    fun overlaps(other: ElevatorCab): Boolean = location.world == other.location.world &&
        kotlin.math.abs(location.x - other.location.x) < 5.0 &&
        kotlin.math.abs(location.z - other.location.z) < 5.0 &&
        location.y < other.location.y + 4.0 && other.location.y < location.y + 4.0

    companion object {
        fun at(position: Location): ElevatorCab = ElevatorCab(position.clone().apply {
            x = floor(x) + 0.5
            y = floor(y)
            z = floor(z) + 0.5
            yaw = round(yaw / 90.0f) * 90.0f
            pitch = 0f
        })
    }
}

class ElevatorTransitCab(val name: String, var style: String, val cab: ElevatorCab)
