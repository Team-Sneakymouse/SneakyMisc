package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

internal class ElevatorCommands(private val module: ElevatorsModule, private val utils: ElevatorCommandUtils) {
    fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("elevator")
        .requires { it.sender.hasPermission("sneakymisc.elevator") }
        .executes { utils.fail(it, "Usage: /elevator <create|style|delete|list|call|reload>") }
        .then(Commands.literal("reload").executes { context ->
            utils.attempt(context) {
                val sender = context.source.sender
                module.reload { sender.sendMessage(it) }
                "Reloading elevators.yml..."
            }
        })
        .then(Commands.literal("list").executes { context ->
            module.getElevators().forEach { context.source.sender.sendMessage(it.formatComponent()) }; 1
        })
        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
            .then(Commands.argument("style", StringArgumentType.word()).suggests(utils.styleSuggestions).executes { context ->
                utils.attempt(context) {
                    module.createElevator(context.stringArg("name"), context.stringArg("style"))
                    "Created elevator ${context.stringArg("name")}"
                }
            })))
        .then(Commands.literal("style").then(Commands.argument("name", StringArgumentType.word()).suggests(utils.elevatorSuggestions)
            .then(Commands.argument("style", StringArgumentType.word()).suggests(utils.styleSuggestions).executes { context ->
                utils.attempt(context) {
                    val elevator = requireNotNull(module.getElevator(context.stringArg("name"))) { "Elevator not found" }
                    module.setStyle(elevator, context.stringArg("style"))
                    "Updated elevator style"
                }
            })))
        .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(utils.elevatorSuggestions).executes { context ->
            utils.attempt(context) {
                val elevator = requireNotNull(module.getElevator(context.stringArg("name"))) { "Elevator not found" }
                module.deleteElevator(elevator)
                "Deleted elevator"
            }
        }))
        .then(Commands.literal("call").then(Commands.argument("floor", StringArgumentType.word()).suggests(utils.floorSuggestions).executes { context ->
            utils.attempt(context) {
                val floor = requireNotNull(module.getFloor(context.stringArg("floor"))) { "Elevator floor not found" }
                module.call(floor)
                "Opened ${floor.id}"
            }
        }))
        .build()
}
