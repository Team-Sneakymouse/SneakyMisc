package com.danidipp.sneakymisc.elevators

import kotlin.test.*

class ElevatorTravelCoordinatorTest {
    private class Callback(val action: () -> Unit) : ElevatorTask {
        var cancelled = false
        override fun cancel() { cancelled = true }
        fun fire() = action() // Deliberately fire cancelled work too, to exercise lifecycle guards.
    }

    private class World : ElevatorTravelEnvironment<String, String> {
        val styles = mutableMapOf("a1" to "stone", "a2" to "stone", "a3" to "stone", "b1" to "wood", "b2" to "wood")
        val cabs = linkedMapOf("stone1" to "stone", "stone2" to "stone", "wood1" to "wood")
        val occupants = mutableMapOf<String, MutableSet<String>>()
        val closing = mutableMapOf<String, Callback>()
        val arrivals = mutableListOf<Callback>()
        val effects = mutableListOf<String>()
        val released = mutableListOf<String>()
        var boardFailure = false
        var arrivalFailure = false
        var prepareFailure = false

        fun people(at: String) = occupants.getOrPut(at) { linkedSetOf() }
        override fun compatibleCabs(origin: String) = cabs.filterValues { it == styles[origin] }.keys.toList()
        override fun isEmpty(cab: String) = people(cab).isEmpty()
        override fun prepare(origin: String?, destination: String, cab: String) {
            if (prepareFailure) error("cannot load destination")
        }
        override fun release(origin: String?, destination: String, cab: String) { released.add(cab) }
        override fun closeSelections(floor: String, interrupt: Boolean) { effects.add("selection:$floor:$interrupt") }
        override fun openDoors(floor: String) { effects.add("open:$floor") }
        override fun closeDoors(floor: String, closed: () -> Unit): ElevatorTask = Callback(closed).also { closing[floor] = it }
        override fun board(origin: String, cab: String): Int {
            if (boardFailure) error("boarding failed")
            effects.add("board:$origin:$cab")
            val people = people(origin).toList()
            people(cab).addAll(people)
            people(origin).clear()
            return people.size
        }
        override fun boardInPlace(cab: String): Int {
            effects.add("boardInPlace:$cab")
            return people(cab).size
        }
        override fun afterTravel(arrive: () -> Unit): ElevatorTask = Callback(arrive).also { arrivals.add(it) }
        override fun arrive(cab: String, destination: String) {
            if (arrivalFailure) error("arrival failed")
            effects.add("arrive:$cab:$destination")
            people(destination).addAll(people(cab))
            people(cab).clear()
        }
        override fun failed(origin: String?, cab: String, failure: Exception) { effects.add("failed:$origin:$cab") }
    }

    private val world = World()
    private val travel = ElevatorTravelCoordinator(world)

    @Test fun `calls open independent floors even at capacity`() {
        world.cabs.clear()
        travel.call("a1")
        travel.call("a2")
        assertEquals(listOf("open:a1", "open:a2"), world.effects)
        assertEquals(ElevatorDepartureResult.NO_COMPATIBLE_CAB, travel.start("a1", "a2"))
    }

    @Test fun `same style shares capacity and other styles have independent pools`() {
        assertEquals(ElevatorDepartureResult.STARTED, travel.start("a1", "a2"))
        assertEquals(ElevatorDepartureResult.STARTED, travel.start("a2", "a3"))
        assertEquals(ElevatorDepartureResult.AT_CAPACITY, travel.start("a3", "a1"))
        assertEquals(ElevatorDepartureResult.STARTED, travel.start("b1", "b2"))
        assertEquals(setOf("stone1", "stone2", "wood1"), world.cabs.keys.filter { travel.uses(it) }.toSet())
        assertFalse("selection:a3:false" in world.effects)
    }

    @Test fun `first valid selection wins and call cancels closing before stale transfer`() {
        world.people("a1").add("alice")
        travel.start("a1", "a2")
        val stale = world.closing.getValue("a1")
        assertEquals(ElevatorDepartureResult.BUSY, travel.start("a1", "a3"))
        travel.call("a1")
        assertTrue(stale.cancelled)
        assertFalse(travel.uses("stone1"))
        travel.start("a1", "a3")
        stale.fire()
        assertEquals(setOf("alice"), world.people("a1"))
        assertTrue(world.arrivals.isEmpty())
        world.closing.getValue("a1").fire()
        assertEquals(setOf("alice"), world.people("stone1"))
    }

    @Test fun `passengers are sampled after doors close and again at arrival`() {
        world.people("a1").addAll(listOf("alice", "bob"))
        travel.start("a1", "a2")
        world.people("a1").remove("bob") // Bob leaves before the doors close.
        world.people("a1").add("charlie") // Charlie boards during closing.
        world.closing.getValue("a1").fire()
        world.people("stone1").remove("alice") // Alice teleports out in transit.
        world.people("stone1").add("summoned")
        world.arrivals.single().fire()
        assertEquals(setOf("charlie", "summoned"), world.people("a2"))
        assertFalse(travel.uses("stone1"))
    }

    @Test fun `arrival cancels outgoing departure before either party can leave`() {
        world.people("a1").add("incoming")
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        world.people("a2").add("outgoing")
        travel.start("a2", "a3")
        val stale = world.closing.getValue("a2")
        world.arrivals.single().fire()
        assertTrue(stale.cancelled)
        assertFalse(travel.isDeparting("a2"))
        assertFalse(travel.uses("stone2"))
        stale.fire()
        assertEquals(setOf("incoming", "outgoing"), world.people("a2"))
        val interruption = world.effects.indexOf("selection:a2:true")
        assertTrue(interruption >= 0 && interruption < world.effects.indexOf("arrive:stone1:a2"))
        assertEquals("open:a2", world.effects.last())
    }

    @Test fun `empty active cab retains its reservation until original arrival without interrupting destination`() {
        world.people("a1").add("alice")
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        world.people("stone1").clear()
        assertTrue(travel.uses("stone1"))
        world.arrivals.single().fire()
        assertFalse(travel.uses("stone1"))
        assertFalse("selection:a2:true" in world.effects)
    }

    @Test fun `stranded occupants block allocation without passenger tracking`() {
        world.people("stone1").add("stranded")
        world.cabs.remove("stone2")
        assertEquals(ElevatorDepartureResult.AT_CAPACITY, travel.start("a1", "a2"))
        world.people("stone1").clear() // /spawn
        assertEquals(ElevatorDepartureResult.STARTED, travel.start("a1", "a2"))
    }

    @Test fun `same floor requires no capacity and empty departure releases its cab`() {
        world.cabs.clear()
        assertEquals(ElevatorDepartureResult.SAME_FLOOR, travel.start("a1", "a1"))
        world.cabs["stone1"] = "stone"
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        assertFalse(travel.uses("stone1"))
        assertTrue(world.arrivals.isEmpty())
    }

    @Test fun `prepare and boarding failures release reservations`() {
        world.prepareFailure = true
        assertEquals(ElevatorDepartureResult.FAILED, travel.start("a1", "a2"))
        assertFalse(travel.uses("stone1"))
        assertFalse(travel.isDeparting("a1"))
        world.prepareFailure = false
        world.boardFailure = true
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        assertFalse(travel.uses("stone1"))
        assertEquals(listOf("stone1", "stone1"), world.released)
    }

    @Test fun `failed arrival frees reservation but occupied cab cannot be reused`() {
        world.people("a1").add("alice")
        world.cabs.remove("stone2")
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        world.arrivalFailure = true
        world.arrivals.single().fire()
        assertFalse(travel.uses("stone1"))
        assertEquals(ElevatorDepartureResult.AT_CAPACITY, travel.start("a2", "a3"))
    }

    @Test fun `shutdown invalidates closing and arrival callbacks without evacuation`() {
        world.people("a1").add("alice")
        travel.start("a1", "a2")
        world.closing.getValue("a1").fire()
        travel.start("a2", "a3")
        travel.shutdown()
        world.arrivals.single().fire()
        world.closing.getValue("a2").fire()
        assertEquals(setOf("alice"), world.people("stone1"))
        assertEquals(setOf("stone1", "stone2"), world.released.toSet())
        assertFalse(travel.uses("stone1"))
        assertFalse(travel.uses("stone2"))
    }

    @Test fun `stranded occupants travel from their idle cab and arrive like ordinary travel`() {
        world.people("stone1").add("stranded")
        world.people("a2").add("selecting")
        travel.start("a2", "a3")
        val stale = world.closing.getValue("a2")
        assertEquals(ElevatorDepartureResult.STARTED, travel.startFromCab("stone1", "a2"))
        assertTrue(travel.uses("stone1"))
        assertTrue(travel.involves("a2"))
        assertEquals(ElevatorDepartureResult.BUSY, travel.startFromCab("stone1", "a3"))
        world.arrivals.single().fire()
        assertTrue(stale.cancelled)
        assertEquals(setOf("stranded", "selecting"), world.people("a2"))
        assertFalse(travel.uses("stone1"))
        assertEquals("open:a2", world.effects.last())
    }

    @Test fun `cab start with nobody inside or failing preparation releases the cab`() {
        assertEquals(ElevatorDepartureResult.FAILED, travel.startFromCab("stone1", "a2"))
        assertFalse(travel.uses("stone1"))
        world.people("stone1").add("stranded")
        world.prepareFailure = true
        assertEquals(ElevatorDepartureResult.FAILED, travel.startFromCab("stone1", "a2"))
        assertFalse(travel.uses("stone1"))
        assertEquals(listOf("stone1", "stone1"), world.released)
        assertEquals("failed:null:stone1", world.effects.last())
        assertTrue(world.arrivals.isEmpty())
    }

    @Test fun `shutdown cancels cab-start travel`() {
        world.people("stone1").add("stranded")
        travel.startFromCab("stone1", "a2")
        travel.shutdown()
        world.arrivals.single().fire()
        assertEquals(setOf("stranded"), world.people("stone1"))
        assertFalse(travel.uses("stone1"))
    }
}
