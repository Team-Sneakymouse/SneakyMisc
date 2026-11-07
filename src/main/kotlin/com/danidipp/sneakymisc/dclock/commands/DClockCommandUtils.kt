package com.danidipp.sneakymisc.dclock.commands

import com.danidipp.sneakymisc.dclock.DClockModule
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DClockCommandUtils(
    private val module: DClockModule,
) {
    val displaySuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        for (name in module.getDisplayNames()) {
            if (name.startsWith(builder.remainingLowerCase)) builder.suggest(name)
        }
        builder.buildFuture()
    }

    fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    fun success(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 1
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
