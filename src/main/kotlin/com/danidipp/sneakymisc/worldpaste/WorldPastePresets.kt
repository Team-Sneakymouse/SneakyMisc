package com.danidipp.sneakymisc.worldpaste

/**
 * One named paste: schematic files (relative to FAWE's schematic folder) whose clipboard origins all go to [origin]
 * in [world], then console [afterCommands].
 */
internal data class WorldPastePreset(
    val name: String,
    val world: String,
    val origin: IntArray,
    val schematics: List<String>,
    val afterCommands: List<String> = emptyList(),
) {
    override fun equals(other: Any?) = other is WorldPastePreset && name == other.name && world == other.world &&
        origin.contentEquals(other.origin) && schematics == other.schematics && afterCommands == other.afterCommands

    override fun hashCode() = listOf(name, world, origin.toList(), schematics, afterCommands).hashCode()
}

/** Every world paste. New ones are added here, in code, with the jar that needs them. */
internal object WorldPastePresets {
    val ALL: List<WorldPastePreset> = listOf(
        // Old Darkvale after Quintara Lotus's /bigboom (2026-11-19): five cubic chunks of the blast crater, cut so
        // none needs a poly selection. The origin is the one they were copied from.
        WorldPastePreset(
            name = "darkvalescar",
            world = "world",
            origin = intArrayOf(4991, 60, 4850),
            schematics = (1..5).map { "darkvalescar$it.schem" },
            // off + on clears the crater's displays and builds them fresh on the new ground, and leaves it on.
            afterCommands = listOf("darkvale crater off", "darkvale crater on"),
        ),
    )

    /** A relative path with no `..` parts, e.g. `darkvale/crater1.schem`. The job also checks the resolved path. */
    fun isRelativeInside(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\") || ':' in path) return false
        val parts = path.split('/', '\\')
        return parts.none { it == ".." } && parts.last().isNotEmpty()
    }
}
