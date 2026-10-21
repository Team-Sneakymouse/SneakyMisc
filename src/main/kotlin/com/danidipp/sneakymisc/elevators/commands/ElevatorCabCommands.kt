package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

internal class ElevatorCabCommands(private val module: ElevatorsModule, private val utils: ElevatorCommandUtils) {
    fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("elevatorcab")
        .requires { it.sender.hasPermission("sneakymisc.elevator") }
        .executes { utils.fail(it, "Usage: /elevatorcab <create|style|delete|list>") }
        .then(Commands.literal("list").executes { context ->
            module.getTransitCabs().forEach { cab ->
                val pos = cab.cab.location
                context.source.sender.sendMessage("${cab.name}: style=${cab.style}, ${pos.world.name} ${pos.blockX} ${pos.blockY} ${pos.blockZ}")
            }; 1
        })
        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("style", StringArgumentType.word()).suggests(utils.styleSuggestions).executes { context ->
                val player = context.requirePlayer("Only players can register transit cabs") ?: return@executes 0
                utils.attempt(context) {
                    module.createTransitCab(context.stringArg("name"), context.stringArg("style"), player.location)
                    "Registered transit cab ${context.stringArg("name")}. Place cabs beyond proximity voice range of play areas and other cabs."
                }
            })))
        .then(Commands.literal("style").then(Commands.argument("name", StringArgumentType.word()).suggests(utils.cabSuggestions)
            .then(Commands.argument("style", StringArgumentType.word()).suggests(utils.styleSuggestions).executes { context ->
                utils.attempt(context) {
                    module.setTransitStyle(context.stringArg("name"), context.stringArg("style"))
                    "Updated transit cab style"
                }
            })))
        .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(utils.cabSuggestions).executes { context ->
            utils.attempt(context) {
                module.deleteTransitCab(context.stringArg("name"))
                "Deleted transit cab registration"
            }
        }))
        .build()
}
