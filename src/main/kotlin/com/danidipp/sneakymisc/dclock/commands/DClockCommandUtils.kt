package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DClockCommandUtils(
    private val module: DClockModule,
) {
    val clockSuggestions = suggestionProvider { module.getClockNames() }
    val globalDisplaySuggestions = suggestionProvider { module.getDisplayNames() }
    val overtimeSuggestions = suggestionProvider { overtimeBehaviors }
    val sourceValueModeSuggestions = suggestionProvider { sourceValueModes }

    fun displaySuggestions(clockNameArgument: String): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider { context, builder ->
            val clockName = context.stringArg(clockNameArgument)
            suggest(module.getDisplayNames(clockName), builder)
        }
    }

    fun suggestionProvider(values: () -> Iterable<String>): SuggestionProvider<CommandSourceStack> {
        return SuggestionProvider { _, builder -> suggest(values(), builder) }
    }

    fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    fun success(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 1
    }

    fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.startsWith(remainingLowerCase)) {
                suggest(value)
            }
        }
    }.buildFuture()

    companion object {
        private val overtimeBehaviors = listOf("freeze", "count-up")
        private val sourceValueModes = listOf("unix-seconds", "literal")
    }
}

internal val CommandContext<CommandSourceStack>.sender: CommandSender
    get() = source.sender

internal fun CommandContext<CommandSourceStack>.stringArg(name: String): String =
    StringArgumentType.getString(this, name)

internal fun CommandContext<CommandSourceStack>.intArg(name: String): Int =
    IntegerArgumentType.getInteger(this, name)

internal fun CommandContext<CommandSourceStack>.requirePlayer(message: String): Player? {
    val player = sender as? Player
    if (player == null) {
        sender.sendMessage(message)
    }
    return player
}
