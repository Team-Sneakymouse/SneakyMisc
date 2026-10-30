package com.danidipp.sneakymisc.elevators

import org.bukkit.Location
import org.bukkit.World
import org.mockito.Mockito.mock
import kotlin.test.*

class ElevatorCabTest {
    private val world = mock(World::class.java)

    @Test fun `fixed cab interior excludes doorway corridor adjacent floors and other worlds`() {
        val cab = ElevatorCab.at(Location(world, .5, 60.0, .5))
        assertTrue(cab.contains(Location(world, -1.99, 60.0, 2.99)))
        assertFalse(cab.contains(Location(world, .5, 59.99, .5)))
        assertTrue(cab.contains(Location(world, .5, 64.99, .5)))
        assertFalse(cab.contains(Location(world, .5, 65.0, .5)))
        assertFalse(cab.contains(Location(world, .5, 60.0, 3.0)))
        val otherWorld = mock(World::class.java) // Location only weakly references its world.
        assertFalse(cab.contains(Location(otherWorld, .5, 60.0, .5)))
    }

    @Test fun `both teleport stages preserve relative position facing and pitch at every cardinal rotation`() {
        for (originYaw in listOf(0f, 90f, 180f, -90f)) {
            for (transitYaw in listOf(0f, 90f, 180f, -90f)) {
                val origin = ElevatorCab.at(Location(world, 10.5, 60.0, 20.5, originYaw, 0f))
                val transit = ElevatorCab.at(Location(world, -100.5, -50.0, -200.5, transitYaw, 0f))
                val destination = ElevatorCab.at(Location(world, 40.5, 80.0, 60.5, -90f, 0f))
                val initial = Location(world, 11.25, 60.2, 19.3, 27f, 33f)
                val inTransit = origin.destination(initial, transit)
                val result = transit.destination(inTransit, destination)
                val direct = origin.destination(initial, destination)
                assertEquals(direct.x, result.x, 1e-8)
                assertEquals(direct.y, result.y, 1e-8)
                assertEquals(direct.z, result.z, 1e-8)
                assertEquals(direct.yaw, result.yaw)
                assertEquals(33f, result.pitch)
                assertTrue(transit.contains(inTransit))
                assertTrue(destination.contains(result))
            }
        }
    }

    @Test fun `cab creation snaps center height and cardinal facing without mutating input`() {
        val input = Location(world, -2.2, 70.7, 5.8, 130f, 50f)
        val cab = ElevatorCab.at(input)
        assertEquals(-2.5, cab.location.x)
        assertEquals(70.0, cab.location.y)
        assertEquals(5.5, cab.location.z)
        assertEquals(90f, cab.location.yaw)
        assertEquals(0f, cab.location.pitch)
        assertEquals(-2.2, input.x)
    }
}
