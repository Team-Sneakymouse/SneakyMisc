package com.danidipp.sneakymisc.worldpaste

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.logging.Level

/** A schematic read into memory, and where it will land. */
internal class LoadedSchematic(val file: String, val clipboard: Clipboard, val box: PasteBox)

/**
 * Pastes one preset, in order:
 * 1. read the schematics (async) and check every box fits the world's build height,
 * 2. load every chunk the area touches, holding each with a plugin ticket as it loads, and wait for its entities,
 * 3. kill every player standing above or below the area and remove every other entity inside the boxes,
 * 4. paste blocks, block entities and air with FAWE, without the schematics' entities or biomes (async),
 * 5. run the preset's console after-commands and tell LodServerSupport the chunks changed,
 * 6. tell LSS again after [MARK_AGAIN_TICKS] (FAWE may still have been relighting) and let the chunks unload after
 *    [HOLD_TICKS], so LSS has served the new LODs from memory by then.
 *
 * Nothing in the world changes before step 3, so a bad file, a box out of bounds or chunks that won't load stop
 * the job harmlessly. If the paste itself fails part-way, LSS is still told about the area.
 *
 * Every callback runs through [guard], and every ending through [finish], which always clears the run lock.
 */
internal class WorldPasteJob(
    private val plugin: Plugin,
    private val sender: CommandSender,
    private val preset: WorldPastePreset,
    private val onFinish: () -> Unit,
) {
    private val logger = plugin.logger
    private val lod = LodServerSupportBridge(logger)

    /** Every chunk loaded so far; [held] are those this job put a ticket on. Main thread only. */
    private val chunks = ArrayList<Chunk>()
    private val held = ArrayList<Chunk>()
    private var schematics = listOf<LoadedSchematic>()
    private var waitTask: BukkitTask? = null
    private var pasteStarted = false
    private var finished = false

    fun start() = guard {
        val world = Bukkit.getWorld(preset.world) ?: return@guard finish("World ${preset.world} isn't loaded.")
        report("Reading ${preset.schematics.size} schematics for ${preset.name}...")
        async {
            val loaded = readSchematics(schematicsFolder(), preset)
            sync {
                schematics = loaded
                checkBounds(world)
            }
        }
    }

    private fun checkBounds(world: World) {
        val outside = schematics.filter { it.box.minY < world.minHeight || it.box.maxY >= world.maxHeight }
        if (outside.isNotEmpty()) {
            return finish("${outside.joinToString { "${it.file} (${it.box})" }} would land outside ${world.name}'s " +
                "build height ${world.minHeight} to ${world.maxHeight - 1}. Nothing was changed.")
        }
        loadChunks(world, PasteArea(schematics.map { it.box }))
    }

    private fun loadChunks(world: World, area: PasteArea) {
        val positions = area.chunks()
        report("Loading ${positions.size} chunks...")
        var failure: Throwable? = null
        for (position in positions) {
            world.getChunkAtAsync(position.x, position.z).whenComplete { chunk, error ->
                onMain {
                    if (finished) return@onMain
                    if (error != null) failure = error
                    else {
                        // Ticket as soon as it loads, so it can't unload while the rest of the area loads.
                        chunks += chunk
                        if (chunk.addPluginChunkTicket(plugin)) held += chunk
                    }
                }
            }
        }
        var waited = 0
        waitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            guard {
                failure?.let { return@guard finish("Couldn't load the area's chunks: $it. Nothing was changed.") }
                if (chunks.size == positions.size && chunks.all { it.isEntitiesLoaded }) {
                    stopWaiting()
                    clearArea(world, area)
                } else if (++waited > LOAD_WAIT_TICKS) {
                    finish("Only ${chunks.count { it.isEntitiesLoaded }} of ${positions.size} chunks loaded with their " +
                        "entities in time. Nothing was changed.")
                }
            }
        }, 1L, 1L)
    }

    private fun clearArea(world: World, area: PasteArea) {
        val doomed = world.players.filter { area.footprintContains(it.location.x, it.location.z) && !it.isDead }
        doomed.forEach { it.health = 0.0 }
        var removed = 0
        for (chunk in chunks) {
            for (entity in chunk.entities) {
                // Players were killed instead; what rides them (cosmetics) belongs to them.
                if (entity is Player || ridesPlayer(entity)) continue
                val at = entity.location
                if (!area.contains(at.x, at.y, at.z)) continue
                entity.remove()
                removed++
            }
        }
        report("Killed ${doomed.size} players in the area${names(doomed)} and removed $removed entities. Pasting...")
        pasteStarted = true
        val done = ArrayList<String>()
        async {
            try {
                for (schematic in schematics) {
                    paste(world, schematic)
                    done += schematic.file
                }
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "World paste ${preset.name} failed", e)
                val partial = schematics.getOrNull(done.size)?.file
                sync {
                    hold(world, area, "Paste stopped: $e. Pasted ${done.size} of ${schematics.size} schematics" +
                        (partial?.let { "; $it may be partly pasted" } ?: "") + ". After-commands were skipped.")
                }
                return@async
            }
            sync { pasted(world, area) }
        }
    }

    private fun paste(world: World, schematic: LoadedSchematic) {
        val origin = BlockVector3.at(preset.origin[0], preset.origin[1], preset.origin[2])
        WorldEdit.getInstance().newEditSessionBuilder()
            .world(BukkitAdapter.adapt(world))
            .maxBlocks(-1)
            .build()
            .use { session ->
                val operation = ClipboardHolder(schematic.clipboard).createPaste(session)
                    .to(origin)
                    .ignoreAirBlocks(false)
                    .copyEntities(false)
                    .copyBiomes(false)
                    .build()
                Operations.complete(operation)
            }
        logger.info("World paste ${preset.name}: pasted ${schematic.file} at ${schematic.box}")
    }

    private fun pasted(world: World, area: PasteArea) {
        report("Pasted ${schematics.size} schematics.")
        for (command in preset.afterCommands) {
            val ok = runCatching { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command) }.getOrDefault(false)
            report(if (ok) "Ran /$command." else "/$command failed or doesn't exist.")
        }
        hold(world, area, "World paste ${preset.name} done.")
    }

    /** Tell LSS about the area now and again later, keep the chunks loaded meanwhile, then finish with [message]. */
    private fun hold(world: World, area: PasteArea, message: String) {
        closeClipboards()
        val chunkList = area.chunks()
        val marked = lod.markDirty(world, chunkList)
        report(
            if (marked == null) "Couldn't tell LodServerSupport about the change (see the log); players see the new " +
                "terrain in their LODs after reconnecting."
            else "Told LodServerSupport $marked chunks changed."
        )
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { guard { lod.markDirty(world, chunkList) } }, MARK_AGAIN_TICKS)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { guard { finish(message) } }, HOLD_TICKS)
    }

    /**
     * Ends the job once, whatever happened: each cleanup step runs even if another throws, and the run lock is
     * always released. A failure before the paste started changed nothing.
     */
    private fun finish(message: String) {
        if (finished) return
        finished = true
        try {
            runCatching { stopWaiting() }
            runCatching { closeClipboards() }
            runCatching { releaseChunks() }
            runCatching { report(message) }
        } finally {
            onFinish()
        }
    }

    private fun stopWaiting() {
        waitTask?.cancel()
        waitTask = null
    }

    private fun releaseChunks() {
        for (chunk in held) runCatching { chunk.removePluginChunkTicket(plugin) }
        held.clear()
        chunks.clear()
    }

    /** FAWE's disk-backed clipboards flush their file on close, so close them off the main thread. */
    private fun closeClipboards() {
        val toClose = schematics
        schematics = emptyList()
        if (toClose.isEmpty()) return
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { closeAll(toClose, logger) })
    }

    private fun report(message: String) {
        logger.info("World paste ${preset.name}: $message")
        if (sender !is ConsoleCommandSender) sender.sendMessage(message)
    }

    private fun ridesPlayer(entity: Entity): Boolean {
        var vehicle = entity.vehicle
        while (vehicle != null) {
            if (vehicle is Player) return true
            vehicle = vehicle.vehicle
        }
        return false
    }

    private fun names(players: List<Player>) =
        if (players.isEmpty()) "" else " (${players.joinToString { it.name }})"

    /** Runs [block]; any exception ends the job. If the paste already started, LSS is still told about the area. */
    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "World paste ${preset.name} failed", e)
            finish("World paste failed: $e" + if (pasteStarted) ". The paste may be incomplete." else ". Nothing was changed.")
        }
    }

    private fun async(block: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                block()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "World paste ${preset.name} failed", e)
                sync { finish("World paste failed: $e" + if (pasteStarted) "." else ". Nothing was changed.") }
            }
        })
    }

    private fun sync(block: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable { guard(block) })
    }

    private fun onMain(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) guard(block) else sync(block)
    }

    companion object {
        /** When LSS is told about the area a second time: after FAWE's relight, with time left for LSS to send it. */
        const val MARK_AGAIN_TICKS = 30L * 20
        /**
         * How long pasted chunks stay loaded, so LSS serves the new LODs from memory. Leaves 60 s after the second
         * mark: LSS sends dirty notices every `dirty_broadcast_interval_ticks` (10 s by default).
         */
        const val HOLD_TICKS = 90L * 20
        const val LOAD_WAIT_TICKS = 120 * 20

        /**
         * FAWE's schematic folder (`saving.dir` in its config, `plugins/FastAsyncWorldEdit/schematics` by default),
         * the same one `//schem load` reads.
         */
        fun schematicsFolder(): File {
            val worldEdit = WorldEdit.getInstance()
            return worldEdit.getWorkingDirectoryPath(worldEdit.configuration.saveDir).toFile()
        }

        /** Reads every schematic of [preset] and works out where each lands. Blocking: call off the main thread. */
        fun readSchematics(folder: File, preset: WorldPastePreset): List<LoadedSchematic> {
            val loaded = ArrayList<LoadedSchematic>()
            val root = folder.canonicalFile
            try {
                for (name in preset.schematics) {
                    val file = File(root, name).canonicalFile
                    require(file.toPath().startsWith(root.toPath())) { "$name is outside ${root.path}" }
                    require(file.isFile) { "${file.path} doesn't exist" }
                    val format = requireNotNull(ClipboardFormats.findByFile(file)) { "$name isn't a schematic FAWE can read" }
                    val clipboard = file.inputStream().buffered().use { input -> format.getReader(input).use { it.read() } }
                    val region = clipboard.region
                    loaded += LoadedSchematic(
                        name,
                        clipboard,
                        PasteBox.landing(region.minimumPoint.toArray(), region.maximumPoint.toArray(), clipboard.origin.toArray(), preset.origin),
                    )
                }
            } catch (e: Exception) {
                closeAll(loaded, null)
                throw e
            }
            return loaded
        }

        /** Closes every clipboard, carrying on past failures. Blocking: call off the main thread. */
        fun closeAll(schematics: List<LoadedSchematic>, logger: java.util.logging.Logger?) {
            for (schematic in schematics) {
                try {
                    (schematic.clipboard as? AutoCloseable)?.close()
                } catch (e: Exception) {
                    logger?.log(Level.WARNING, "Couldn't close clipboard for ${schematic.file}", e)
                }
            }
        }

        private fun BlockVector3.toArray() = intArrayOf(x(), y(), z())
    }
}
