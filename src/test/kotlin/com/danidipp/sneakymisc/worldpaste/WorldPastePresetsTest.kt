package com.danidipp.sneakymisc.worldpaste

import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldPastePresetsTest {
    private fun parse(text: String) = WorldPastePresets.parse(YamlConfiguration().apply { loadFromString(text) })

    @Test
    fun `reads a preset with its after-commands`() {
        val result = parse(
            """
            darkvalescar:
              world: world
              origin: [4991, 60, 4850]
              schematics: [darkvalescar1.schem, darkvalescar2.schem]
              after-commands:
                - /darkvale crater rebuild
            """.trimIndent()
        )

        assertEquals(emptyList(), result.problems)
        assertEquals(
            WorldPastePreset(
                "darkvalescar", "world", intArrayOf(4991, 60, 4850),
                listOf("darkvalescar1.schem", "darkvalescar2.schem"), listOf("darkvale crater rebuild"),
            ),
            result.presets["darkvalescar"],
        )
    }

    @Test
    fun `rejects decimals in the origin and after-commands that aren't a list`() {
        val result = parse(
            """
            decimal:
              world: world
              origin: [4991.9, 60, 4850]
              schematics: [a.schem]
            scalar-commands:
              world: world
              origin: [1, 2, 3]
              schematics: [a.schem]
              after-commands: darkvale crater rebuild
            """.trimIndent()
        )

        assertTrue(result.presets.isEmpty())
        assertEquals(2, result.problems.size)
    }

    @Test
    fun `allows schematics in subfolders`() {
        val result = parse(
            """
            sub:
              world: world
              origin: [1, 2, 3]
              schematics: [darkvale/darkvalescar1.schem]
            """.trimIndent()
        )

        assertEquals(emptyList(), result.problems)
        assertEquals(listOf("darkvale/darkvalescar1.schem"), result.presets["sub"]?.schematics)
    }

    @Test
    fun `skips presets with a bad origin, no schematics or a path leaving the folder`() {
        val result = parse(
            """
            short-origin:
              world: world
              origin: [1, 2]
              schematics: [a.schem]
            no-files:
              world: world
              origin: [1, 2, 3]
            escape:
              world: world
              origin: [1, 2, 3]
              schematics: [../../server.properties]
            no-world:
              origin: [1, 2, 3]
              schematics: [a.schem]
            """.trimIndent()
        )

        assertTrue(result.presets.isEmpty())
        assertEquals(4, result.problems.size)
    }
}
