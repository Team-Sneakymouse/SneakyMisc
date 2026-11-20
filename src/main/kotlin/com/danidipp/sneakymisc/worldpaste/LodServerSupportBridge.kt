package com.danidipp.sneakymisc.worldpaste

import org.bukkit.Bukkit
import org.bukkit.World
import java.util.logging.Logger

/**
 * Tells LodServerSupport that pasted chunks changed, so it drops its cached LODs and tells nearby clients to
 * re-request them straight away.
 *
 * LSS only notices block changes through Bukkit events, and FAWE fires none. Its own event listener calls
 * `DirtyColumnTracker.markDirty(dimension, chunkX, chunkZ)`; this makes the same call through LSS's public getters.
 * Reflection, because LSS publishes no API artifact and a Paper plugin's classes can't be named in LSS's
 * `paper.update_events` without opening SneakyMisc's classloader to every plugin. Checked against LSS 0.22.0.
 */
internal class LodServerSupportBridge(private val logger: Logger) {
    /** Returns how many chunks were marked, or null when LSS is missing or its methods changed. */
    fun markDirty(world: World, chunks: Collection<PasteChunk>): Int? {
        val plugin = Bukkit.getPluginManager().getPlugin(PLUGIN)
        if (plugin == null || !plugin.isEnabled) {
            logger.warning("$PLUGIN isn't enabled, so it wasn't told about the pasted chunks.")
            return null
        }
        return try {
            val service = plugin.javaClass.getMethod("getRequestService").invoke(plugin) ?: return null
            val tracker = service.javaClass.getMethod("getDirtyTracker").invoke(service) ?: return null
            val mark = tracker.javaClass.getMethod(
                "markDirty", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            // LSS keys dimensions by the world key, e.g. minecraft:overworld (see its PaperWorldHandler).
            val dimension = world.key.toString()
            for (chunk in chunks) mark.invoke(tracker, dimension, chunk.x, chunk.z)
            chunks.size
        } catch (e: ReflectiveOperationException) {
            logger.warning("Couldn't mark pasted chunks dirty in $PLUGIN ${plugin.pluginMeta.version}: $e. " +
                "Players see the new terrain in their LODs after reconnecting.")
            null
        }
    }

    private companion object {
        const val PLUGIN = "LodServerSupport"
    }
}
