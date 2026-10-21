package com.danidipp.sneakymisc.elevators

/** Empty input never inherits digits from the current elevator floor. */
internal class ElevatorFloorInput {
    var tens: Int? = null
        private set
    var ones: Int? = null
        private set

    fun digit(value: Int): String? {
        require(value in 0..9)
        if (tens == null) {
            tens = value
            return null
        }
        if (ones != null) return null
        ones = value
        return (tens!! * 10 + value).toString()
    }

    fun reset() { tens = null; ones = null }
}
