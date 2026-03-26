package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.danidipp.sneakymisc.dclock.MagicSpellsSourceConfig
import com.danidipp.sneakymisc.dclock.PocketBaseSourceConfig
import com.danidipp.sneakymisc.dclock.SourceConfig
import com.danidipp.sneakymisc.dclock.SourceValueMode
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

internal class DClockClockCommands(
    private val module: DClockModule,
    private val utils: DClockCommandUtils,
) {
    fun build(): ArgumentBuilder<CommandSourceStack, *> {
        return Commands.literal("clock")
            .executes { utils.fail(it, ROOT_USAGE) }
            .then(buildCreateCommand())
            .then(buildDeleteCommand())
            .then(buildOvertimeCommandCommand())
    }

    private fun buildCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("create")
            .executes { utils.fail(it, CREATE_USAGE) }
            .then(
                Commands.argument("clock", StringArgumentType.word())
                    .executes { utils.fail(it, CREATE_USAGE) }
                    .then(buildPocketBaseCreateCommand())
                    .then(buildMagicSpellsCreateCommand())
            )

    private fun buildPocketBaseCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("pocketbase")
            .executes { utils.fail(it, POCKETBASE_USAGE) }
            .then(
                Commands.argument("record", StringArgumentType.word())
                    .executes { context ->
                        createClock(
                            context = context,
                            source = PocketBaseSourceConfig(
                                context.stringArg("record"),
                                SourceValueMode.UNIX_SECONDS,
                            ),
                        )
                    }
                    .then(buildPocketBaseValueModeCommand())
            )

    private fun buildMagicSpellsCreateCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("magicspells")
            .executes { utils.fail(it, MAGICSPELLS_USAGE) }
            .then(
                Commands.argument("variable", StringArgumentType.word())
                    .executes { context ->
                        createClock(
                            context = context,
                            source = MagicSpellsSourceConfig(
                                context.stringArg("variable"),
                                SourceValueMode.UNIX_SECONDS,
                            ),
                        )
                    }
                    .then(buildMagicSpellsValueModeCommand())
            )

    private fun buildPocketBaseValueModeCommand(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.argument("valueMode", StringArgumentType.word())
            .suggests(utils.sourceValueModeSuggestions)
            .executes { context ->
                val valueMode = SourceValueMode.parse(context.stringArg("valueMode"))
                    ?: return@executes utils.fail(context, "Unknown source value mode '${context.stringArg("valueMode")}'")
                createClock(
                    context = context,
                    source = PocketBaseSourceConfig(context.stringArg("record"), valueMode),
                )
            }

    private fun buildMagicSpellsValueModeCommand(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.argument("valueMode", StringArgumentType.word())
            .suggests(utils.sourceValueModeSuggestions)
            .executes { context ->
                val valueMode = SourceValueMode.parse(context.stringArg("valueMode"))
                    ?: return@executes utils.fail(context, "Unknown source value mode '${context.stringArg("valueMode")}'")
                createClock(
                    context = context,
                    source = MagicSpellsSourceConfig(context.stringArg("variable"), valueMode),
                )
            }

    private fun buildDeleteCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("delete")
            .executes { utils.fail(it, DELETE_USAGE) }
            .then(
                Commands.argument("clock", StringArgumentType.word())
                    .suggests(utils.clockSuggestions)
                    .executes(::deleteClock)
            )

    private fun buildOvertimeCommandCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("overtimecommand")
            .executes { utils.fail(it, OVERTIME_COMMAND_USAGE) }
            .then(
                Commands.argument("clock", StringArgumentType.word())
                    .suggests(utils.clockSuggestions)
                    .executes(::clearOvertimeCommand)
                    .then(
                        Commands.argument("command", StringArgumentType.greedyString())
                            .executes(::setOvertimeCommand)
                    )
            )

    private fun createClock(
        context: CommandContext<CommandSourceStack>,
        source: SourceConfig,
    ): Int {
        val clockName = context.stringArg("clock")
        val created = module.createClock(clockName, source)
        if (!created) {
            return utils.fail(context, "Clock '$clockName' already exists")
        }
        return utils.success(context, "Created clock '$clockName'")
    }

    private fun deleteClock(context: CommandContext<CommandSourceStack>): Int {
        val clockName = context.stringArg("clock")
        val result = module.deleteClock(clockName)
            ?: return utils.fail(context, "Clock '$clockName' not found")

        return utils.success(
            context,
            "Deleted clock '$clockName' (${result.removed} loaded entities removed, ${result.orphaned} orphaned)",
        )
    }

    private fun setOvertimeCommand(context: CommandContext<CommandSourceStack>): Int {
        val clockName = context.stringArg("clock")
        val command = context.stringArg("command").replace("{clock}", clockName).trim()
        if (command.isBlank()) {
            return utils.fail(context, "Overtime command must not be empty")
        }

        return when (module.setClockOvertimeCommand(clockName, command)) {
            DClockModule.SetClockOvertimeCommandResult.NotFound ->
                utils.fail(context, "Clock '$clockName' not found")

            is DClockModule.SetClockOvertimeCommandResult.Updated ->
                utils.success(context, "Set overtime command for '$clockName' to '$command'")
        }
    }

    private fun clearOvertimeCommand(context: CommandContext<CommandSourceStack>): Int {
        val clockName = context.stringArg("clock")
        return when (val result = module.setClockOvertimeCommand(clockName, null)) {
            DClockModule.SetClockOvertimeCommandResult.NotFound ->
                utils.fail(context, "Clock '$clockName' not found")

            is DClockModule.SetClockOvertimeCommandResult.Updated -> {
                if (result.previousCommand == null) {
                    utils.fail(context, OVERTIME_COMMAND_USAGE)
                } else {
                    utils.success(context, "Cleared overtime command for '$clockName'")
                }
            }
        }
    }

    private companion object {
        const val ROOT_USAGE = "Missing clock action. Usage: /dclock clock <create|delete|overtimecommand> ..."
        const val CREATE_USAGE = "Missing arguments. Usage: /dclock clock create <clock> <pocketbase|magicspells> <record|variable> [unix-seconds|literal]"
        const val POCKETBASE_USAGE = "Missing arguments. Usage: /dclock clock create <clock> pocketbase <record> [unix-seconds|literal]"
        const val MAGICSPELLS_USAGE = "Missing arguments. Usage: /dclock clock create <clock> magicspells <variable> [unix-seconds|literal]"
        const val DELETE_USAGE = "Missing arguments. Usage: /dclock clock delete <clock>"
        const val OVERTIME_COMMAND_USAGE = "Missing arguments. Usage: /dclock clock overtimecommand <clock> [ ...command ]"
    }
}
