package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymail.MailRecord
import com.danidipp.sneakymail.MailReward
import com.danidipp.sneakymail.SneakyMail
import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class LeaderboardRewardsCommand(private val plugin: SneakyMisc, private val module: LeaderboardsModule) : Command("leaderboardrewards") {
    
    init {
        description = "Distribute rewards for a leaderboard"
        usage = "/leaderboardrewards <leaderboardId>"
        permission = "sneakymisc.command.leaderboardrewards"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!testPermission(sender)) return true

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: $usage").color(NamedTextColor.RED))
            return true
        }

        val leaderboardName = args[0]
        val leaderboard = module.leaderboards[leaderboardName]

        if (leaderboard == null) {
            sender.sendMessage(Component.text("Leaderboard '$leaderboardName' not found.").color(NamedTextColor.RED))
            return true
        }

        if (leaderboard.rewardRules.isEmpty()) {
            sender.sendMessage(Component.text("Leaderboard '$leaderboardName' has no rewards configured.").color(NamedTextColor.RED))
            return true
        }

        val sortedEntries = leaderboard.userCache.values.sortedByDescending { it.value }
        if (sortedEntries.isEmpty()) {
            sender.sendMessage(Component.text("Leaderboard '$leaderboardName' is empty. No rewards to distribute.").color(NamedTextColor.YELLOW))
            return true
        }

        var mailsSent = 0
        for ((index, entry) in sortedEntries.withIndex()) {
            val rank = index + 1
            val rewards = mutableListOf<MailReward>()

            for (rule in leaderboard.rewardRules) {
                if (rank in rule.range) {
                    rewards.add(MailReward.CommandReward("ms cast as {player} ${rule.rewardSpell}"))
                }
            }

            if (rewards.isNotEmpty()) {
                val player = Bukkit.getOfflinePlayer(entry.playerUUID)
                sendRewardMail(player, rank, rewards)
                mailsSent++
            }
        }

        sender.sendMessage(Component.text("Distributed rewards for '$leaderboardName' to $mailsSent players.").color(NamedTextColor.GREEN))
        return true
    }

    private fun sendRewardMail(recipient: OfflinePlayer, rank: Int, rewards: List<MailReward>) {
        // SneakyMail API usage based on user provided snippet
        SneakyMail.getInstance().mailSender.createMailAsync(
            MailRecord(
                sender_name = "<gold>Grand Paladin Order</gold>",
                sender_uuid = "",
                recipient_name = recipient.name ?: "Unknown",
                recipient_uuid = recipient.uniqueId.toString(),
                available = true,
                note = "<gold>Congratulations!</gold>\nYou achieved <yellow>${getOrdinal(rank)}</yellow> place on a leaderboard!",
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
