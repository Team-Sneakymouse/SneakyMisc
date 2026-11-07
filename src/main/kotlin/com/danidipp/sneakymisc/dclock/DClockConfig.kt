package com.danidipp.sneakymisc.dclock

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/**
 * dclock.yml: the in-world clock displays, each an ordered list of digit entity UUIDs.
 *
 * ```yaml
 * displays:
 *   spawn:
 *     - 0b5c...
 * ```
 *
 * [problems] lists anything that could not be read. Saving such a config would drop those
 * entries, so the module refuses to edit displays until the file is fixed and reloaded.
 */
data class DClockConfig(
    val displays: Map<String, List<UUID>>,
    val problems: List<String> = emptyList(),
) {
    companion object {
        /** Display names are YAML keys and placeholder suffixes, so no dots or spaces. */
        val DISPLAY_NAME = Regex("[a-z0-9_-]+")

        fun load(configFile: File): DClockConfig {
            if (!configFile.exists()) return DClockConfig(emptyMap())
            val yaml = YamlConfiguration()
            try {
                yaml.load(configFile)
            } catch (e: Exception) {
                return DClockConfig(emptyMap(), listOf("cannot parse ${configFile.name}: ${e.message}"))
            }

            val displays = linkedMapOf<String, List<UUID>>()
            val problems = mutableListOf<String>()
            yaml.getConfigurationSection("displays")?.let { section ->
                for (name in section.getKeys(false)) {
                    if (!DISPLAY_NAME.matches(name)) {
                        problems += "display '$name': invalid name (allowed: a-z 0-9 _ -)"
                        continue
                    }
                    val raw = section.getStringList(name)
                    val digits = raw.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    when {
                        digits.isEmpty() -> problems += "display '$name': no valid digit UUIDs"
                        digits.size != raw.size -> problems += "display '$name': invalid digit UUIDs in $raw"
                        else -> displays[name] = digits
                    }
                }
            }
            return DClockConfig(displays, problems)
        }
    }

    fun save(configFile: File) {
        check(problems.isEmpty()) { "refusing to save a config that did not load cleanly" }
        val yaml = YamlConfiguration()
        val section = yaml.createSection("displays")
        for ((name, digits) in displays) section.set(name, digits.map(UUID::toString))
        yaml.save(configFile)
    }
}
