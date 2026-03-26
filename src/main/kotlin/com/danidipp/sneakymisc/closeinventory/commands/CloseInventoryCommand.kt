package com.danidipp.sneakymisc.closeinventory.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit

class CloseInventoryCommand {
    private val playerSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        suggest(Bukkit.getOnlinePlayers().map { it.name }.sorted(), builder)
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("closeinventory")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { fail(it, USAGE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests(playerSuggestions)
                    .executes(::closeInventory)
            ).build()

    private fun closeInventory(context: CommandContext<CommandSourceStack>): Int {
        val playerName = StringArgumentType.getString(context, "player")
        val player = Bukkit.getPlayer(playerName) ?: return fail(context, "Player not found")
        player.closeInventory()
        return Command.SINGLE_SUCCESS
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    private fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.lowercase().startsWith(remainingLowerCase)) {
                suggest(value)
            }
        }
    }.buildFuture()

    private companion object {
        const val PERMISSION = "sneakymisc.closeinventory"
        const val USAGE = "Usage: /closeinventory <player>"
    }
}
