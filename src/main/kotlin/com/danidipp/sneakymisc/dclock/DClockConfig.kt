package com.danidipp.sneakymisc.dclock

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.logging.Logger

enum class OvertimeBehavior {
    FREEZE,
    COUNT_UP;

    companion object {
        fun parse(value: String?): OvertimeBehavior? {
            return when (value?.lowercase()) {
                "freeze" -> FREEZE
                "count-up" -> COUNT_UP
                else -> null
            }
        }
    }

    fun configValue(): String {
        return when (this) {
            FREEZE -> "freeze"
            COUNT_UP -> "count-up"
        }
    }
}

enum class SourceValueMode {
    UNIX_SECONDS,
    LITERAL;

    companion object {
        fun parse(value: String?): SourceValueMode? {
            return when (value?.lowercase()) {
                null, "", "unix-seconds" -> UNIX_SECONDS
                "literal" -> LITERAL
                else -> null
            }
        }
    }

    fun configValue(): String {
        return when (this) {
            UNIX_SECONDS -> "unix-seconds"
            LITERAL -> "literal"
        }
    }
}

sealed interface SourceConfig

data class PocketBaseSourceConfig(
    val recordId: String,
    val valueMode: SourceValueMode,
) : SourceConfig

data class MagicSpellsSourceConfig(
    val variableName: String,
    val valueMode: SourceValueMode,
) : SourceConfig

data class DisplayConfig(
    var overtimeBehavior: OvertimeBehavior,
    val digits: MutableList<UUID>,
)

data class ClockConfig(
    val source: SourceConfig,
    var overtimeCommand: String?,
    val displays: MutableMap<String, DisplayConfig>,
)

data class DClockConfig(
    val clocks: MutableMap<String, ClockConfig>,
) {
    companion object {
        fun load(configFile: File, logger: Logger): DClockConfig {
            if (!configFile.exists()) {
                return DClockConfig(mutableMapOf())
            }

            val yaml = YamlConfiguration.loadConfiguration(configFile)
            val clocksSection = yaml.getConfigurationSection("clocks") ?: return DClockConfig(mutableMapOf())
            val clocks = linkedMapOf<String, ClockConfig>()

            for (clockName in clocksSection.getKeys(false)) {
                val clockSection = clocksSection.getConfigurationSection(clockName)
                if (clockSection == null) {
                    logger.warning("DClock '$clockName': invalid clock section")
                    continue
                }

                val source = loadSourceConfig(clockName, clockSection.getConfigurationSection("source"), logger) ?: continue
                val displays = loadDisplays(clockName, clockSection.getConfigurationSection("displays"), logger)
                clocks[clockName] = ClockConfig(
                    source = source,
                    overtimeCommand = clockSection.getString("overtimeCommand")?.takeIf { it.isNotBlank() },
                    displays = displays,
                )
            }

            return DClockConfig(clocks.toMutableMap())
        }

        private fun loadSourceConfig(clockName: String, section: ConfigurationSection?, logger: Logger): SourceConfig? {
            if (section == null) {
                logger.warning("DClock '$clockName': missing source section")
                return null
            }

            return when (section.getString("type")?.lowercase()) {
                "pocketbase" -> {
                    val recordId = section.getString("record")
                    if (recordId.isNullOrBlank()) {
                        logger.warning("DClock '$clockName': missing source.record for pocketbase source")
                        null
                    } else {
                        val valueMode = SourceValueMode.parse(section.getString("valueMode"))
                        if (valueMode == null) {
                            logger.warning("DClock '$clockName': invalid source.valueMode")
                            null
                        } else {
                            PocketBaseSourceConfig(recordId, valueMode)
                        }
                    }
                }

                "magicspells" -> {
                    val variable = section.getString("variable")
                    if (variable.isNullOrBlank()) {
                        logger.warning("DClock '$clockName': missing source.variable for magicspells source")
                        null
                    } else {
                        val valueMode = SourceValueMode.parse(section.getString("valueMode"))
                        if (valueMode == null) {
                            logger.warning("DClock '$clockName': invalid source.valueMode")
                            null
                        } else {
                            MagicSpellsSourceConfig(variable, valueMode)
                        }
                    }
                }

                else -> {
                    logger.warning("DClock '$clockName': unknown source type '${section.getString("type")}'")
                    null
                }
            }
        }

        private fun loadDisplays(
            clockName: String,
            section: ConfigurationSection?,
            logger: Logger,
        ): MutableMap<String, DisplayConfig> {
            if (section == null) return linkedMapOf<String, DisplayConfig>().toMutableMap()

            val displays = linkedMapOf<String, DisplayConfig>()
            for (displayName in section.getKeys(false)) {
                val displaySection = section.getConfigurationSection(displayName)
                if (displaySection == null) {
                    logger.warning("DClock '$clockName/$displayName': invalid display section")
                    continue
                }

                val overtimeBehavior = OvertimeBehavior.parse(displaySection.getString("overtimeBehavior"))
                if (overtimeBehavior == null) {
                    logger.warning("DClock '$clockName/$displayName': missing or invalid overtimeBehavior")
                    continue
                }

                if (!displaySection.isList("digits")) {
                    logger.warning("DClock '$clockName/$displayName': missing digits list")
                    continue
                }

                val digits = mutableListOf<UUID>()
                for (rawUuid in displaySection.getStringList("digits")) {
                    val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull()
                    if (uuid == null) {
                        logger.warning("DClock '$clockName/$displayName': invalid digit UUID '$rawUuid'")
                        continue
                    }
                    digits += uuid
                }

                if (digits.isEmpty()) {
                    logger.warning("DClock '$clockName/$displayName': digits list contains no valid UUIDs")
                    continue
                }

                displays[displayName] = DisplayConfig(
                    overtimeBehavior = overtimeBehavior,
                    digits = digits,
                )
            }

            return displays.toMutableMap()
        }
    }

    fun save(configFile: File) {
        val yaml = YamlConfiguration()
        val clocksSection = yaml.createSection("clocks")

        for ((clockName, clockConfig) in clocks) {
            val clockSection = clocksSection.createSection(clockName)
            val sourceSection = clockSection.createSection("source")
            when (val source = clockConfig.source) {
                is PocketBaseSourceConfig -> {
                    sourceSection.set("type", "pocketbase")
                    sourceSection.set("record", source.recordId)
                    sourceSection.set("valueMode", source.valueMode.configValue())
                }

                is MagicSpellsSourceConfig -> {
                    sourceSection.set("type", "magicspells")
                    sourceSection.set("variable", source.variableName)
                    sourceSection.set("valueMode", source.valueMode.configValue())
                }
            }

            clockConfig.overtimeCommand?.takeIf { it.isNotBlank() }?.let {
                clockSection.set("overtimeCommand", it)
            }

            val displaysSection = clockSection.createSection("displays")
            for ((displayName, displayConfig) in clockConfig.displays) {
                val displaySection = displaysSection.createSection(displayName)
                displaySection.set("overtimeBehavior", displayConfig.overtimeBehavior.configValue())
                displaySection.set("digits", displayConfig.digits.map(UUID::toString))
            }
        }

        yaml.save(configFile)
    }
}
