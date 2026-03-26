package com.danidipp.sneakymisc.leaderboards.commands

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.leaderboards.LeaderboardsModule
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

class LeaderboardDebugCommand(
    private val plugin: SneakyMisc,
    private val module: LeaderboardsModule,
) {
    private val leaderboardSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        suggest(module.leaderboards.keys.sorted(), builder)
    }

    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("leaderboarddebug")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes(::showLeaderboardDebug)
            .then(
                Commands.argument("leaderboard_name", StringArgumentType.word())
                    .suggests(leaderboardSuggestions)
                    .executes(::showLeaderboardDebug)
            )

    private fun showLeaderboardDebug(context: CommandContext<CommandSourceStack>): Int {
        if (!plugin.isEnabled) {
            return fail(context, "Plugin is disabled")
        }

        val leaderboardName = runCatching { StringArgumentType.getString(context, "leaderboard_name") }.getOrNull()
        if (leaderboardName == null) {
            if (module.leaderboards.isEmpty()) {
                return success(context, "No leaderboards configured")
            }
            return success(context, "Available leaderboards: ${module.leaderboards.keys.joinToString(", ")}")
        }

        val leaderboard = module.leaderboards[leaderboardName]
            ?: return fail(context, "Unknown leaderboard: $leaderboardName")

        context.source.sender.sendMessage("$leaderboardName Cache Entries (${leaderboard.userCache.size}):")
        val sortedEntries = leaderboard.userCache.values.sortedByDescending { it.value }.take(32)
        for (entry in sortedEntries) {
            context.source.sender.sendMessage(" - ${entry.characterName}: ${entry.value}")
        }
        return Command.SINGLE_SUCCESS
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    private fun success(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.lowercase().startsWith(remainingLowerCase)) {
                suggest(value)
            }
        }
    }.buildFuture()

    private companion object {
        const val PERMISSION = "sneakymisc.command.leaderboarddebug"
    }
}
