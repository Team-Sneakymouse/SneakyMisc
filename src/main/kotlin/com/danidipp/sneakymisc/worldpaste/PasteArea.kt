package com.danidipp.sneakymisc.worldpaste

internal data class PasteChunk(val x: Int, val z: Int)

/** An inclusive block box in world coordinates: where one schematic lands. */
internal data class PasteBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Box minimum must not exceed its maximum" }
    }

    /** Whether a position (entity coordinates, so fractional) is inside the box's blocks. */
    fun contains(x: Double, y: Double, z: Double): Boolean =
        footprintContains(x, z) && y >= minY && y < maxY + 1.0

    /** Whether (x, z) is above or below the box at any height. */
    fun footprintContains(x: Double, z: Double): Boolean =
        x >= minX && x < maxX + 1.0 && z >= minZ && z < maxZ + 1.0

    override fun toString() = "$minX $minY $minZ to $maxX $maxY $maxZ"

    companion object {
        /**
         * Where a clipboard lands when its origin is pasted at [pasteAt]: every block keeps its offset from the
         * clipboard origin. Arguments are x, y, z triples.
         */
        fun landing(clipboardMin: IntArray, clipboardMax: IntArray, clipboardOrigin: IntArray, pasteAt: IntArray): PasteBox {
            // Exact arithmetic: a far-off origin throws instead of wrapping round to the other side of the world.
            fun at(point: IntArray, axis: Int) = Math.addExact(pasteAt[axis], Math.subtractExact(point[axis], clipboardOrigin[axis]))
            return PasteBox(
                at(clipboardMin, 0), at(clipboardMin, 1), at(clipboardMin, 2),
                at(clipboardMax, 0), at(clipboardMax, 1), at(clipboardMax, 2),
            )
        }
    }
}

/** Every box of one preset, pasted together. */
internal class PasteArea(val boxes: List<PasteBox>) {
    fun contains(x: Double, y: Double, z: Double) = boxes.any { it.contains(x, y, z) }

    fun footprintContains(x: Double, z: Double) = boxes.any { it.footprintContains(x, z) }

    /** Every chunk that any box touches, in a stable order. */
    fun chunks(): List<PasteChunk> {
        val chunks = LinkedHashSet<PasteChunk>()
        for (box in boxes) {
            for (cx in (box.minX shr 4)..(box.maxX shr 4)) {
                for (cz in (box.minZ shr 4)..(box.maxZ shr 4)) chunks += PasteChunk(cx, cz)
            }
        }
        return chunks.toList()
    }
}
