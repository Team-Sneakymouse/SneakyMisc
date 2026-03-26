package com.danidipp.sneakymisc.elevators.commands

import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ElevatorCommandUtils(
    private val module: ElevatorsModule,
) {
    val elevatorSuggestions = suggestionProvider { module.getElevators().map { it.name }.sorted() }
    val floorSuggestions = suggestionProvider { module.getElevators().flatMap { it.getFloors().map { floor -> floor.id } }.sorted() }

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
            if (value.lowercase().startsWith(remainingLowerCase)) {
                suggest(value)
            }
        }
    }.buildFuture()
}

internal val CommandContext<CommandSourceStack>.sender: CommandSender
    get() = source.sender

internal fun CommandContext<CommandSourceStack>.stringArg(name: String): String =
    StringArgumentType.getString(this, name)

internal fun CommandContext<CommandSourceStack>.requirePlayer(message: String): Player? {
    val player = sender as? Player
    if (player == null) {
        sender.sendMessage(message)
    }
    return player
}
