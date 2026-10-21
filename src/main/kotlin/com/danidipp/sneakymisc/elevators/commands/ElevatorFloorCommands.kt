package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.ItemFrame

internal class ElevatorFloorCommands(private val module: ElevatorsModule, private val utils: ElevatorCommandUtils) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal("elevatorfloor")
            .requires { it.sender.hasPermission("sneakymisc.elevatorfloor") }
            .executes { utils.fail(it, "Usage: /elevatorfloor <create|delete|open|close|link> <name-floor>") }
        root.then(Commands.literal("create").then(Commands.argument("floor", StringArgumentType.word()).executes { context ->
            val player = context.requirePlayer("Only players can create elevator floors") ?: return@executes 0
            utils.attempt(context) {
                val floor = module.createFloor(context.stringArg("floor"), player.location)
                "Created ${floor.id}"
            }
        }))
        for (action in listOf("delete", "open", "close")) {
            root.then(Commands.literal(action).then(Commands.argument("floor", StringArgumentType.word()).suggests(utils.floorSuggestions).executes { context ->
                utils.attempt(context) {
                    val floor = requireNotNull(module.getFloor(context.stringArg("floor"))) { "Elevator floor not found" }
                    when (action) {
                        "delete" -> module.deleteFloor(floor)
                        "open" -> module.call(floor)
                        "close" -> module.close(floor)
                    }
                    "Updated ${floor.id}"
                }
            }))
        }
        root.then(Commands.literal("link").then(Commands.argument("floor", StringArgumentType.word()).suggests(utils.floorSuggestions)
            .executes { context ->
                val player = context.requirePlayer("Only players can link controls") ?: return@executes 0
                utils.attempt(context) {
                    val floor = requireNotNull(module.getFloor(context.stringArg("floor"))) { "Elevator floor not found" }
                    val frame = requireNotNull(player.getTargetEntity(5) as? ItemFrame) { "Look at an item frame within five blocks" }
                    val control = module.link(frame, floor)
                    "Linked $control to ${floor.id}"
                }
            }))
        return root.build()
    }
}
