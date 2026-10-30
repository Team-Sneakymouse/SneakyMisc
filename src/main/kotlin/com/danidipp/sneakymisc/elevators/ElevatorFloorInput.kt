package com.danidipp.sneakymisc.elevators

/**
 * Keypad entry. Numbered floors take two digits (tens, then ones); `01` is G.
 * B starts a basement entry that completes with a single digit. Empty input never inherits the current floor.
 */
internal class ElevatorFloorInput {
    var tens: Int? = null
        private set
    var ones: Int? = null
        private set
    /** True after B was pressed; B takes the tens slot. */
    var basement = false
        private set

    fun digit(value: Int): String? {
        require(value in 0..9)
        if (ones != null) return null
        if (basement) {
            ones = value
            return "B$value"
        }
        if (tens == null) {
            tens = value
            return null
        }
        ones = value
        return ElevatorFloorLabels.canonical((tens!! * 10 + value).toString())
    }

    /** B only works as the first key. */
    fun basement() {
        if (tens == null && !basement) basement = true
    }

    fun reset() { tens = null; ones = null; basement = false }
}
