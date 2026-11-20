package com.danidipp.sneakymisc.worldpaste

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldPastePresetsTest {
    @Test
    fun `preset names are unique command words and every schematic path stays inside the folder`() {
        val names = WorldPastePresets.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (preset in WorldPastePresets.ALL) {
            assertTrue(preset.name.matches(Regex("[a-z0-9_-]+")), preset.name)
            assertEquals(3, preset.origin.size, preset.name)
            assertTrue(preset.schematics.isNotEmpty(), preset.name)
            preset.schematics.forEach { assertTrue(WorldPastePresets.isRelativeInside(it), "${preset.name}: $it") }
        }
    }

    @Test
    fun `relative paths may use subfolders but not leave the folder`() {
        assertTrue(WorldPastePresets.isRelativeInside("darkvale/darkvalescar1.schem"))
        assertFalse(WorldPastePresets.isRelativeInside("../../server.properties"))
        assertFalse(WorldPastePresets.isRelativeInside("/etc/passwd"))
        assertFalse(WorldPastePresets.isRelativeInside("C:\\schem.schem"))
        assertFalse(WorldPastePresets.isRelativeInside("darkvale/"))
    }
}
