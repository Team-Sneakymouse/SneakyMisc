package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockConfig
import com.danidipp.sneakymisc.dclock.DClockModule
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
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
    }

    private fun buildCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("create")
            .executes { utils.fail(it, CREATE_USAGE) }
            .then(
                Commands.argument("display", StringArgumentType.word())
                    .executes { utils.fail(it, CREATE_USAGE) }
                    .then(
                        Commands.argument("size", IntegerArgumentType.integer(1))
                            .executes(::createDisplay)
                    )
            )

    private fun buildDeleteCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("delete")
            .executes { utils.fail(it, DELETE_USAGE) }
            .then(
                Commands.argument("display", StringArgumentType.word())
                    .suggests(utils.displaySuggestions)
                    .executes(::deleteDisplay)
            )

    private fun createDisplay(context: CommandContext<CommandSourceStack>): Int {
        val player = context.requirePlayer("Only players can create displays") ?: return 0
        val displayName = context.stringArg("display")
        module.editBlockedReason()?.let { return utils.fail(context, it) }
        if (!DClockConfig.DISPLAY_NAME.matches(displayName)) return utils.fail(context, "Display names may only use a-z, 0-9, _ and -")
        if (displayName == "value") return utils.fail(context, "'value' is reserved for %dclock_value%")
        if (!module.createDisplay(displayName, context.intArg("size"), player)) {
            return utils.fail(context, "Display '$displayName' already exists")
        }
        return utils.success(context, "Created display '$displayName'")
    }

    private fun deleteDisplay(context: CommandContext<CommandSourceStack>): Int {
        val displayName = context.stringArg("display")
        module.editBlockedReason()?.let { return utils.fail(context, it) }
        val result = module.deleteDisplay(displayName)
            ?: return utils.fail(context, "Display '$displayName' not found")
        return utils.success(
            context,
            "Deleted display '$displayName' (${result.removed} loaded entities removed, ${result.orphaned} orphaned)",
        )
    }

    private companion object {
        const val ROOT_USAGE = "Missing display action. Usage: /dclock display <create|delete> ..."
        const val CREATE_USAGE = "Missing arguments. Usage: /dclock display create <display> <size>"
        const val DELETE_USAGE = "Missing arguments. Usage: /dclock display delete <display>"
    }
}
