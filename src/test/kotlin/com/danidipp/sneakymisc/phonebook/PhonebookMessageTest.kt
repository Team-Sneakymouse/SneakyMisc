package com.danidipp.sneakymisc.phonebook

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import java.util.PropertyResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class PhonebookMessageTest {
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
    fun `phonebook messages resource contains production keys`() {
        val missingKeys = productionPhonebookMessageKeys() - phonebookMessageResourceKeys()

        assertEquals(emptySet(), missingKeys)
    }

    @Test
    fun `phonebook messages resource entries render through the Bukkit adapter`() {
        PhonebookTranslations.registerDefaults(phonebookMessagesPath())

        phonebookMessageResourceKeys().forEach { key ->
            assertFalse(
                PhonebookMessage(key, mapOf("character" to Component.text("Character"))).asComponent() is TranslatableComponent,
                "Default translation did not resolve for $key",
            )
        }
    }

    @Test
    fun `named MiniMessage arguments render from the messages resource`() {
        PhonebookTranslations.registerDefaults(phonebookMessagesPath())

        val rendered = PhonebookMessage(
            "sneakymisc.phonebook.target_offline",
            mapOf("character" to Component.text("Reachable Character", NamedTextColor.AQUA)),
        ).asComponent()

        assertFalse(rendered is TranslatableComponent)
        assertEquals("Reachable Character is no longer reachable.", PlainTextComponentSerializer.plainText().serialize(rendered))
        assertEquals(NamedTextColor.AQUA, rendered.children().first().color())
    }

    @Test
    fun `phonebook messages can render before being written to item meta`() {
        PhonebookTranslations.registerDefaults(phonebookMessagesPath())

        val rendered = PhonebookMessage("sneakymisc.phonebook.exchange.accept").asComponent()

        assertFalse(rendered is TranslatableComponent)
        assertEquals("Accept", PlainTextComponentSerializer.plainText().serialize(rendered))
        assertEquals(NamedTextColor.GREEN, rendered.color())
    }

    private fun phonebookMessagesPath(): Path {
        val resource = requireNotNull(javaClass.classLoader.getResource("messages/phonebook.txt")) {
            "Missing test resource messages/phonebook.txt"
        }
        return Path.of(resource.toURI())
    }

    private fun phonebookSourcePath(): Path =
        Path.of("src/main/kotlin/com/danidipp/sneakymisc/phonebook")

    private fun phonebookMessageResourceKeys(): Set<String> =
        phonebookMessagesPath().toFile().reader(StandardCharsets.UTF_8).use { reader ->
            PropertyResourceBundle(reader).keySet()
        }

    private fun productionPhonebookMessageKeys(): Set<String> {
        val permissionKeys = setOf(
            "sneakymisc.phonebook",
            "sneakymisc.phonebook.debug",
        )
        val keyPattern = Regex("\"(sneakymisc\\.phonebook(?:\\.[A-Za-z0-9_]+)*)\"")

        val sourceFiles = Files.walk(phonebookSourcePath()).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .toList()
        }

        return sourceFiles
            .flatMap { path -> keyPattern.findAll(path.readText()).map { it.groupValues[1] }.toList() }
            .filter { it !in permissionKeys }
            .toSet()
    }
}
