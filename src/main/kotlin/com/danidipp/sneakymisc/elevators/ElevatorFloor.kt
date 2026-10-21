package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask

class ElevatorFloor(
    val id: String,
    val cab: ElevatorCab,
    val elevator: Elevator,
    schedule: (Long, Runnable) -> BukkitTask = { delay, action ->
        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), action, delay)
    },
) {
    private val doors = ElevatorDoors(id, cab, schedule)
    val location: Location get() = cab.location
    val isOpen: Boolean get() = doors.isOpen

    internal fun open() = doors.open()
    internal fun close(closed: () -> Unit = {}): ElevatorTask = doors.close(closed)
    internal fun ensureDoors() = doors.ensureDoors()
    internal fun initialize() = doors.initialize()
    internal fun stop() = doors.stop()
    internal fun cleanup() = doors.cleanup()

    fun toComponent(): TextComponent = Component.text(id, NamedTextColor.AQUA)
        .hoverEvent(Component.text("${location.world.name}: ${location.blockX}, ${location.blockY}, ${location.blockZ}\nClick to teleport"))
        .clickEvent(ClickEvent.runCommand("/minecraft:tp @s ${location.x} ${location.y} ${location.z} ${location.yaw} ${location.pitch}"))

    companion object {
        const val DOOR_DURATION = ElevatorDoors.DOOR_DURATION
        val ELEVATOR_KEY = ElevatorDoors.ELEVATOR_KEY
    }
}
