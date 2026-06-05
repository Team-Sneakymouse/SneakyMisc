package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookCallRoutingTest {
    @Test
    fun `left clicking a contact rereads the relationship and calls even after unlisting`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val data = PhonebookData(
            listings = emptySet(),
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                    PhonebookContact(ownerCharacter, viewerAccount, targetCharacter, targetAccount)
            ),
        )
        val directory = FakePhonebookDirectory(
            characters = listOf(
                PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
            ),
            onlineAccounts = setOf(targetAccount),
        )
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val caller = RecordingPhonebookCaller()

        val result = PhonebookCallRouter(directory, activeCharacters, caller)
            .leftClickContact(data, PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10), targetCharacter)

        assertEquals(PhonebookContactClickResult.Called, result)
        assertEquals(
            listOf(PhonebookCall(viewerAccount, targetAccount, "Target Character")),
            caller.calls,
        )
    }

    @Test
    fun `left clicking an offline Phonebook Call Target fails without placing a call`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val directory = FakePhonebookDirectory(
            characters = listOf(PhonebookCharacter(targetAccount, targetCharacter, "Target Character")),
            onlineAccounts = emptySet(),
        )
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to ownerCharacter))
        val caller = RecordingPhonebookCaller()
        val data = PhonebookData(
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                    PhonebookContact(ownerCharacter, viewerAccount, targetCharacter, targetAccount)
            ),
        )

        val result = PhonebookCallRouter(directory, activeCharacters, caller)
            .leftClickContact(data, PhonebookBrowserState(viewerAccount, ownerCharacter, page = 0, renderToken = 10), targetCharacter)

        assertEquals(PhonebookContactClickResult.TargetOffline, result)
        assertEquals(emptyList(), caller.calls)
    }

    @Test
    fun `left clicking a stale owner fails before resolving a Phonebook Call Target`() {
        val viewerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val renderedOwnerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val activeOwnerCharacter = UUID.fromString("11111111-0000-0000-0000-000000000000")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val directory = FakePhonebookDirectory(
            characters = emptyList(),
            onlineAccounts = emptySet(),
        )
        val activeCharacters = FakePhonebookActiveCharacters(mapOf(viewerAccount to activeOwnerCharacter))
        val caller = RecordingPhonebookCaller()

        val result = PhonebookCallRouter(directory, activeCharacters, caller)
            .leftClickContact(PhonebookData(), PhonebookBrowserState(viewerAccount, renderedOwnerCharacter, page = 0, renderToken = 10), targetCharacter)

        assertEquals(PhonebookContactClickResult.StaleOwner, result)
        assertEquals(emptyList(), caller.calls)
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

    private class FakePhonebookDirectory(
        characters: List<PhonebookCharacter>,
        private val onlineAccounts: Set<UUID>,
    ) : PhonebookDirectory {
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts
    }

    private class FakePhonebookActiveCharacters(
        private val activeCharacters: Map<UUID, UUID>,
    ) : PhonebookActiveCharacters {
        override fun activeCharacter(accountId: UUID): UUID? = activeCharacters[accountId]
    }
}
