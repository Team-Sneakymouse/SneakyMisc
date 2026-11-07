package com.danidipp.sneakymisc.dclock

import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DClockConfigTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    private fun file(content: String? = null) = File(createTempDirectory().toFile(), "dclock.yml").also { f ->
        content?.let { f.writeText(it) }
    }

    @Test
    fun `saves and reloads`() {
        val file = file()
        DClockConfig(mapOf("spawn" to listOf(a, b))).save(file)
        val loaded = DClockConfig.load(file)
        assertEquals(mapOf("spawn" to listOf(a, b)), loaded.displays)
        assertTrue(loaded.problems.isEmpty())
    }

    @Test
    fun `broken yaml is a problem and cannot be saved over`() {
        val loaded = DClockConfig.load(file("displays: [unclosed"))
        assertTrue(loaded.problems.isNotEmpty())
        assertFailsWith<IllegalStateException> { loaded.save(file()) }
    }

    @Test
    fun `invalid uuids and dotted names are problems`() {
        val loaded = DClockConfig.load(file("displays:\n  spawn:\n  - $a\n  - nope\n  a:\n    b:\n    - $b\n"))
        assertEquals(emptyMap(), loaded.displays)
        assertEquals(2, loaded.problems.size)
    }

    @Test
    fun `display names allow only yaml-safe characters`() {
        assertTrue(DClockConfig.DISPLAY_NAME.matches("spawn_main-2"))
        assertTrue(!DClockConfig.DISPLAY_NAME.matches("spawn.main"))
    }
}
