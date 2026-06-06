package com.danidipp.sneakymisc.phonebook

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.PropertyResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator

class PhonebookMessageTest {
    @Test
    fun `Bukkit adapter converts phonebook messages to named component arguments`() {
        val message = PhonebookMessage(
            "sneakymisc.phonebook.target_offline",
            mapOf("character" to Component.text("Reachable Character")),
        )

        val component = message.asComponent() as TranslatableComponent

        assertEquals("sneakymisc.phonebook.target_offline", component.key())
        assertEquals(1, component.arguments().size)
        assertTrue(component.arguments().single().value() is Component)
    }

    @Test
    fun `phonebook messages resource exists and contains translations`() {
        val keys = phonebookMessageResourceKeys()

        assertTrue(Files.isRegularFile(phonebookMessagesPath()))
        assertTrue(keys.isNotEmpty(), "Expected at least one phonebook translation")
    }

    @Test
    fun `phonebook messages resource has no duplicate keys`() {
        val duplicateKeys = Files.readAllLines(phonebookMessagesPath(), StandardCharsets.UTF_8)
            .mapNotNull { line -> line.substringBefore("=", missingDelimiterValue = "").takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        assertEquals(emptySet(), duplicateKeys)
    }

    @Test
    fun `phonebook messages resource entries resolve through Adventure`() {
        PhonebookTranslations.registerDefaults(phonebookMessagesPath())

        phonebookMessageResourceKeys().forEach { key ->
            assertFalse(
                GlobalTranslator.render(PhonebookMessage(key, mapOf("character" to Component.text("Character"))).asComponent(), Locale.US) is TranslatableComponent,
                "Default translation did not resolve for $key",
            )
        }
    }

    @Test
    fun `named MiniMessage arguments render from the messages resource`() {
        PhonebookTranslations.registerDefaults(phonebookMessagesPath())

        val rendered = GlobalTranslator.render(
            PhonebookMessage(
                "sneakymisc.phonebook.target_offline",
                mapOf("character" to Component.text("Reachable Character", NamedTextColor.AQUA)),
            ).asComponent(),
            Locale.US,
        )

        assertFalse(rendered is TranslatableComponent)
        assertEquals("Reachable Character is no longer reachable.", PlainTextComponentSerializer.plainText().serialize(rendered))
        assertEquals(NamedTextColor.AQUA, rendered.children().first().color())
    }

    private fun phonebookMessagesPath(): Path {
        val resource = requireNotNull(javaClass.classLoader.getResource("messages/phonebook.txt")) {
            "Missing test resource messages/phonebook.txt"
        }
        return Path.of(resource.toURI())
    }

    private fun phonebookMessageResourceKeys(): Set<String> =
        phonebookMessagesPath().toFile().reader(StandardCharsets.UTF_8).use { reader ->
            PropertyResourceBundle(reader).keySet()
        }
}
