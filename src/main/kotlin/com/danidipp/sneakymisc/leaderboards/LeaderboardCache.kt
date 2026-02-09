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
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
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

enum class LeaderboardType {
    PLAYER,
    CHARACTER
}

data class LeaderboardConfig(
    val name: String,
    val type: LeaderboardType,
    val valueVariable: String,
    val displayVariables: List<String>
)

class LeaderboardCache(val plugin: SneakyMisc): SneakyModule {
    private val leaderboards = mutableMapOf<String, Leaderboard>()
    val ONE_MINUTE = 20L * 60L

    inner class Leaderboard(
        val name: String,
        val type: LeaderboardType,
        val valueVariable: Variable,
        val displayVariables: List<GlobalStringVariable>
    ) {
        val cache = ConcurrentMap<UUID, LeaderboardEntry>()

        fun update(character: Character, value: Double) {
            val playerUUID = character.player.uniqueId
            val characterUUID = UUID.fromString(character.characterUUID)
            val cacheKey = if (type == LeaderboardType.PLAYER) playerUUID else characterUUID
            
            if (value <= 1.0E-6) {
                cache.remove(cacheKey)
                return
            }

            cache.put(cacheKey, LeaderboardEntry(
                playerUUID = playerUUID,
                characterUUID = characterUUID,
                characterName = character.name,
                value = value
            ))
        }

        fun updateScoreboard() {
            val sortedEntries = cache.values.sortedByDescending { it.value }.take(displayVariables.size)
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

    override val commands: List<Command> = listOf(object : Command("debugleaderboard") {
        init {
            description = "Debug command for leaderboard cache"
            usage = "/debugleaderboard [leaderboard_name]"
            permission = "sneakymisc.command.debugleaderboard"
        }

        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
            if (!plugin.isEnabled) {
                sender.sendMessage("Plugin is disabled")
                return true
            }
            
            val leaderboardName = args.getOrNull(0)
            
            // No arguments: list available leaderboards
            if (leaderboardName == null) {
                if (leaderboards.isEmpty()) {
                    sender.sendMessage("No leaderboards configured")
                } else {
                    sender.sendMessage("Available leaderboards: ${leaderboards.keys.joinToString(", ")}")
                }
                return true
            }
            
            // Valid leaderboard name: show cached entries
            val leaderboard = leaderboards[leaderboardName]
            if (leaderboard == null) {
                sender.sendMessage("Unknown leaderboard: $leaderboardName")
                return true
            }
            
            sender.sendMessage("$leaderboardName Cache Entries (${leaderboard.cache.size}):")
            val sortedEntries = leaderboard.cache.values.sortedByDescending { it.value }.take(32)
            for (entry in sortedEntries) {
                sender.sendMessage(" - ${entry.characterName}: ${entry.value}")
            }
            return true
        }
    })

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
                updateScoreboards()
            })
        }, ONE_MINUTE, ONE_MINUTE)
    }

    private fun loadConfig() {
        val configFile = File(plugin.dataFolder, "leaderboards.yml")
        if (!configFile.exists()) {
            plugin.logger.info("No leaderboards.yml found, no leaderboards will be created")
            return
        }

        val config = YamlConfiguration.loadConfiguration(configFile)
        val leaderboardConfigs = mutableListOf<LeaderboardConfig>()

        // Parse each top-level key as a leaderboard
        for (key in config.getKeys(false)) {
            val section = config.getConfigurationSection(key)
            if (section == null) {
                plugin.logger.warning("Leaderboard '$key': Invalid configuration section")
                continue
            }

            val type = section.getString("type")
            val values = section.getStringList("values")
            val display = section.getStringList("display")

            // Validate configuration
            val lbType = try {
                LeaderboardType.valueOf(type!!.uppercase())
            } catch (e: Exception) {
                plugin.logger.warning("Leaderboard '$key': Unknown type '$type' (supported: 'player', 'character')")
                continue
            }

            if (values.isEmpty()) {
                plugin.logger.warning("Leaderboard '$key': 'values' list is empty")
                continue
            }
            if (values.size > 1) {
                plugin.logger.warning("Leaderboard '$key': Only supports a single value variable, found ${values.size}")
                continue
            }
            if (display.isEmpty()) {
                plugin.logger.warning("Leaderboard '$key': 'display' list is empty")
                continue
            }

            leaderboardConfigs.add(LeaderboardConfig(
                name = key,
                type = lbType,
                valueVariable = values[0],
                displayVariables = display
            ))
        }

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

            if (hasError) {
                continue
            }

            leaderboards[lbConfig.name] = Leaderboard(
                name = lbConfig.name,
                type = lbConfig.type,
                valueVariable = valueVariable,
                displayVariables = displayVariables
            )
            plugin.logger.info("Leaderboard '${lbConfig.name}' initialized with ${displayVariables.size} display variables")
        }
    }

    fun updateScoreboards() {
        for ((_, leaderboard) in leaderboards) {
            leaderboard.updateScoreboard()
        }
    }
}