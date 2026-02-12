package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent
import com.nisovin.magicspells.variables.Variable
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable
import io.ktor.util.collections.ConcurrentMap
import net.sneakycharactermanager.paper.handlers.character.Character
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong

data class LeaderboardEntry(
    val playerUUID: UUID,
    val characterUUID: UUID,
    val characterName: String,
    val value: Double
)

class LeaderboardsModule(val plugin: SneakyMisc): SneakyModule {
    val leaderboards = mutableMapOf<String, Leaderboard>()
    val ONE_MINUTE = 20L * 60L

    inner class Leaderboard(
        val name: String,
        val type: LeaderboardType,
        val valueVariable: Variable,
        val displayVariables: List<GlobalStringVariable>,
        val rewardRules: List<RewardRule>
    ) {
        val userCache = ConcurrentMap<UUID, LeaderboardEntry>()

        fun update(character: Character, value: Double) {
            val playerUUID = character.player.uniqueId
            val characterUUID = UUID.fromString(character.characterUUID)
            val cacheKey = if (type == LeaderboardType.PLAYER) playerUUID else characterUUID
            
            if (value <= 1.0E-6) {
                userCache.remove(cacheKey)
                return
            }

            userCache.put(cacheKey, LeaderboardEntry(
                playerUUID = playerUUID,
                characterUUID = characterUUID,
                characterName = character.name,
                value = value
            ))
        }

        fun updateDisplays() {
            val sortedEntries = userCache.values.sortedByDescending { it.value }.take(displayVariables.size)
            for (i in displayVariables.indices) {
                val variable = displayVariables[i]
                val entry = sortedEntries.getOrNull(i)
                if (entry == null) {
                    variable.parseAndSet("null", "")
                    continue
                }
                val formattedValue = String.format("%,d", entry.value.roundToLong())
                variable.parseAndSet("null", "${entry.characterName} $formattedValue")
            }
        }
    }

    override val commands: List<Command> = listOf(
        LeaderboardDebugCommand(plugin, this),
        LeaderboardRewardsCommand(plugin, this),
    )

    override val listeners: List<Listener> = listOf(object: Listener {
        @EventHandler
        fun onMSEnable(event: MagicSpellsLoadedEvent) {
            loadConfig()
        }

        @EventHandler
        fun onCharacterLoad(event: LoadCharacterEvent) {
            if (leaderboards.isEmpty()) {
                return
            }
            val currentCharacter = Character.get(event.player) ?: return
            for ((_, leaderboard) in leaderboards) {
                val value = leaderboard.valueVariable.getValue(event.player)
                leaderboard.update(currentCharacter, value)
            }
        }

        @EventHandler
        fun onPlayerQuit(event: PlayerQuitEvent){
            if (leaderboards.isEmpty()) {
                return
            }
            val currentCharacter = Character.get(event.player) ?: return
            for ((_, leaderboard) in leaderboards) {
                val value = leaderboard.valueVariable.getValue(event.player)
                leaderboard.update(currentCharacter, value)
            }
        }
    })

    init {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (leaderboards.isEmpty()) {
                return@Runnable
            }
            for (player in Bukkit.getOnlinePlayers()) {
                val character = Character.get(player) ?: continue
                for ((_, leaderboard) in leaderboards) {
                    val value = leaderboard.valueVariable.getValue(player)
                    leaderboard.update(character, value)
                }
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                updateDisplays()
            })
        }, ONE_MINUTE, ONE_MINUTE)
    }

    private fun loadConfig() {
        val configFile = File(plugin.dataFolder, "leaderboards.yml")
        val leaderboardConfigs = LeaderboardConfig.load(configFile, plugin.logger)

        // Create leaderboard instances from validated configs
        for (lbConfig in leaderboardConfigs) {
            val valueVariable = MagicSpells.getVariableManager().getVariable(lbConfig.valueVariable)
            if (valueVariable == null) {
                plugin.logger.warning("Leaderboard '${lbConfig.name}': Variable '${lbConfig.valueVariable}' not found")
                continue
            }

            val displayVariables = mutableListOf<GlobalStringVariable>()
            var hasError = false
            for (displayVarName in lbConfig.displayVariables) {
                val displayVar = MagicSpells.getVariableManager().getVariable(displayVarName) as? GlobalStringVariable
                if (displayVar == null) {
                    plugin.logger.warning("Leaderboard '${lbConfig.name}': Display variable '$displayVarName' not found")
                    hasError = true
                    break
                }
                displayVariables.add(displayVar)
            }

            if (hasError) continue

            // Parse reward rules
            val rewardRules = mutableListOf<RewardRule>()
            lbConfig.rewards?.forEach { (rangeStr, rewardSpell) ->
                try {
                    val range = when {
                        rangeStr.contains("-") -> {
                            val parts = rangeStr.split("-")
                            parts[0].toInt()..parts[1].toInt()
                        }
                        rangeStr.endsWith("+") -> {
                            val start = rangeStr.dropLast(1).toInt()
                            start..Int.MAX_VALUE
                        }
                        else -> {
                            val single = rangeStr.toInt()
                            single..single
                        }
                    }
                    rewardRules.add(RewardRule(range, rewardSpell))
                } catch (e: NumberFormatException) {
                    plugin.logger.warning("Leaderboard '${lbConfig.name}': Invalid reward range format '$rangeStr'. Use 'X', 'X-Y', or 'X+'.")
                }
            }

            leaderboards[lbConfig.name] = Leaderboard(
                name = lbConfig.name,
                type = lbConfig.type,
                valueVariable = valueVariable,
                displayVariables = displayVariables,
                rewardRules = rewardRules
            )
            plugin.logger.info("Leaderboard '${lbConfig.name}' initialized with ${displayVariables.size} display variables and ${rewardRules.size} reward rules")
        }
    }

    fun updateDisplays() {
        for ((_, leaderboard) in leaderboards) {
            leaderboard.updateDisplays()
        }
    }
}