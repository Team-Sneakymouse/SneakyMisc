package com.danidipp.sneakymisc.phonebook

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.translation.GlobalTranslator

class PhonebookMessageTest {
    @Test
    fun `phonebook message keeps named arguments until the Bukkit adapter converts them`() {
        val message = PhonebookMessage(
            PhonebookMessageKeys.TARGET_OFFLINE,
            mapOf("character" to "Reachable Character"),
        )

        val component = message.asComponent() as TranslatableComponent

        assertEquals(PhonebookMessageKeys.TARGET_OFFLINE, component.key())
        assertEquals(listOf("character"), PhonebookMessageKeys.argumentNames(component.key()))
        assertEquals(Component.text("Reachable Character"), component.arguments().single().value())
    }

    @Test
    fun `missing named message arguments fail at the adapter seam`() {
        val message = PhonebookMessage(PhonebookMessageKeys.TARGET_OFFLINE)

        assertFailsWith<IllegalArgumentException> {
            message.asComponent()
        }
    }

    @Test
    fun `default phonebook translations resolve through Adventure`() {
        PhonebookTranslations.registerDefaults()

        val rendered = GlobalTranslator.render(
            Component.translatable(PhonebookMessageKeys.NO_ACTIVE_CHARACTER),
            Locale.US,
        )

        assertFalse(rendered is TranslatableComponent)
    }
}
