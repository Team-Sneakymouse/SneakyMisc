package com.danidipp.sneakymisc.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatModuleTest {
    @Test
    fun `dependencies can be read without WorldGuard installed`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("com.sk89q.worldguard.protection.flags.registry.FlagConflictException")
        }

        assertEquals(listOf("WorldGuard", "PlaceholderAPI"), ChatModule.deps)
    }
}
