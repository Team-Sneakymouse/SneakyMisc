package com.danidipp.sneakymisc.elevators

/**
 * Elevator floor labels: G (ground, also floor 1), 2–99, and basements B1–B9.
 * Floor 0, plain B, and B0 do not exist.
 */
internal object ElevatorFloorLabels {
    private val numbered = Regex("[2-9]|[1-9][0-9]")
    private val basement = Regex("B[1-9]")
    private val reversedBasement = Regex("([1-9])B")

    const val RULE = "Floor must be G, 2 through 99, or B1 through B9 (without leading zeros)"

    fun isCanonical(label: String): Boolean = label == "G" || label.matches(numbered) || label.matches(basement)

    /** Converts aliases (1 → G, 2B → B2) to the stored label. Unknown labels are returned unchanged. */
    fun canonical(label: String): String = when {
        label == "1" -> "G"
        label.matches(reversedBasement) -> "B${label[0]}"
        else -> label
    }

    /** Applies [canonical] to the label part of an `<elevator>-<label>` ID. */
    fun canonicalId(id: String): String {
        if (!id.contains('-')) return id
        return "${id.substringBeforeLast('-')}-${canonical(id.substringAfterLast('-'))}"
    }
}
