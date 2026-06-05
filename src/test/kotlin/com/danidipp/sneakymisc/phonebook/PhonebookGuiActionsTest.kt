package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookGuiActionsTest {
    @Test
    fun `calling a visible contact closes the Phonebook and places a call`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val data = PhonebookData(
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                    PhonebookContact.between(ownerCharacter, viewerAccount, targetCharacter, targetAccount)
            ),
        )
        val phonebooks = RecordingPhonebookStore(data)
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val directory = FakePhonebookDirectory(
            characters = listOf(PhonebookCharacter(targetAccount, targetCharacter, "Target Character")),
            onlineAccounts = setOf(targetAccount),
        )
        val caller = RecordingPhonebookCaller()
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, directory, caller)
            .callContact(
                viewer,
                PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10),
                targetCharacter,
            )

        assertEquals(PhonebookContactClickResult.Called, result)
        assertEquals(listOf(PhonebookCall(viewerAccount, targetAccount, "Target Character")), caller.calls)
        assertEquals(1, viewer.closeCount)
        assertEquals(emptyList(), viewer.messages)
        assertEquals(emptyList(), viewer.openedModels)
    }

    @Test
    fun `calling an offline Phonebook Call Target sends keyed feedback and refreshes`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val data = PhonebookData(
            listings = setOf(targetCharacter),
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                    PhonebookContact.between(ownerCharacter, viewerAccount, targetCharacter, targetAccount)
            ),
        )
        val phonebooks = RecordingPhonebookStore(data)
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val directory = FakePhonebookDirectory(
            characters = listOf(PhonebookCharacter(targetAccount, targetCharacter, "Target Character")),
            onlineAccounts = emptySet(),
        )
        val caller = RecordingPhonebookCaller()
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, directory, caller)
            .callContact(
                viewer,
                PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10),
                targetCharacter,
            )

        assertEquals(PhonebookContactClickResult.TargetOffline, result)
        assertEquals(emptyList(), caller.calls)
        assertEquals(
            listOf(
                PhonebookMessage(
                    PhonebookMessageKeys.TARGET_OFFLINE,
                    mapOf("character" to "Target Character"),
                )
            ),
            viewer.messages,
        )
        assertEquals(
            listOf(
                PhonebookBrowserModel(
                    PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 99),
                    emptyList(),
                )
            ),
            viewer.openedModels,
        )
        assertEquals(0, viewer.closeCount)
    }

    @Test
    fun `calling a missing contact refreshes without feedback`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val selectedContactCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val directory = FakePhonebookDirectory()
        val caller = RecordingPhonebookCaller()
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, directory, caller)
            .callContact(
                viewer,
                PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10),
                selectedContactCharacter,
            )

        assertEquals(PhonebookContactClickResult.MissingContact, result)
        assertEquals(emptyList(), caller.calls)
        assertEquals(emptyList(), viewer.messages)
        assertEquals(
            listOf(
                PhonebookBrowserModel(
                    PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 99),
                    emptyList(),
                )
            ),
            viewer.openedModels,
        )
        assertEquals(0, viewer.closeCount)
    }

    @Test
    fun `calling rejects a stale Phonebook owner with keyed feedback and no refresh or call`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val renderedOwnerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val activeOwnerCharacter = UUID.fromString("11111111-0000-0000-0000-000000000000")
        val selectedContactCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to activeOwnerCharacter))
        val directory = FakePhonebookDirectory()
        val caller = RecordingPhonebookCaller()
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, directory, caller)
            .callContact(
                viewer,
                PhonebookBrowserState(viewerAccount, renderedOwnerCharacter, page = 0, renderToken = 10),
                selectedContactCharacter,
            )

        assertEquals(PhonebookContactClickResult.StaleOwner, result)
        assertEquals(emptyList(), caller.calls)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.STALE_OWNER)), viewer.messages)
        assertEquals(emptyList(), viewer.openedModels)
        assertEquals(0, viewer.closeCount)
    }

    @Test
    fun `removing a visible contact saves the relationship deletion sends local keyed feedback and refreshes`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val removedAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val removedCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val remainingAccount = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val remainingCharacter = UUID.fromString("30000000-0000-0000-0000-000000000000")
        val removedContact = PhonebookContact.between(ownerCharacter, viewerAccount, removedCharacter, removedAccount)
        val remainingContact = PhonebookContact.between(ownerCharacter, viewerAccount, remainingCharacter, remainingAccount)
        val initialData = PhonebookData(
            listings = setOf(ownerCharacter, removedCharacter, remainingCharacter),
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, removedCharacter) to removedContact,
                PhonebookContactKeys.forCharacters(ownerCharacter, remainingCharacter) to remainingContact,
            ),
        )
        val phonebooks = RecordingPhonebookStore(initialData)
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val directory = FakePhonebookDirectory(
            characters = listOf(
                PhonebookCharacter(removedAccount, removedCharacter, "Removed Character"),
                PhonebookCharacter(remainingAccount, remainingCharacter, "Remaining Character"),
            ),
            onlineAccounts = setOf(remainingAccount),
        )
        val viewer = RecordingPhonebookViewer(viewerAccount)
        val state = PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10)

        val result = guiActions(phonebooks, activeCharacters, directory)
            .removeContact(viewer, state, removedCharacter)

        assertEquals(PhonebookRemoveContactResult.Removed, result)
        assertEquals(initialData.listings, phonebooks.savedData.single().listings)
        assertEquals(
            mapOf(PhonebookContactKeys.forCharacters(ownerCharacter, remainingCharacter) to remainingContact),
            phonebooks.savedData.single().contacts,
        )
        assertEquals(
            listOf(
                PhonebookMessage(
                    PhonebookMessageKeys.CONTACT_REMOVED,
                    mapOf("character" to "Removed Character"),
                )
            ),
            viewer.messages,
        )
        assertEquals(
            listOf(
                PhonebookBrowserModel(
                    PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 99),
                    listOf(VisiblePhonebookContact(remainingAccount, remainingCharacter, "Remaining Character")),
                )
            ),
            viewer.openedModels,
        )
    }

    @Test
    fun `removing a contact rejects a stale Phonebook owner before mutating storage`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val renderedOwnerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val activeOwnerCharacter = UUID.fromString("11111111-0000-0000-0000-000000000000")
        val selectedContactCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to activeOwnerCharacter))
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, FakePhonebookDirectory())
            .removeContact(
                viewer,
                PhonebookBrowserState(viewerAccount, renderedOwnerCharacter, page = 0, renderToken = 10),
                selectedContactCharacter,
            )

        assertEquals(PhonebookRemoveContactResult.StaleOwner, result)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.STALE_OWNER)), viewer.messages)
        assertEquals(emptyList(), phonebooks.savedData)
        assertEquals(emptyList(), viewer.openedModels)
    }

    @Test
    fun `removing an already missing contact names the affected Phonebook owner Character and refreshes`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val selectedContactCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val phonebooks = RecordingPhonebookStore(PhonebookData(listings = setOf(ownerCharacter)))
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val directory = FakePhonebookDirectory(
            characters = listOf(PhonebookCharacter(viewerAccount, ownerCharacter, "Owner Character")),
        )
        val viewer = RecordingPhonebookViewer(viewerAccount)

        val result = guiActions(phonebooks, activeCharacters, directory)
            .removeContact(
                viewer,
                PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10),
                selectedContactCharacter,
            )

        assertEquals(PhonebookRemoveContactResult.AlreadyRemoved, result)
        assertEquals(
            listOf(
                PhonebookMessage(
                    PhonebookMessageKeys.CONTACT_ALREADY_REMOVED,
                    mapOf("character" to "Owner Character"),
                )
            ),
            viewer.messages,
        )
        assertEquals(emptyList(), phonebooks.savedData)
        assertEquals(
            listOf(
                PhonebookBrowserModel(
                    PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 99),
                    emptyList(),
                )
            ),
            viewer.openedModels,
        )
    }

    private fun guiActions(
        phonebooks: PhonebookContactStore,
        activeCharacters: PhonebookActiveCharacters,
        directory: PhonebookDirectory,
        caller: PhonebookCaller = RecordingPhonebookCaller(),
    ): PhonebookGuiActions =
        PhonebookGuiActions(
            phonebooks = phonebooks,
            activeCharacters = activeCharacters,
            directory = directory,
            caller = caller,
            browser = PhonebookBrowser(
                resolver = PhonebookResolver(directory),
                renderer = PhonebookBrowserRenderer(),
                renderTokenProvider = { 99L },
            ),
        )

    private class RecordingPhonebookViewer(
        override val accountId: UUID,
    ) : PhonebookViewer {
        override val permitted: Boolean = true
        val messages = mutableListOf<PhonebookMessage>()
        val openedModels = mutableListOf<PhonebookBrowserModel>()
        var closeCount = 0
            private set

        override fun sendMessage(message: PhonebookMessage) {
            messages += message
        }

        override fun openInventory(model: PhonebookBrowserModel) {
            openedModels += model
        }

        override fun closeInventory() {
            closeCount++
        }
    }

    private class RecordingPhonebookCaller : PhonebookCaller {
        val calls = mutableListOf<PhonebookCall>()

        override fun startOrReuseCall(callerAccountId: UUID, targetAccountId: UUID, targetDisplayName: String) {
            calls += PhonebookCall(callerAccountId, targetAccountId, targetDisplayName)
        }
    }

    private data class PhonebookCall(
        val callerAccountId: UUID,
        val targetAccountId: UUID,
        val targetDisplayName: String,
    )

    private class FakePhonebookActiveCharacters(
        private val activeCharacters: Map<UUID, UUID>,
    ) : PhonebookActiveCharacters {
        override fun activeCharacter(accountId: UUID): UUID? = activeCharacters[accountId]
    }

    private class FakePhonebookDirectory(
        characters: List<PhonebookCharacter> = emptyList(),
        private val onlineAccounts: Set<UUID> = emptySet(),
    ) : PhonebookDirectory {
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts
    }
}
