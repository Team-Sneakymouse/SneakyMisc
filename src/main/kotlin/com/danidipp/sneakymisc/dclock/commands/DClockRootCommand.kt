package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.ConsoleCommandSender

/**
 * /dclock reload | add <seconds> [source] | set <seconds> [source] | display ...
 *
 * `add` is how gameplay feeds the clock (MagicSpells runs it from the console); `seconds` may be
 * fractional or negative. The clock never goes below 0.
 */
class DClockRootCommand(
    private val module: DClockModule,
) {
    fun build(utils: DClockCommandUtils) =
        Commands.literal("dclock")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { utils.fail(it, USAGE) }
            .then(Commands.literal("reload").executes { context ->
                module.reload()
                utils.success(context, "Reloaded DClock config")
            })
            .then(buildAdjustCommand("add", utils) { seconds, source -> module.add(seconds, source) })
            .then(buildAdjustCommand("set", utils) { seconds, source -> module.set(seconds, source) })
            .then(DClockDisplayCommands(module, utils).build())

    private fun buildAdjustCommand(
        name: String,
        utils: DClockCommandUtils,
        action: (Double, String) -> Unit,
    ) = Commands.literal(name)
        .executes { utils.fail(it, "Missing arguments. Usage: /dclock $name <seconds> [source]") }
        .then(
            Commands.argument("seconds", DoubleArgumentType.doubleArg())
                .executes { run(it, name, utils, action, it.source.sender.name) }
                .then(
                    Commands.argument("source", StringArgumentType.greedyString())
                        .executes { run(it, name, utils, action, it.stringArg("source")) }
                )
        )

    private fun run(
        context: CommandContext<CommandSourceStack>,
        name: String,
        utils: DClockCommandUtils,
        action: (Double, String) -> Unit,
        source: String,
    ): Int {
        val seconds = DoubleArgumentType.getDouble(context, "seconds")
        if (!seconds.isFinite()) return utils.fail(context, "Seconds must be a finite number")
        action(seconds, source)
        // MagicSpells runs `add` from the console on every kill; don't echo those into the log.
        if (context.source.sender is ConsoleCommandSender) return 1
        return utils.success(context, "Sent DClock $name $seconds")
    }

    companion object {
        const val PERMISSION = "sneakymisc.dclock"
        private const val USAGE = "Missing subcommand. Usage: /dclock <reload|add|set|display>"
    }
}
