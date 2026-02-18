package com.danidipp.sneakymisc.leaderboards.commands

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.leaderboards.LeaderboardsModule
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class LeaderboardDebugCommand(private val plugin: SneakyMisc, private val module: LeaderboardsModule) : Command("leaderboarddebug") {
    init {
        description = "Debug command for leaderboard cache"
        usage = "/leaderboarddebug [leaderboard_name]"
        permission = "sneakymisc.command.leaderboarddebug"
    }


    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!plugin.isEnabled) {
            sender.sendMessage("Plugin is disabled")
            return true
        }

        val leaderboardName = args.getOrNull(0)

        // No arguments: list available leaderboards
        if (leaderboardName == null) {
            if (module.leaderboards.isEmpty()) {
                sender.sendMessage("No leaderboards configured")
            } else {
                sender.sendMessage("Available leaderboards: ${module.leaderboards.keys.joinToString(", ")}")
            }
            return true
        }

        // Valid leaderboard name: show cached entries
        val leaderboard = module.leaderboards[leaderboardName]
        if (leaderboard == null) {
            sender.sendMessage("Unknown leaderboard: $leaderboardName")
            return true
        }

        sender.sendMessage("$leaderboardName Cache Entries (${leaderboard.userCache.size}):")
        val sortedEntries = leaderboard.userCache.values.sortedByDescending { it.value }.take(32)
        for (entry in sortedEntries) {
            sender.sendMessage(" - ${entry.characterName}: ${entry.value}")
        }
        return true
    }
}