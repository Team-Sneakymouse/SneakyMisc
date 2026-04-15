package com.danidipp.sneakymisc.paintings

import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey

class PaintingsConfigTest {
    @Test
    fun `missing file returns empty result`() {
        val configPath = createTempFile(prefix = "paintings-missing", suffix = ".yml")
        configPath.deleteIfExists()
        val infos = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val result = PaintingsConfig.load(configPath, infos::add, warnings::add)

        assertTrue(result.definitions.isEmpty())
        assertTrue(result.skippedEntries.isEmpty())
        assertEquals(1, infos.size)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `valid yaml loads definitions and parses minimessage`() {
        val configPath = createTempFile(prefix = "paintings-valid", suffix = ".yml")
        configPath.writeText(
            """
            sneakymisc:moonrise:
              width: 2
              height: 3
              asset-id: sneakymisc:moonrise_asset
              title: "<gold>Moonrise"
              author: "<gray><italic>DaniDipp"
            """.trimIndent()
        )

        val result = PaintingsConfig.load(configPath, {}, {})
        val definition = result.definitions.getValue(NamespacedKey.fromString("sneakymisc:moonrise")!!)

        assertEquals(2, definition.width)
        assertEquals(3, definition.height)
        assertEquals(NamespacedKey.fromString("sneakymisc:moonrise_asset"), definition.assetId)
        assertEquals(MiniMessage.miniMessage().deserialize("<gold>Moonrise"), definition.title)
        assertEquals(MiniMessage.miniMessage().deserialize("<gray><italic>DaniDipp"), definition.author)
        assertTrue(result.skippedEntries.isEmpty())
    }

    @Test
    fun `invalid entries are skipped`() {
        val configPath = createTempFile(prefix = "paintings-invalid", suffix = ".yml")
        configPath.writeText(
            """
            invalid-id:
              width: 2
              height: 2
              asset-id: sneakymisc:valid
            sneakymisc:bad-width:
              width: 0
              height: 2
              asset-id: sneakymisc:valid
            sneakymisc:missing-asset:
              width: 2
              height: 2
            sneakymisc:good:
              width: 1
              height: 1
              asset-id: sneakymisc:good
            """.trimIndent()
        )

        val warnings = mutableListOf<String>()
        val result = PaintingsConfig.load(configPath, {}, warnings::add)

        assertEquals(listOf("sneakymisc:good"), result.definitions.keys.map { it.asString() })
        assertEquals(3, result.skippedEntries.size)
        assertEquals(3, warnings.size)
    }

    @Test
    fun `blank title and author are treated as absent`() {
        val configPath = createTempFile(prefix = "paintings-blank", suffix = ".yml")
        configPath.writeText(
            """
            sneakymisc:plain:
              width: 1
              height: 1
              asset-id: sneakymisc:plain
              title: "   "
              author: ""
            """.trimIndent()
        )

        val result = PaintingsConfig.load(configPath, {}, {})
        val definition = result.definitions.getValue(NamespacedKey.fromString("sneakymisc:plain")!!)

        assertNull(definition.title)
        assertNull(definition.author)
    }

    @Test
    fun `invalid minimessage skips entry`() {
        val configPath = createTempFile(prefix = "paintings-mm", suffix = ".yml")
        configPath.writeText(
            """
            sneakymisc:broken:
              width: 1
              height: 1
              asset-id: sneakymisc:broken
              title: "<red><bold>Broken</red>"
            sneakymisc:good:
              width: 1
              height: 1
              asset-id: sneakymisc:good
              title: "<green>Good"
            """.trimIndent()
        )

        val warnings = mutableListOf<String>()
        val result = PaintingsConfig.load(configPath, {}, warnings::add)

        assertEquals(listOf("sneakymisc:good"), result.definitions.keys.map { it.asString() })
        assertEquals(1, result.skippedEntries.size)
        assertTrue(warnings.any { it.contains("Invalid MiniMessage") })
    }
}
