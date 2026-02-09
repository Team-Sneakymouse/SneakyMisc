package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymail.MailRecord
import com.danidipp.sneakymail.MailReward
import com.danidipp.sneakymail.SneakyMail
import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class LeaderboardRewardsCmd(private val plugin: SneakyMisc, private val cache: LeaderboardCache) : Command("leaderboardrewards") {
    
    init {
        description = "Distribute rewards for a leaderboard"
        usage = "/leaderboardrewards <leaderboardId>"
        permission = "sneakymisc.command.leaderboardrewards"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!testPermission(sender)) return true

        if (args.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: $usage</red>"))
            return true
        }

        val leaderboardName = args[0]
        val leaderboard = cache.getLeaderboard(leaderboardName)

        if (leaderboard == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Leaderboard '$leaderboardName' not found.</red>"))
            return true
        }

        if (leaderboard.rewardRules.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Leaderboard '$leaderboardName' has no rewards configured.</red>"))
            return true
        }

        val sortedEntries = leaderboard.cache.values.sortedByDescending { it.value }
        if (sortedEntries.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Leaderboard '$leaderboardName' is empty. No rewards to distribute.</yellow>"))
            return true
        }

        var mailsSent = 0
        
        // Don't block main thread with mail creation if list is huge, though createMailAsync handles DB IO asynchronously.
        // We will process the logic on the main thread because accessing cache/creating components is fast and safe.
        
        for ((index, entry) in sortedEntries.withIndex()) {
            val rank = index + 1
            val rewards = mutableListOf<MailReward>()

            for (rule in leaderboard.rewardRules) {
                if (rank in rule.range) {
                    // Create command reward. The command is run as console usually, replacing {player}
                    // SneakyMail CommandReward format: "ms cast as {player} spell" or simply command string
                    // The Request specified: "ms cast as {player} "+magicSpellName
                    // and "rewards config value" IS the magicSpellName.
                    
                    val command = "ms cast as {player} ${rule.reward}"
                    rewards.add(MailReward.CommandReward(command))
                }
            }

            if (rewards.isNotEmpty()) {
                val player = Bukkit.getOfflinePlayer(entry.playerUUID)
                sendRewardMail(player, rank, leaderboardName, rewards)
                mailsSent++
            }
        }

        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Distributed rewards for '$leaderboardName' to $mailsSent players.</green>"))
        return true
    }

    private fun sendRewardMail(recipient: OfflinePlayer, rank: Int, leaderboardName: String, rewards: List<MailReward>) {
        // SneakyMail API usage based on user provided snippet
        SneakyMail.getInstance().mailSender.createMailAsync(
            MailRecord(
                sender_name = "<gold>Grand Paladin Order</gold>",
                sender_uuid = "",
                recipient_name = recipient.name ?: "Unknown",
                recipient_uuid = recipient.uniqueId.toString(),
                available = true,
                note = MiniMessage.miniMessage().serialize(
                    Component.text("<gold>Congratulations!</gold>\nYou achieved <yellow>${getOrdinal(rank)}</yellow> place on a leaderboard!")
                ),
                rewards = rewards
            )
        )
    }

    private fun getOrdinal(i: Int): String {
        val suffixes = arrayOf("th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th")
        return when (i % 100) {
            11, 12, 13 -> i.toString() + "th"
            else -> i.toString() + suffixes[i % 10]
        }
    }
}
