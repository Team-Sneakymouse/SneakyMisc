package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component

internal class ElevatorFloorCommands(
    private val module: ElevatorsModule,
    private val utils: ElevatorCommandUtils,
) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("elevatorfloor")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { utils.fail(it, ROOT_USAGE) }
            .then(buildCreateCommand())
            .then(buildDeleteCommand())
            .then(buildOpenCommand())
            .then(buildCloseCommand())
            .build()
    }

    private fun buildCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("create")
            .executes { utils.fail(it, CREATE_USAGE) }
            .then(
                Commands.argument("floor", StringArgumentType.word())
                    .executes(::createFloor)
            )

    private fun buildDeleteCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("delete")
            .executes { utils.fail(it, DELETE_USAGE) }
            .then(
                Commands.argument("floor", StringArgumentType.word())
                    .suggests(utils.floorSuggestions)
                    .executes(::deleteFloor)
            )

    private fun buildOpenCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("open")
            .executes { utils.fail(it, OPEN_USAGE) }
            .then(
                Commands.argument("floor", StringArgumentType.word())
                    .suggests(utils.floorSuggestions)
                    .executes(::openFloor)
            )

    private fun buildCloseCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("close")
            .executes { utils.fail(it, CLOSE_USAGE) }
            .then(
                Commands.argument("floor", StringArgumentType.word())
                    .suggests(utils.floorSuggestions)
                    .executes(::closeFloor)
            )

    private fun createFloor(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val player = context.requirePlayer("Only players can create elevators and floors") ?: return 0
        val floorId = context.stringArg("floor")
        val elevatorName = validateFloorId(context, floorId) ?: return 0
        val elevator = module.getElevator(elevatorName) ?: module.createElevator(elevatorName)

        val existingFloor = elevator.getFloor(floorId)
        if (existingFloor != null) {
            context.source.sender.sendMessage(
                Component.text("ElevatorFloor ").append(existingFloor.toComponent()).append(Component.text(" already exists")),
            )
            return 0
        }

        val location = player.location.toBlockLocation().setDirection(player.facing.direction)
        val floor = elevator.createFloor(floorId, location)
        context.source.sender.sendMessage(
            Component.text("ElevatorFloor ").append(floor.toComponent()).append(Component.text(" created")),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun deleteFloor(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val floorId = context.stringArg("floor")
        val elevatorName = validateFloorId(context, floorId) ?: return 0
        val elevator = module.getElevator(elevatorName)
            ?: return utils.fail(context, "Elevator $elevatorName not found")
        val floor = elevator.deleteFloor(floorId)
            ?: return utils.fail(context, "ElevatorFloor $floorId not found")

        context.source.sender.sendMessage(
            Component.text("ElevatorFloor ").append(floor.toComponent()).append(Component.text(" deleted")),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun openFloor(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val floorId = context.stringArg("floor")
        val elevatorName = validateFloorId(context, floorId) ?: return 0
        val elevator = module.getElevator(elevatorName)
            ?: return utils.fail(context, "Elevator not found")
        if (elevator.inTransit) {
            return utils.fail(context, "Elevator is in transit")
        }
        val floor = module.getFloor(floorId)
            ?: return utils.fail(context, "ElevatorFloor not found")

        floor.open()
        return Command.SINGLE_SUCCESS
    }

    private fun closeFloor(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val floorId = context.stringArg("floor")
        validateFloorId(context, floorId) ?: return 0
        val floor = module.getFloor(floorId)
            ?: return utils.fail(context, "ElevatorFloor not found")

        floor.close()
        return Command.SINGLE_SUCCESS
    }

    private fun validateFloorId(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        floorId: String,
    ): String? {
        if (floorId.count { it == '-' } != 1) {
            utils.fail(context, "Format: <name>-<floor>")
            return null
        }
        return floorId.substringBefore('-')
    }

    private companion object {
        const val PERMISSION = "sneakymisc.elevatorfloor"
        const val ROOT_USAGE = "Missing subcommand. Usage: /elevatorfloor <create|delete|open|close>"
        const val CREATE_USAGE = "Missing arguments. Usage: /elevatorfloor create <name-floor>"
        const val DELETE_USAGE = "Missing arguments. Usage: /elevatorfloor delete <name-floor>"
        const val OPEN_USAGE = "Missing arguments. Usage: /elevatorfloor open <name-floor>"
        const val CLOSE_USAGE = "Missing arguments. Usage: /elevatorfloor close <name-floor>"
    }
}
