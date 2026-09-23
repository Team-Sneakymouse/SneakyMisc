package com.danidipp.sneakymisc.forceloadregion

import kotlin.test.Test
import kotlin.test.assertEquals

class FullyContainedChunksTest {
    @Test
    fun `finds chunks whose complete horizontal footprint is inside the region`() {
        val chunks = FullyContainedChunks.find(0, 31, 0, 31) { _, _ -> true }

        assertEquals(
            setOf(
                ChunkPosition(0, 0),
                ChunkPosition(0, 1),
                ChunkPosition(1, 0),
                ChunkPosition(1, 1),
            ),
            chunks.toSet(),
        )
    }

    @Test
    fun `excludes chunks cut by a concave gap even when all four corners are inside`() {
        val chunks = FullyContainedChunks.find(0, 15, 0, 15) { x, z -> x != 8 || z != 8 }

        assertEquals(emptyList(), chunks)
    }

    @Test
    fun `handles partial chunks and negative coordinates`() {
        val chunks = FullyContainedChunks.find(-31, 30, -16, -1) { x, z ->
            x in -31..30 && z in -16..-1
        }

        assertEquals(setOf(ChunkPosition(-1, -1), ChunkPosition(0, -1)), chunks.toSet())
    }
}
