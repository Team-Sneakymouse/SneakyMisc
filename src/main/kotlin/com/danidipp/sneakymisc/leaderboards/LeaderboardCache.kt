package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent
import com.nisovin.magicspells.variables.variabletypes.CharacterVariable
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable
import io.ktor.util.collections.ConcurrentMap
import net.sneakycharactermanager.paper.handlers.character.Character
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import kotlin.math.roundToLong

data class LeaderboardEntry(
    val playerUUID: UUID,
    val characterUUID: UUID,
    val characterName: String,
    val value: Double
)
class LeaderboardCache(val plugin: SneakyMisc): SneakyModule {
    val bountyCache = ConcurrentMap<UUID, LeaderboardEntry>()
    lateinit var bountyLeaderboard: List<GlobalStringVariable>
    lateinit var bountyVariable: CharacterVariable
    val paladinCache = ConcurrentMap<UUID, LeaderboardEntry>()
    lateinit var paladinLeaderboard: List<GlobalStringVariable>
    lateinit var paladinVariable: CharacterVariable
    val ONE_MINUTE = 20L * 60L

    override val commands: List<Command> = listOf(object : Command("debugleaderboard") {
        init {
            description = "Debug command for bounty leaderboard cache"
            usage = "/debugleaderboard <bounty|paladin>"
            permission = "sneakymisc.command.debugleaderboard"
        }

        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
            if (!plugin.isEnabled) {
                sender.sendMessage("Plugin is disabled")
                return true
            }
            if (!::bountyVariable.isInitialized) {
                sender.sendMessage("MSVariables not initialized")
                return true
            }
            val type = args.getOrNull(0)
            if (type == null) {
                sender.sendMessage("Usage: $usage")
                return true
            }
            when (type.lowercase()) {
                "bounty" -> {
                    sender.sendMessage("Bounty Cache Entries (${bountyCache.size}):")
                    val sortedEntries = bountyCache.values.sortedByDescending { it.value }.take(32)
                    for (entry in sortedEntries) sender.sendMessage(" - ${entry.characterName}: ${entry.value}")
                    return true
                }
                "paladin" -> {
                    sender.sendMessage("Paladin Cache Entries (${paladinCache.size}):")
                    val sortedEntries = paladinCache.values.sortedByDescending { it.value }.take(32)
                    for (entry in sortedEntries) sender.sendMessage(" - ${entry.characterName}: ${entry.value}")
                    return true
                }
                else -> {
                    sender.sendMessage("Unknown leaderboard type: $type")
                    return true
                }
            }
        }
    })
    override val listeners: List<Listener> = listOf(object: Listener {
        @EventHandler
        fun onMSEnable(event: MagicSpellsLoadedEvent) {
            bountyLeaderboard = (1..32).mapNotNull { MagicSpells.getVariableManager().getVariable("bounty$it") as? GlobalStringVariable }
            val maybeBountyVariable = MagicSpells.getVariableManager().getVariable("bankBounty") as? CharacterVariable
            paladinLeaderboard = (1..32).mapNotNull { MagicSpells.getVariableManager().getVariable("toppaladin$it") as? GlobalStringVariable }
            val maybePaladinVariable = MagicSpells.getVariableManager().getVariable("paladinArrests") as? CharacterVariable

            if (bountyLeaderboard.size < 32 || paladinLeaderboard.size < 32) {
                if (bountyLeaderboard.size < 32)
                    plugin.logger.warning("Bounty leaderboard variables not properly configured. Expected 32, found ${bountyLeaderboard.size}.")
                if (paladinLeaderboard.size < 32)
                    plugin.logger.warning("Paladin leaderboard variables not properly configured. Expected 32, found ${paladinLeaderboard.size}.")
                Bukkit.getPluginManager().disablePlugin(plugin)
                return
            }
            if (maybeBountyVariable == null || maybePaladinVariable == null) {
                if (maybeBountyVariable == null)
                    plugin.logger.warning("Character variable 'bankBounty' not found.")
                if (maybePaladinVariable == null)
                    plugin.logger.warning("Character variable 'paladinArrests' not found.")
                Bukkit.getPluginManager().disablePlugin(plugin)
                return
            }
            bountyVariable = maybeBountyVariable
            paladinVariable = maybePaladinVariable
        }
        @EventHandler
        fun onCharacterLoad(event: LoadCharacterEvent) {
            if (!::bountyVariable.isInitialized) {
                plugin.logger.warning("MSVariables not initialized before LoadCharacterEvent")
                return
            }
            val currentCharacter = Character.get(event.player) ?: return
            val bountyValue = bountyVariable.getValue(event.player)
            val paladinValue = paladinVariable.getValue(event.player)
            updateBounty(currentCharacter, bountyValue)
            updatePaladin(currentCharacter, paladinValue)
        }
        @EventHandler
        fun onPlayerQuit(event: PlayerQuitEvent){
            if (!::bountyVariable.isInitialized) {
                plugin.logger.warning("MSVariables not initialized before PlayerQuitEvent")
                return
            }
            val currentCharacter = Character.get(event.player) ?: return
            val bountyValue = bountyVariable.getValue(event.player)
            val paladinValue = paladinVariable.getValue(event.player)
            updateBounty(currentCharacter, bountyValue)
            updatePaladin(currentCharacter, paladinValue)
        }
    })

    init {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (!::bountyVariable.isInitialized) {
                plugin.logger.warning("MSVariables not initialized before scheduled leaderboard update")
                return@Runnable
            }
            for (player in Bukkit.getOnlinePlayers()) {
                val character = Character.get(player) ?: continue
                val bountyValue = bountyVariable.getValue(player)
                val paladinValue = paladinVariable.getValue(player)
                updateBounty(character, bountyValue)
                updatePaladin(character, paladinValue)
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                updateScoreboards()
            })
        }, ONE_MINUTE, ONE_MINUTE)

    }

    fun updateBounty(character: Character, value: Double) {
        val characterUUID = UUID.fromString(character.characterUUID)
        if (value <= 1.0E-6) {
            bountyCache.remove(characterUUID)
            return
        }

        bountyCache.put(characterUUID, LeaderboardEntry(
            playerUUID = character.player.uniqueId,
            characterUUID = characterUUID,
            characterName = character.name,
            value = value
        ))
    }
    fun updatePaladin(character: Character, value: Double) {
        val characterUUID = UUID.fromString(character.characterUUID)
        if (value <= 1.0E-6) {
            paladinCache.remove(characterUUID)
            return
        }

        paladinCache.put(characterUUID, LeaderboardEntry(
            playerUUID = character.player.uniqueId,
            characterUUID = characterUUID,
            characterName = character.name,
            value = value
        ))
    }

    fun updateScoreboards() {
        val sortedBounties = bountyCache.values.sortedByDescending { it.value }.take(32) // sorted snapshot
        for (i in bountyLeaderboard.indices) {
            val variable = bountyLeaderboard[i]
            val entry = sortedBounties.getOrNull(i)
            if (entry == null) {
                variable.parseAndSet("null", "")
                continue
            }
            val formattedValue = String.format("%,d", entry.value.roundToLong())
            variable.parseAndSet("null", "${entry.characterName} $formattedValue")
        }
        val sortedPaladins = paladinCache.values.sortedByDescending { it.value }.take(32) // sorted snapshot
        for (i in paladinLeaderboard.indices) {
            val variable = paladinLeaderboard[i]
            val entry = sortedPaladins.getOrNull(i)
            if (entry == null) {
                variable.parseAndSet("null", "")
                continue
            }
            val formattedValue = String.format("%,d", entry.value.roundToLong())
            variable.parseAndSet("null", "${entry.characterName} $formattedValue")
        }
    }
}