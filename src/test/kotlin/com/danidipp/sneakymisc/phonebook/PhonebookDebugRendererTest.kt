package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent

class PhonebookDebugRendererTest {
    @Test
    fun `uuid components expose raw uuid text with copy to clipboard click behavior`() {
        val uuid = uuid(1)

        val component = PhonebookDebugComponents.uuid(uuid)

        assertEquals(uuid.toString(), (component as TextComponent).content())
        assertEquals(ClickEvent.copyToClipboard(uuid.toString()), component.clickEvent())
    }

    @Test
    fun `debug rendering includes Character names and clickable raw UUIDs`() {
        val account = uuid(1)
        val characterId = uuid(100)
        val contactAccount = uuid(2)
        val contactCharacter = uuid(200)
        val model = PhonebookDebugModel(
            accountId = account,
            activeCharacterId = characterId,
            characters = listOf(
                PhonebookDebugCharacterSummary(
                    character = PhonebookCharacter(account, characterId, "Debug Character"),
                    active = true,
                    listed = true,
                    storedContactCount = 1,
                    visibleContactCount = 1,
                    missingContactCount = 0,
                    contactExamples = PhonebookDebugExamplePage(
                        totalCount = 1,
                        limit = 5,
                        examples = listOf(
                            PhonebookDebugContactExample(
                                characterId = contactCharacter,
                                accountId = contactAccount,
                                displayName = "Contact Character",
                                listed = true,
                                online = true,
                                visible = true,
                                missingCharacter = false,
                            )
                        ),
                    ),
                    missingContactExamples = PhonebookDebugExamplePage(totalCount = 0, limit = 5, examples = emptyList()),
                )
            ),
        )

        val components = PhonebookDebugRenderer().render(model)

        val text = components.joinToString("\n") { it.collectText() }
        assertTrue("Debug Character" in text)
        assertTrue("Contact Character" in text)
        assertTrue(account.toString() in text)
        assertTrue(characterId.toString() in text)
        assertTrue(ClickEvent.copyToClipboard(account.toString()) in components.collectClickEvents())
        assertTrue(ClickEvent.copyToClipboard(characterId.toString()) in components.collectClickEvents())
    }

    private fun uuid(value: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private fun Component.collectText(): String {
        val own = (this as? TextComponent)?.content().orEmpty()
        return own + children().joinToString("") { it.collectText() }
    }

    private fun List<Component>.collectClickEvents(): List<ClickEvent<*>> =
        flatMap { it.collectClickEvents() }

    private fun Component.collectClickEvents(): List<ClickEvent<*>> =
        listOfNotNull(clickEvent()) + children().flatMap { it.collectClickEvents() }
}
