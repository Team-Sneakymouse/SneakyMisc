package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.danidipp.sneakymisc.dclock.OvertimeBehavior
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

internal class DClockDisplayCommands(
    private val module: DClockModule,
    private val utils: DClockCommandUtils,
) {
    fun build(): ArgumentBuilder<CommandSourceStack, *> {
        return Commands.literal("display")
            .executes { utils.fail(it, ROOT_USAGE) }
            .then(buildCreateCommand())
            .then(buildDeleteCommand())
            .then(buildOvertimeBehaviorCommand())
    }

    private fun buildCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("create")
            .executes { utils.fail(it, CREATE_USAGE) }
            .then(
                Commands.argument("clock", StringArgumentType.word())
                    .suggests(utils.clockSuggestions)
                    .executes { utils.fail(it, CREATE_USAGE) }
                    .then(
                        Commands.argument("display", StringArgumentType.word())
                            .executes { utils.fail(it, CREATE_USAGE) }
                            .then(
                                Commands.argument("size", IntegerArgumentType.integer(1))
                                    .executes(::createDisplay)
                            )
                    )
            )

    private fun buildDeleteCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("delete")
            .executes { utils.fail(it, DELETE_USAGE) }
            .then(
                Commands.argument("clock", StringArgumentType.word())
                    .suggests(utils.clockSuggestions)
                    .executes { utils.fail(it, DELETE_USAGE) }
                    .then(
                        Commands.argument("display", StringArgumentType.word())
                            .suggests(utils.displaySuggestions("clock"))
                            .executes(::deleteDisplay)
                    )
            )

    private fun buildOvertimeBehaviorCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("overtimebehavior")
            .executes { utils.fail(it, OVERTIME_USAGE) }
            .then(
                Commands.argument("display", StringArgumentType.word())
                    .suggests(utils.globalDisplaySuggestions)
                    .executes { utils.fail(it, OVERTIME_USAGE) }
                    .then(
                        Commands.argument("behavior", StringArgumentType.word())
                            .suggests(utils.overtimeSuggestions)
                            .executes(::updateOvertimeBehavior)
                    )
            )

    private fun createDisplay(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val player = context.requirePlayer("Only players can create displays") ?: return 0
        val clockName = context.stringArg("clock")
        val displayName = context.stringArg("display")
        val size = context.intArg("size")
        val created = module.createDisplay(clockName, displayName, size, player)

        return when (created) {
            null -> utils.fail(context, "Clock '$clockName' not found")
            false -> utils.fail(context, "Display '$displayName' already exists")
            true -> utils.success(context, "Created display '$displayName' for clock '$clockName'")
        }
    }

    private fun deleteDisplay(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val clockName = context.stringArg("clock")
        val displayName = context.stringArg("display")
        val result = module.deleteDisplay(clockName, displayName)
            ?: return utils.fail(context, "Display '$displayName' under clock '$clockName' not found")

        return utils.success(
            context,
            "Deleted display '$displayName' (${result.removed} loaded entities removed, ${result.orphaned} orphaned)",
        )
    }

    private fun updateOvertimeBehavior(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val displayName = context.stringArg("display")
        val behaviorValue = context.stringArg("behavior")
        val behavior = OvertimeBehavior.parse(behaviorValue)
            ?: return utils.fail(context, "Unknown overtime behavior '$behaviorValue'")

        return when (module.setDisplayOvertimeBehavior(displayName, behavior)) {
            is DClockModule.SetOvertimeBehaviorResult.NotFound ->
                utils.fail(context, "Display '$displayName' not found")

            is DClockModule.SetOvertimeBehaviorResult.Ambiguous ->
                utils.fail(context, "Display '$displayName' is ambiguous across clocks")

            is DClockModule.SetOvertimeBehaviorResult.Updated ->
                utils.success(context, "Updated display '$displayName' overtime behavior to ${behavior.configValue()}")
        }
    }

    private companion object {
        const val ROOT_USAGE = "Missing display action. Usage: /dclock display <create|delete|overtimebehavior> ..."
        const val CREATE_USAGE = "Missing arguments. Usage: /dclock display create <clock> <display> <size>"
        const val DELETE_USAGE = "Missing arguments. Usage: /dclock display delete <clock> <display>"
        const val OVERTIME_USAGE = "Missing arguments. Usage: /dclock display overtimebehavior <display> <freeze|count-up>"
    }
}
