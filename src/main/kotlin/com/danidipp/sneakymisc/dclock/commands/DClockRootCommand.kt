package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands

class DClockRootCommand(
    private val module: DClockModule,
) {
    fun build(utils: DClockCommandUtils) =
        Commands.literal("dclock")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { utils.fail(it, "Missing subcommand. Usage: /dclock <reload|clock|display>") }
            .then(buildReloadCommand(utils))
            .then(DClockClockCommands(module, utils).build())
            .then(DClockDisplayCommands(module, utils).build())

    private fun buildReloadCommand(utils: DClockCommandUtils) =
        Commands.literal("reload")
            .executes { context ->
                module.reload()
                utils.success(context, "Reloaded DClock config")
                Command.SINGLE_SUCCESS
            }

    companion object {
        const val PERMISSION = "sneakymisc.dclock"
    }
}
