package com.danidipp.sneakymisc.forceloadregion

internal data class ChunkPosition(val x: Int, val z: Int)

internal object FullyContainedChunks {
    private const val CHUNK_SIZE = 16

    fun find(
        minX: Int,
        maxX: Int,
        minZ: Int,
        maxZ: Int,
        contains: (x: Int, z: Int) -> Boolean,
    ): List<ChunkPosition> {
        if (minX > maxX || minZ > maxZ) return emptyList()

        val chunks = mutableListOf<ChunkPosition>()
        val minChunkX = Math.floorDiv(minX, CHUNK_SIZE)
        val maxChunkX = Math.floorDiv(maxX, CHUNK_SIZE)
        val minChunkZ = Math.floorDiv(minZ, CHUNK_SIZE)
        val maxChunkZ = Math.floorDiv(maxZ, CHUNK_SIZE)

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (isFullyContained(chunkX, chunkZ, contains)) {
                    chunks += ChunkPosition(chunkX, chunkZ)
                }
            }
        }
        return chunks
    }

    private fun isFullyContained(
        chunkX: Int,
        chunkZ: Int,
        contains: (x: Int, z: Int) -> Boolean,
    ): Boolean {
        val minBlockX = chunkX * CHUNK_SIZE
        val minBlockZ = chunkZ * CHUNK_SIZE
        for (xOffset in 0 until CHUNK_SIZE) {
            for (zOffset in 0 until CHUNK_SIZE) {
                if (!contains(minBlockX + xOffset, minBlockZ + zOffset)) return false
            }
        }
        return true
    }
}
