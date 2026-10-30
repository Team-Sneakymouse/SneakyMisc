package com.danidipp.sneakymisc.elevators

internal fun interface ElevatorTask {
    fun cancel()
}

/**
 * Physical effects live behind this interface; scheduling and cancellation share one lifecycle.
 * A null origin means travel started inside an idle transit cab rather than at an elevator floor.
 */
internal interface ElevatorTravelEnvironment<F : Any, C : Any> {
    fun compatibleCabs(origin: F): List<C>
    fun isEmpty(cab: C): Boolean
    fun prepare(origin: F?, destination: F, cab: C)
    fun release(origin: F?, destination: F, cab: C)
    fun closeSelections(floor: F, interrupt: Boolean)
    fun openDoors(floor: F)
    fun closeDoors(floor: F, closed: () -> Unit): ElevatorTask
    fun board(origin: F, cab: C): Int
    /** Starts travel effects for the accounts already inside [cab] and returns how many there are. */
    fun boardInPlace(cab: C): Int
    fun afterTravel(arrive: () -> Unit): ElevatorTask
    fun arrive(cab: C, destination: F)
    fun failed(origin: F?, cab: C, failure: Exception)
}

internal enum class ElevatorDepartureResult { STARTED, SAME_FLOOR, BUSY, NO_COMPATIBLE_CAB, AT_CAPACITY, FAILED }

internal class ElevatorTravelCoordinator<F : Any, C : Any>(
    private val environment: ElevatorTravelEnvironment<F, C>,
) {
    private inner class Travel(val origin: F?, val destination: F, val cab: C) {
        var task: ElevatorTask? = null
    }

    private val active = mutableSetOf<Travel>()
    private val departures = mutableMapOf<F, Travel>()

    fun isDeparting(floor: F): Boolean = floor in departures
    fun involves(floor: F): Boolean = active.any { it.origin == floor || it.destination == floor }
    fun uses(cab: C): Boolean = active.any { it.cab == cab }
    fun isIdle(): Boolean = active.isEmpty()

    fun call(floor: F) {
        cancelDeparture(floor)
        environment.openDoors(floor)
    }

    fun start(origin: F, destination: F): ElevatorDepartureResult {
        if (isDeparting(origin)) return ElevatorDepartureResult.BUSY
        if (origin == destination) {
            call(origin)
            return ElevatorDepartureResult.SAME_FLOOR
        }
        val compatible = environment.compatibleCabs(origin)
        if (compatible.isEmpty()) return ElevatorDepartureResult.NO_COMPATIBLE_CAB
        val cab = compatible.firstOrNull { !uses(it) && environment.isEmpty(it) }
            ?: return ElevatorDepartureResult.AT_CAPACITY
        val travel = Travel(origin, destination, cab)
        active.add(travel)
        departures[origin] = travel
        try {
            environment.prepare(origin, destination, cab)
            environment.closeSelections(origin, false)
            travel.task = environment.closeDoors(origin) { depart(travel) }
        } catch (failure: Exception) {
            finish(travel)
            environment.failed(origin, cab, failure)
            environment.openDoors(origin)
            return ElevatorDepartureResult.FAILED
        }
        return ElevatorDepartureResult.STARTED
    }

    /** Sends the accounts inside an idle transit cab to [destination]. There are no doors to close first. */
    fun startFromCab(cab: C, destination: F): ElevatorDepartureResult {
        if (uses(cab)) return ElevatorDepartureResult.BUSY
        val travel = Travel(null, destination, cab)
        active.add(travel)
        try {
            environment.prepare(null, destination, cab)
            if (environment.boardInPlace(cab) == 0) {
                finish(travel)
                return ElevatorDepartureResult.FAILED
            }
            travel.task = environment.afterTravel { arrive(travel) }
        } catch (failure: Exception) {
            finish(travel)
            environment.failed(null, cab, failure)
            return ElevatorDepartureResult.FAILED
        }
        return ElevatorDepartureResult.STARTED
    }

    private fun depart(travel: Travel) {
        val origin = travel.origin ?: return
        if (departures[origin] !== travel || travel !in active) return
        departures.remove(origin)
        travel.task = null
        try {
            if (environment.board(origin, travel.cab) == 0) {
                finish(travel)
                environment.openDoors(origin)
                return
            }
            travel.task = environment.afterTravel { arrive(travel) }
        } catch (failure: Exception) {
            finish(travel)
            environment.failed(origin, travel.cab, failure)
        }
    }

    private fun arrive(travel: Travel) {
        if (travel !in active) return
        travel.task = null
        try {
            if (environment.isEmpty(travel.cab)) return
            cancelDeparture(travel.destination)
            environment.closeSelections(travel.destination, true)
            environment.arrive(travel.cab, travel.destination)
            environment.openDoors(travel.destination)
        } catch (failure: Exception) {
            environment.failed(travel.origin, travel.cab, failure)
        } finally {
            finish(travel)
        }
    }

    fun cancelDeparture(floor: F) {
        departures[floor]?.let { finish(it) }
    }

    private fun finish(travel: Travel) {
        if (!active.remove(travel)) return
        travel.origin?.let { if (departures[it] === travel) departures.remove(it) }
        travel.task?.cancel()
        travel.task = null
        environment.release(travel.origin, travel.destination, travel.cab)
    }

    fun shutdown() {
        active.toList().forEach { finish(it) }
    }
}
