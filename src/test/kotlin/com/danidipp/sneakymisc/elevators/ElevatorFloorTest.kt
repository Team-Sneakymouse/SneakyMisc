package com.danidipp.sneakymisc.elevators

import kotlin.test.*
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.data.MultipleFacing
import org.bukkit.entity.BlockDisplay
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import org.mockito.Mockito.*

class ElevatorFloorTest {
    private class Ticks {
        class Scheduled(val due: Long, val action: Runnable, val handle: BukkitTask) {
            var cancelled = false
            var fired = false
        }
        var now = 0L
            private set
        val tasks = mutableListOf<Scheduled>()

        fun schedule(delay: Long, action: Runnable): BukkitTask {
            val handle = mock(BukkitTask::class.java)
            val task = Scheduled(now + delay, action, handle)
            doAnswer { task.cancelled = true; null }.`when`(handle).cancel()
            tasks.add(task)
            return handle
        }

        fun advance(ticks: Long) {
            val target = now + ticks
            while (true) {
                val task = tasks.filter { !it.cancelled && !it.fired && it.due <= target }.minByOrNull { it.due } ?: break
                now = task.due
                task.fired = true
                task.action.run()
            }
            now = target
        }

        fun pending() = tasks.filter { !it.fired && !it.cancelled }
    }

    private class Fixture {
        val ticks = Ticks()
        val world = mock(World::class.java)
        val materials = mutableMapOf<Triple<Int, Int, Int>, Material>()
        private val blocks = mutableMapOf<Triple<Int, Int, Int>, Block>()
        val left = door("left")
        val right = door("right")
        val floor: ElevatorFloor

        init {
            val chunk = mock(Chunk::class.java)
            `when`(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk)
            `when`(world.getChunkAt(any(Location::class.java))).thenReturn(chunk)
            `when`(chunk.entities).thenReturn(arrayOf(left, right))
            `when`(world.getBlockAt(any(Location::class.java))).thenAnswer {
                val location = it.getArgument<Location>(0)
                world.getBlockAt(location.blockX, location.blockY, location.blockZ)
            }
            `when`(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer { call ->
                val key = Triple(call.getArgument<Int>(0), call.getArgument<Int>(1), call.getArgument<Int>(2))
                blocks.getOrPut(key) {
                    val block = mock(Block::class.java)
                    `when`(block.blockData).thenReturn(mock(MultipleFacing::class.java))
                    doAnswer { materials[key] = it.getArgument(0); null }.`when`(block).setType(any(Material::class.java), eq(false))
                    block
                }
            }
            floor = ElevatorFloor("tower-1", ElevatorCab.at(Location(world, .5, 60.0, .5)), Elevator("tower", "stone"), ticks::schedule)
            floor.initialize()
        }

        private fun door(side: String): BlockDisplay {
            val door = mock(BlockDisplay::class.java)
            val pdc = mock(PersistentDataContainer::class.java)
            `when`(door.persistentDataContainer).thenReturn(pdc)
            `when`(pdc.get(ElevatorFloor.ELEVATOR_KEY, PersistentDataType.STRING)).thenReturn("tower-1:$side")
            `when`(door.isValid).thenReturn(true)
            var transformation = Transformation(Vector3f(), AxisAngle4f(), Vector3f(1f), AxisAngle4f())
            `when`(door.transformation).thenAnswer { transformation }
            doAnswer { transformation = it.getArgument(0); null }.`when`(door).setTransformation(any(Transformation::class.java))
            return door
        }

        fun assertOpenDoorway() {
            for (x in -1..1) for (y in 60..63) assertEquals(Material.AIR, materials[Triple(x, y, 3)])
            assertEquals(-2.45f, left.transformation.translation.x)
            assertEquals(1.45f, right.transformation.translation.x)
        }

        fun assertClosedDoorway() {
            for (x in -1..1) for (y in 60..63) assertEquals(Material.IRON_BARS, materials[Triple(x, y, 3)])
            assertEquals(-1.5f, left.transformation.translation.x)
            assertEquals(0f, right.transformation.translation.x)
        }
    }

    @Test fun `reopening during closing cancels departure and ignores stale barrier tasks`() {
        val f = Fixture()
        f.floor.open()
        f.ticks.advance(60)
        var departures = 0
        f.floor.close { departures++ }
        val staleClosing = f.ticks.pending().toList()
        f.ticks.advance(16)
        f.floor.open()
        f.ticks.advance(60)
        // Even already-queued work delivered after cancellation must not change the doorway.
        staleClosing.forEach { it.action.run() }
        assertEquals(0, departures)
        assertTrue(f.floor.isOpen)
        f.assertOpenDoorway()
    }

    @Test fun `departure selected during opening closes once and prevents old opening work`() {
        for (elapsed in listOf(10L, 25L, 45L)) {
            val f = Fixture()
            f.floor.open()
            f.ticks.advance(elapsed)
            val staleOpening = f.ticks.pending().toList()
            var departures = 0
            f.floor.close { departures++ }
            f.ticks.advance(39)
            assertEquals(0, departures)
            f.ticks.advance(1)
            assertEquals(1, departures)
            staleOpening.forEach { it.action.run() }
            f.ticks.advance(500)
            assertEquals(1, departures)
            assertFalse(f.floor.isOpen)
            f.assertClosedDoorway()
        }
    }

    @Test fun `calling an open floor replaces its automatic close timer`() {
        val f = Fixture()
        f.floor.open()
        f.ticks.advance(100)
        val oldClose = f.ticks.pending().single()
        f.floor.open()
        assertTrue(oldClose.cancelled)
        oldClose.action.run()
        f.ticks.advance(120) // Original close time: tick 220.
        assertTrue(f.floor.isOpen)
        f.assertOpenDoorway()
        f.ticks.advance(79)
        assertTrue(f.floor.isOpen)
        f.ticks.advance(1) // Refreshed close time: tick 300.
        assertFalse(f.floor.isOpen)
        f.ticks.advance(40)
        f.assertClosedDoorway()
    }

    @Test fun `an old departure cancellation handle cannot cancel replacement door work`() {
        val f = Fixture()
        f.floor.open()
        f.ticks.advance(60)
        val oldDeparture = f.floor.close {}
        f.floor.open()
        oldDeparture.cancel()
        f.ticks.advance(60)
        assertTrue(f.floor.isOpen)
        f.assertOpenDoorway()
    }
}
