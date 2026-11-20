package com.danidipp.sneakymisc.worldpaste

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * One named paste: schematic files whose clipboard origins all go to [origin] in [world], then console
 * [afterCommands] (for example `darkvale crater rebuild`).
 */
internal data class WorldPastePreset(
    val name: String,
    val world: String,
    val origin: IntArray,
    val schematics: List<String>,
    val afterCommands: List<String>,
) {
    override fun equals(other: Any?) = other is WorldPastePreset && name == other.name && world == other.world &&
        origin.contentEquals(other.origin) && schematics == other.schematics && afterCommands == other.afterCommands

    override fun hashCode() = listOf(name, world, origin.toList(), schematics, afterCommands).hashCode()
}

internal data class WorldPastePresetsResult(
    val presets: Map<String, WorldPastePreset>,
    val problems: List<String>,
)

/** Reads `world-pastes.yml`. Read it fresh for every command so edits apply without a reload. */
internal object WorldPastePresets {
    fun load(file: File): WorldPastePresetsResult {
        if (!file.exists()) return WorldPastePresetsResult(emptyMap(), listOf("${file.path} does not exist"))
        return parse(YamlConfiguration.loadConfiguration(file))
    }

    fun parse(yaml: YamlConfiguration): WorldPastePresetsResult {
        val presets = linkedMapOf<String, WorldPastePreset>()
        val problems = mutableListOf<String>()
        for (name in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(name)
            if (section == null) {
                problems += "$name: not a section"
                continue
            }
            val world = section.getString("world")
            if (world.isNullOrBlank()) {
                problems += "$name: missing 'world'"
                continue
            }
            // Read the raw lists: Bukkit's typed getters quietly truncate decimals and drop entries of the wrong type.
            val origin = section.getList("origin")
            if (origin == null || origin.size != 3 || origin.any { it !is Int }) {
                problems += "$name: 'origin' must be a list of three whole numbers [x, y, z]"
                continue
            }
            val schematics = section.getList("schematics")
            if (schematics.isNullOrEmpty() || schematics.any { it !is String }) {
                problems += "$name: 'schematics' must be a list of at least one file name"
                continue
            }
            val afterCommands = section.getList("after-commands") ?: emptyList<Any>()
            if ((section.contains("after-commands") && !section.isList("after-commands")) || afterCommands.any { it !is String }) {
                problems += "$name: 'after-commands' must be a list of commands"
                continue
            }
            val files = schematics.map { it as String }
            val badName = files.firstOrNull { !isRelativeInside(it) }
            if (badName != null) {
                problems += "$name: schematic '$badName' must be a path inside FAWE's schematic folder"
                continue
            }
            presets[name] = WorldPastePreset(
                name = name,
                world = world,
                origin = origin.map { it as Int }.toIntArray(),
                schematics = files,
                afterCommands = afterCommands.map { (it as String).removePrefix("/") },
            )
        }
        return WorldPastePresetsResult(presets, problems)
    }

    /** A relative path with no `..` parts, e.g. `darkvale/crater1.schem`. The job also checks the resolved path. */
    private fun isRelativeInside(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\") || ':' in path) return false
        val parts = path.split('/', '\\')
        return parts.none { it == ".." } && parts.last().isNotEmpty()
    }
}
