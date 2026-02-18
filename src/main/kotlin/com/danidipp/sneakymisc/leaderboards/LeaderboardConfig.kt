package com.danidipp.sneakymisc.leaderboards

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

enum class LeaderboardType {
    PLAYER,
    CHARACTER
}


data class LeaderboardConfig(
    val name: String,
    val type: LeaderboardType,
    val valueVariable: String,
    val displayVariables: List<String>,
) {
    companion object {
        fun load(configFile: File, logger: Logger): List<LeaderboardConfig> {
            if (!configFile.exists()) {
                logger.info("No leaderboards.yml found, no leaderboards will be created")
                return emptyList()
            }

            val config = YamlConfiguration.loadConfiguration(configFile)
            val leaderboardConfigs = mutableListOf<LeaderboardConfig>()

            // Parse each top-level key as a leaderboard
            for (key in config.getKeys(false)) {
                val section = config.getConfigurationSection(key)
                if (section == null) {
                    logger.warning("Leaderboard '$key': Invalid configuration section")
                    continue
                }

                val typeStr = section.getString("type")
                val values = section.getStringList("values")
                val display = section.getStringList("display")
                
                // Validate configuration
                val type = try {
                    LeaderboardType.valueOf(typeStr!!.uppercase())
                } catch (e: Exception) {
                    logger.warning("Leaderboard '$key': Unknown type '$typeStr' (supported: 'player', 'character')")
                    continue
                }

                if (values.isEmpty()) {
                    logger.warning("Leaderboard '$key': 'values' list is empty")
                    continue
                }
                if (values.size > 1) {
                    logger.warning("Leaderboard '$key': Only supports a single value variable, found ${values.size}")
                    continue
                }
                if (display.isEmpty()) {
                    logger.warning("Leaderboard '$key': 'display' list is empty")
                    continue
                }

                leaderboardConfigs.add(LeaderboardConfig(
                    name = key,
                    type = type,
                    valueVariable = values[0],
                    displayVariables = display
                ))
            }
            return leaderboardConfigs
        }
    }
}
