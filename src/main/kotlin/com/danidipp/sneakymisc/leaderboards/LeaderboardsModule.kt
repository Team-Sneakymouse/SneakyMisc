package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.leaderboards.commands.LeaderboardDebugCommand
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable
import net.sneakycharactermanager.paper.handlers.character.Character
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.SneakyPocketbase

class LeaderboardsModule(val plugin: SneakyMisc): SneakyModule {
    val leaderboards = mutableMapOf<String, Leaderboard>()
    val HALF_MINUTE = 20L * 30L
    
    private val db = LeaderboardDB(plugin.logger)

    override val commands: List<Command> = listOf(
        LeaderboardDebugCommand(plugin, this)
    )
    override val listeners: List<Listener> = listOf(object: Listener {
        @EventHandler
        fun onMSEnable(event: MagicSpellsLoadedEvent) {
            loadConfig()
            leaderboards.values.forEach { it.loadInitialData() }
        }

        @EventHandler
        fun onPocketbaseEvent(event: AsyncPocketbaseEvent) {
            val record = db.parseEvent(event) ?: return
            var leaderboard = leaderboards[record.leaderboard] ?: return
            leaderboard.handleRealtimeUpdate(record, event.action)
            plugin.logger.info("Received Pocketbase update for ${record.leaderboard} (name: ${record.name}, value: ${record.value}")
            
            // Immediately update displays for this leaderboard
            Bukkit.getScheduler().runTask(plugin, Runnable {
                leaderboard.updateDisplays()
            })
        }
    })

    init {
        val pb = SneakyPocketbase.getInstance()
        pb.onPocketbaseLoaded {
            plugin.logger.info("Subscribing to Pocketbase leaderboard updates ('${db.LEADERBOARDS_COLLECTION}')")
            pb.subscribeAsync(db.LEADERBOARDS_COLLECTION)
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            if (leaderboards.isEmpty()) return@Runnable
            
            val dateVariable = MagicSpells.getVariableManager().getVariable("leaderboardCount")
            for (player in Bukkit.getOnlinePlayers()) {
                val character = Character.get(player) ?: continue

                // Get player's specific date variable
                val dateInt = (dateVariable?.getValue(player) ?: 0.0).toInt()
                
                // Convert yyMMdd to ISO format with time 00:00:00 in UTC-7, or use current server date if invalid
                val playerDateStr = if (dateInt > 0) {
                   try {
                       val year = 2000 + (dateInt / 10000)
                       val month = (dateInt % 10000) / 100
                       val day = dateInt % 100
                       val localDate = LocalDate.of(year, month, day)
                       // 00:00:00 in UTC-7
                       localDate.atStartOfDay(ZoneId.of("UTC-07:00")).toInstant().toString()
                   } catch (e: Exception) {
                       // Current server date at 00:00:00 UTC-7
                       LocalDate.now(ZoneId.of("UTC-07:00")).atStartOfDay(ZoneId.of("UTC-07:00")).toInstant().toString()
                   }
                } else {
                    // Current server date at 00:00:00 UTC-7
                    LocalDate.now(ZoneId.of("UTC-07:00")).atStartOfDay(ZoneId.of("UTC-07:00")).toInstant().toString()
                }

                for ((_, leaderboard) in leaderboards) {
                    val value = leaderboard.valueVariable.getValue(player)
                    leaderboard.updateScore(character, value, playerDateStr)
                }
            }
        }, HALF_MINUTE, HALF_MINUTE)
    }

    private fun loadConfig() {
        val configFile = File(plugin.dataFolder, "leaderboards.yml")
        val leaderboardConfigs = LeaderboardConfig.load(configFile, plugin.logger)

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

            leaderboards[lbConfig.name] = Leaderboard(
                plugin = plugin,
                db = db,
                name = lbConfig.name,
                type = lbConfig.type,
                valueVariable = valueVariable,
                displayVariables = displayVariables
            )
            plugin.logger.info("Leaderboard '${lbConfig.name}' initialized with ${displayVariables.size} display variables")
        }
    }
}