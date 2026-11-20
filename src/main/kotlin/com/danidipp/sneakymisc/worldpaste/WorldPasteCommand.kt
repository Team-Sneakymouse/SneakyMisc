package com.danidipp.sneakymisc.worldpaste

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `/worldpaste list`, `/worldpaste info <preset>`, `/worldpaste run <preset> confirm`, one literal per preset in
 * [WorldPastePresets.ALL]. Schematics are read from FAWE's schematic folder.
 */
internal class WorldPasteCommand(private val plugin: Plugin) {
    private val running = AtomicBoolean(false)

    fun build(): LiteralCommandNode<CommandSourceStack> {
        val info = Commands.literal("info")
        val run = Commands.literal("run")
        for (preset in WorldPastePresets.ALL) {
            info.then(Commands.literal(preset.name).executes { info(it, preset) })
            run.then(
                Commands.literal(preset.name)
                    .executes {
                        it.source.sender.sendMessage(
                            "This replaces everything in the area, removes its entities and kills players in it. " +
                                "Add 'confirm' to go ahead; '/worldpaste info ${preset.name}' shows the area first."
                        )
                        Command.SINGLE_SUCCESS
                    }
                    .then(Commands.literal("confirm").executes { run(it, preset) })
            )
        }
        return Commands.literal("worldpaste")
            .requires { it.sender.hasPermission(PERMISSION) }
            .then(Commands.literal("list").executes { list(it) })
            .then(info)
            .then(run)
            .build()
    }

    private fun list(context: CommandContext<CommandSourceStack>): Int {
        context.source.sender.sendMessage("World paste presets: ${WorldPastePresets.ALL.joinToString { it.name }}")
        return Command.SINGLE_SUCCESS
    }

    private fun info(context: CommandContext<CommandSourceStack>, preset: WorldPastePreset): Int {
        val sender = context.source.sender
        // Reading the schematics is disk work: off the main thread, then back to report.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val schematics = try {
                WorldPasteJob.readSchematics(WorldPasteJob.schematicsFolder(), preset)
            } catch (e: Exception) {
                sender.sendMessage("Couldn't read ${preset.name}'s schematics: $e")
                return@Runnable
            }
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
        })
        return Command.SINGLE_SUCCESS
    }

    private fun run(context: CommandContext<CommandSourceStack>, preset: WorldPastePreset): Int {
        val sender = context.source.sender
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("A world paste is already running.")
            return 0
        }
        try {
            WorldPasteJob(plugin, sender, preset) { running.set(false) }.start()
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
        return Command.SINGLE_SUCCESS
    }

    private companion object {
        const val PERMISSION = "sneakymisc.command.worldpaste"
    }
}
