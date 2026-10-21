package com.danidipp.sneakymisc.elevators

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor

class Elevator(val name: String, var style: String) {
    private val floors = linkedMapOf<String, ElevatorFloor>()

    fun getFloor(id: String): ElevatorFloor? = floors[id]
    fun getFloors(): Collection<ElevatorFloor> = floors.values
    internal fun addFloor(floor: ElevatorFloor) { floors[floor.id] = floor }
    internal fun removeFloor(floor: ElevatorFloor) { floors.remove(floor.id) }

    fun formatComponent(): Component = Component.text("Elevator $name", NamedTextColor.AQUA)
        .append(Component.text(" (style: $style)", NamedTextColor.GRAY)).appendNewline()
        .append(Component.join(JoinConfiguration.commas(true), floors.values.map { it.toComponent() }))
}
