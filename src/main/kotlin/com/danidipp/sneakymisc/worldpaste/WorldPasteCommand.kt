package com.danidipp.sneakymisc.worldpaste

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `/worldpaste list`, `/worldpaste info <preset>`, `/worldpaste run <preset> confirm`.
 * Presets live in `plugins/SneakyMisc/world-pastes.yml`; schematics are read from FAWE's schematic folder.
 */
internal class WorldPasteCommand(private val plugin: Plugin) {
    private val presetsFile get() = File(plugin.dataFolder, "world-pastes.yml")
    private val running = AtomicBoolean(false)

    // Suggestions can be asked for on the main thread, so read the presets file off it.
    private val presetSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        CompletableFuture.supplyAsync {
            for (name in runCatching { WorldPastePresets.load(presetsFile).presets.keys }.getOrDefault(emptySet())) {
                if (name.lowercase().startsWith(builder.remainingLowerCase)) builder.suggest(name)
            }
            builder.build()
        }
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("worldpaste")
            .requires { it.sender.hasPermission(PERMISSION) }
            .then(Commands.literal("list").executes { list(it) })
            .then(
                Commands.literal("info").then(
                    Commands.argument("preset", StringArgumentType.word())
                        .suggests(presetSuggestions)
                        .executes { info(it) }
                )
            )
            .then(
                Commands.literal("run").then(
                    Commands.argument("preset", StringArgumentType.word())
                        .suggests(presetSuggestions)
                        .executes {
                            it.source.sender.sendMessage(
                                "This replaces everything in the area, removes its entities and kills players in it. " +
                                    "Add 'confirm' to go ahead; '/worldpaste info' shows the area first."
                            )
                            Command.SINGLE_SUCCESS
                        }
                        .then(Commands.literal("confirm").executes { run(it) })
                )
            )
            .build()

    private fun list(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        withPresets(sender) { result ->
            sender.sendMessage(
                if (result.presets.isEmpty()) "No world paste presets in ${presetsFile.path}."
                else "World paste presets: ${result.presets.keys.joinToString()}"
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun info(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val name = StringArgumentType.getString(context, "preset")
        withPreset(sender, name) { preset ->
            val schematics = WorldPasteJob.readSchematics(WorldPasteJob.schematicsFolder(), preset)
            val area = PasteArea(schematics.map { it.box })
            WorldPasteJob.closeAll(schematics, plugin.logger)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val world = Bukkit.getWorld(preset.world)
                val players = world?.players.orEmpty().filter { area.footprintContains(it.location.x, it.location.z) }
                sender.sendMessage("${preset.name}: origin ${preset.origin.joinToString(" ")} in ${preset.world}" +
                    if (world == null) " (not loaded)" else "")
                schematics.forEach { sender.sendMessage("  ${it.file}: ${it.box}") }
                sender.sendMessage("  ${area.chunks().size} chunks; ${players.size} players in the area now" +
                    if (players.isEmpty()) "." else ": ${players.joinToString { it.name }}.")
                if (preset.afterCommands.isNotEmpty()) {
                    sender.sendMessage("  Afterwards: ${preset.afterCommands.joinToString { "/$it" }}")
                }
            })
        }
        return Command.SINGLE_SUCCESS
    }

    private fun run(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val name = StringArgumentType.getString(context, "preset")
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("A world paste is already running.")
            return 0
        }
        withPreset(sender, name, onMissing = { running.set(false) }) { preset ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                WorldPasteJob(plugin, sender, preset) { running.set(false) }.start()
            })
        }
        return Command.SINGLE_SUCCESS
    }

    /** Reads the presets file off the main thread, then runs [block] there too. */
    private fun withPresets(sender: CommandSender, block: (WorldPastePresetsResult) -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val result = WorldPastePresets.load(presetsFile)
                result.problems.forEach { sender.sendMessage("world-pastes.yml: $it") }
                block(result)
            } catch (e: Exception) {
                sender.sendMessage("World paste failed: $e")
            }
        })
    }

    private fun withPreset(sender: CommandSender, name: String, onMissing: () -> Unit = {}, block: (WorldPastePreset) -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val result = WorldPastePresets.load(presetsFile)
                val preset = result.presets[name]
                if (preset == null) {
                    result.problems.forEach { sender.sendMessage("world-pastes.yml: $it") }
                    sender.sendMessage("No world paste preset '$name' in ${presetsFile.path}.")
                    onMissing()
                    return@Runnable
                }
                block(preset)
            } catch (e: Exception) {
                onMissing()
                sender.sendMessage("World paste failed: $e")
            }
        })
    }

    private companion object {
        const val PERMISSION = "sneakymisc.command.worldpaste"
    }
}
