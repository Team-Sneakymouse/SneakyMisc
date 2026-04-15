package com.danidipp.sneakymisc.paintings

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Path

data class PaintingDefinition(
    val key: NamespacedKey,
    val width: Int,
    val height: Int,
    val assetId: NamespacedKey,
    val title: Component?,
    val author: Component?,
)

data class PaintingsConfigResult(
    val definitions: Map<NamespacedKey, PaintingDefinition>,
    val skippedEntries: List<String>,
)

object PaintingsConfig {
    private val miniMessage = MiniMessage.miniMessage()
    private val strictMiniMessage = MiniMessage.builder()
        .strict(true)
        .build()

    fun load(
        configPath: Path,
        info: (String) -> Unit,
        warn: (String) -> Unit,
    ): PaintingsConfigResult {
        val configFile = configPath.toFile()
        if (!configFile.exists()) {
            info("No paintings.yml found at ${configFile.absolutePath}, no custom paintings will be registered")
            return PaintingsConfigResult(emptyMap(), emptyList())
        }

        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val definitions = linkedMapOf<NamespacedKey, PaintingDefinition>()
        val skippedEntries = mutableListOf<String>()

        for (entryKey in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(entryKey)
            if (section == null) {
                skippedEntries += skip(entryKey, "Invalid configuration section", warn)
                continue
            }

            val paintingKey = parseExplicitNamespacedKey(entryKey)
            if (paintingKey == null) {
                skippedEntries += skip(entryKey, "Invalid painting id '$entryKey'", warn)
                continue
            }

            if (!section.isInt("width")) {
                skippedEntries += skip(entryKey, "Missing or invalid integer 'width'", warn)
                continue
            }
            if (!section.isInt("height")) {
                skippedEntries += skip(entryKey, "Missing or invalid integer 'height'", warn)
                continue
            }

            val width = section.getInt("width")
            if (width !in 1..16) {
                skippedEntries += skip(entryKey, "Width must be between 1 and 16, found $width", warn)
                continue
            }

            val height = section.getInt("height")
            if (height !in 1..16) {
                skippedEntries += skip(entryKey, "Height must be between 1 and 16, found $height", warn)
                continue
            }

            val assetIdRaw = section.getString("asset-id")?.trim().orEmpty()
            val assetId = parseExplicitNamespacedKey(assetIdRaw)
            if (assetId == null) {
                skippedEntries += skip(entryKey, "Missing or invalid namespaced 'asset-id'", warn)
                continue
            }

            val title = parseOptionalMiniMessage(entryKey, "title", section.getString("title"), warn)
            if (title == null) {
                skippedEntries += entryKey
                continue
            }

            val author = parseOptionalMiniMessage(entryKey, "author", section.getString("author"), warn)
            if (author == null) {
                skippedEntries += entryKey
                continue
            }

            definitions[paintingKey] = PaintingDefinition(
                key = paintingKey,
                width = width,
                height = height,
                assetId = assetId,
                title = title.component,
                author = author.component,
            )
        }

        return PaintingsConfigResult(definitions, skippedEntries)
    }

    private fun parseOptionalMiniMessage(
        entryKey: String,
        fieldName: String,
        value: String?,
        warn: (String) -> Unit,
    ): MiniMessageParseResult? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return MiniMessageParseResult(null)

        if (trimmed.count { it == '<' } != trimmed.count { it == '>' }) {
            warn("Painting '$entryKey': Invalid MiniMessage in '$fieldName': Unbalanced tag delimiters")
            return null
        }

        if (trimmed.contains("</")) {
            val validation = runCatching {
                strictMiniMessage.deserialize(trimmed)
            }
            if (validation.isFailure) {
                warn("Painting '$entryKey': Invalid MiniMessage in '$fieldName': ${validation.exceptionOrNull()?.message}")
                return null
            }
        }

        val component = runCatching {
            miniMessage.deserialize(trimmed)
        }.getOrElse {
            warn("Painting '$entryKey': Invalid MiniMessage in '$fieldName': ${it.message}")
            return null
        }

        return MiniMessageParseResult(component)
    }

    private fun parseExplicitNamespacedKey(value: String): NamespacedKey? {
        if (!value.contains(':')) return null
        return NamespacedKey.fromString(value)
    }

    private fun skip(entryKey: String, reason: String, warn: (String) -> Unit): String {
        warn("Painting '$entryKey': $reason")
        return entryKey
    }

    private data class MiniMessageParseResult(val component: net.kyori.adventure.text.Component?)
}
