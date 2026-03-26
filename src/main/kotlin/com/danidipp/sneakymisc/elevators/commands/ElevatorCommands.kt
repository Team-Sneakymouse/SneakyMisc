package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

internal class ElevatorCommands(
    private val module: ElevatorsModule,
    private val utils: ElevatorCommandUtils,
) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("elevator")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { utils.fail(it, ROOT_USAGE) }
            .then(buildListCommand())
            .then(buildCallCommand())
            .build()
    }

    private fun buildListCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("list")
            .executes { context ->
                for (elevator in module.getElevators()) {
                    context.source.sender.sendMessage(elevator.formatComponent())
                }
                Command.SINGLE_SUCCESS
            }

    private fun buildCallCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("call")
            .executes { utils.fail(it, CALL_USAGE) }
            .then(
                Commands.argument("floor", StringArgumentType.word())
                    .suggests(utils.floorSuggestions)
                    .executes(::callElevator)
            )

    private fun callElevator(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val floorId = context.stringArg("floor")
        val elevatorName = floorId.split("-").firstOrNull()
            ?: return utils.fail(context, "Invalid floor id '$floorId'")
        val elevator = module.getElevator(elevatorName)
            ?: return utils.fail(context, "Elevator not found")
        if (elevator.inTransit) {
            return utils.fail(context, "Elevator is already in transit")
        }
        val floor = elevator.getFloor(floorId)
            ?: return utils.fail(context, "Floor not found")

        elevator.callTo(floor)
        return Command.SINGLE_SUCCESS
    }

    private companion object {
        const val PERMISSION = "sneakymisc.elevator"
        const val ROOT_USAGE = "Missing subcommand. Usage: /elevator <list|call>"
        const val CALL_USAGE = "Missing arguments. Usage: /elevator call <elevator-floor>"
    }
}
