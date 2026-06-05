package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhonebookOpenHandlerTest {
    @Test
    fun `opening phonebook fails with a translation key when account has no active character`() {
        val viewer = RecordingPhonebookViewer(
            accountId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            permitted = true,
        )
        val directory = FakePhonebookDirectory(activeCharacters = emptyMap())
        val browser = PhonebookBrowser(
            resolver = PhonebookResolver(directory),
            renderer = PhonebookBrowserRenderer(),
            renderTokenProvider = { 1L },
        )

        val result = PhonebookOpenHandler(
            dataProvider = { PhonebookData() },
            directory = directory,
            browser = browser,
        ).openPhonebook(viewer)

        assertEquals(PhonebookOpenResult.NoActiveCharacter, result)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.NO_ACTIVE_CHARACTER)), viewer.messages)
        assertTrue(viewer.openedModels.isEmpty())
    }

    @Test
    fun `opening phonebook stops at the permission gate without resolving active character`() {
        val viewer = RecordingPhonebookViewer(
            accountId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            permitted = false,
        )
        val directory = FakePhonebookDirectory(activeCharacters = emptyMap())
        val browser = PhonebookBrowser(
            resolver = PhonebookResolver(directory),
            renderer = PhonebookBrowserRenderer(),
            renderTokenProvider = { 1L },
        )
        var loaded = false

        val result = PhonebookOpenHandler(
            dataProvider = {
                loaded = true
                PhonebookData()
            },
            directory = directory,
            browser = browser,
        ).openPhonebook(viewer)

        assertEquals(PhonebookOpenResult.NoPermission, result)
        assertEquals(false, loaded)
        assertEquals(0, directory.activeCharacterLookups)
        assertTrue(viewer.messages.isEmpty())
        assertTrue(viewer.openedModels.isEmpty())
    }

    @Test
    fun `opening phonebook renders reachable characters through the browser module`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val viewer = RecordingPhonebookViewer(accountId = viewerAccount, permitted = true)
        val directory = FakePhonebookDirectory(
            activeCharacters = mapOf(viewerAccount to ownerCharacter),
            characters = listOf(PhonebookCharacter(targetAccount, targetCharacter, "Reachable")),
            onlineAccounts = setOf(targetAccount),
        )
        val browser = PhonebookBrowser(
            resolver = PhonebookResolver(directory),
            renderer = PhonebookBrowserRenderer(),
            renderTokenProvider = { 42L },
        )
        val data = PhonebookData(
            listings = setOf(targetCharacter),
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                    PhonebookContact(ownerCharacter, viewerAccount, targetCharacter, targetAccount)
            ),
        )

        val result = PhonebookOpenHandler(
            dataProvider = { data },
            directory = directory,
            browser = browser,
        ).openPhonebook(viewer)

        assertEquals(PhonebookOpenResult.Opened, result)
        assertEquals(
            listOf(
                PhonebookBrowserModel(
                    PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 42L),
                    listOf(VisiblePhonebookContact(targetAccount, targetCharacter, "Reachable")),
                )
            ),
            viewer.openedModels,
        )
    }

    private class RecordingPhonebookViewer(
        override val accountId: UUID,
        override val permitted: Boolean,
    ) : PhonebookViewer {
        val messages = mutableListOf<PhonebookMessage>()
        val openedModels = mutableListOf<PhonebookBrowserModel>()

        override fun sendMessage(message: PhonebookMessage) {
            messages += message
        }

        override fun openInventory(model: PhonebookBrowserModel) {
            openedModels += model
        }
    }

    private class FakePhonebookDirectory(
        private val activeCharacters: Map<UUID, UUID>,
        characters: List<PhonebookCharacter> = emptyList(),
        private val onlineAccounts: Set<UUID> = emptySet(),
    ) : PhonebookDirectory {
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }
        var activeCharacterLookups = 0
            private set

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts

        override fun activeCharacter(accountId: UUID): UUID? {
            activeCharacterLookups++
            return activeCharacters[accountId]
        }
    }
}
