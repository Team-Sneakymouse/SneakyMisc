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
    fun `phonebook message catalog owns key named arguments and default translation`() {
        val entry = PhonebookMessageCatalog.entry(PhonebookMessageKeys.TARGET_OFFLINE)

        assertEquals(PhonebookMessageKeys.TARGET_OFFLINE, entry.key)
        assertEquals(listOf("character"), entry.argumentNames)
        assertEquals("<red><gold>{0}</gold> is no longer reachable.", entry.defaultMiniMessage)
    }

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

    @Test
    fun `default phonebook translations cover every catalog entry`() {
        PhonebookTranslations.registerDefaults()

        PhonebookMessageCatalog.entries.forEach { entry ->
            val arguments = entry.argumentNames.map { name -> Component.text(name) }

            assertFalse(
                GlobalTranslator.render(Component.translatable(entry.key, arguments), Locale.US) is TranslatableComponent,
                "Default translation did not resolve for ${entry.key}",
            )
        }
    }

    @Test
    fun `listing feedback keys resolve without named arguments`() {
        PhonebookTranslations.registerDefaults()

        assertEquals(emptyList(), PhonebookMessageKeys.argumentNames(PhonebookMessageKeys.LISTED))
        assertEquals(emptyList(), PhonebookMessageKeys.argumentNames(PhonebookMessageKeys.UNLISTED))
        assertFalse(GlobalTranslator.render(Component.translatable(PhonebookMessageKeys.LISTED), Locale.US) is TranslatableComponent)
        assertFalse(GlobalTranslator.render(Component.translatable(PhonebookMessageKeys.UNLISTED), Locale.US) is TranslatableComponent)
    }

    @Test
    fun `contact removal feedback keys resolve with named Character arguments`() {
        PhonebookTranslations.registerDefaults()

        assertEquals(listOf("character"), PhonebookMessageKeys.argumentNames(PhonebookMessageKeys.CONTACT_REMOVED))
        assertEquals(listOf("character"), PhonebookMessageKeys.argumentNames(PhonebookMessageKeys.CONTACT_ALREADY_REMOVED))
        assertFalse(
            GlobalTranslator.render(
                PhonebookMessage(PhonebookMessageKeys.CONTACT_REMOVED, mapOf("character" to "Removed")).asComponent(),
                Locale.US,
            ) is TranslatableComponent
        )
        assertFalse(
            GlobalTranslator.render(
                PhonebookMessage(PhonebookMessageKeys.CONTACT_ALREADY_REMOVED, mapOf("character" to "Owner")).asComponent(),
                Locale.US,
            ) is TranslatableComponent
        )
    }
}
