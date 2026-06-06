package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookDebugInspectorTest {
    @Test
    fun `inspecting an account summarizes all Characters and surfaces hidden missing contacts`() {
        val account = uuid(1)
        val activeCharacter = uuid(100)
        val otherOwnedCharacter = uuid(101)
        val reachableAccount = uuid(2)
        val reachableCharacter = uuid(200)
        val unlistedAccount = uuid(3)
        val unlistedCharacter = uuid(300)
        val offlineAccount = uuid(4)
        val offlineCharacter = uuid(400)
        val missingAccount = uuid(5)
        val missingCharacter = uuid(500)
        val data = PhonebookData(
            listings = setOf(activeCharacter, reachableCharacter, offlineCharacter, missingCharacter),
            contacts = contacts(
                PhonebookContact.between(activeCharacter, account, reachableCharacter, reachableAccount),
                PhonebookContact.between(activeCharacter, account, unlistedCharacter, unlistedAccount),
                PhonebookContact.between(activeCharacter, account, offlineCharacter, offlineAccount),
                PhonebookContact.between(activeCharacter, account, missingCharacter, missingAccount),
            ),
        )
        val directory = FakePhonebookDirectory(
            characters = listOf(
                PhonebookCharacter(account, activeCharacter, "Active"),
                PhonebookCharacter(account, otherOwnedCharacter, "Other Owned"),
                PhonebookCharacter(reachableAccount, reachableCharacter, "Reachable"),
                PhonebookCharacter(unlistedAccount, unlistedCharacter, "Unlisted"),
                PhonebookCharacter(offlineAccount, offlineCharacter, "Offline"),
            ),
            onlineAccounts = setOf(reachableAccount, unlistedAccount, missingAccount),
        )

        val model = PhonebookDebugInspector(
            phonebooks = RecordingPhonebookStore(data),
            activeCharacters = FakePhonebookActiveCharacters(mapOf(account to activeCharacter)),
            directory = directory,
            exampleLimit = 2,
        ).inspect(account)

        assertEquals(account, model.accountId)
        assertEquals(activeCharacter, model.activeCharacterId)
        assertEquals(
            listOf(
                PhonebookDebugCharacterSummary(
                    character = PhonebookCharacter(account, activeCharacter, "Active"),
                    active = true,
                    listed = true,
                    storedContactCount = 4,
                    visibleContactCount = 1,
                    missingContactCount = 1,
                    contactExamples = PhonebookDebugExamplePage(
                        totalCount = 4,
                        limit = 2,
                        examples = listOf(
                            PhonebookDebugContactExample(
                                characterId = reachableCharacter,
                                accountId = reachableAccount,
                                displayName = "Reachable",
                                listed = true,
                                online = true,
                                visible = true,
                                missingCharacter = false,
                            ),
                            PhonebookDebugContactExample(
                                characterId = unlistedCharacter,
                                accountId = unlistedAccount,
                                displayName = "Unlisted",
                                listed = false,
                                online = true,
                                visible = false,
                                missingCharacter = false,
                            ),
                        ),
                    ),
                    missingContactExamples = PhonebookDebugExamplePage(
                        totalCount = 1,
                        limit = 2,
                        examples = listOf(
                            PhonebookDebugMissingContact(missingAccount, missingCharacter),
                        ),
                    ),
                ),
                PhonebookDebugCharacterSummary(
                    character = PhonebookCharacter(account, otherOwnedCharacter, "Other Owned"),
                    active = false,
                    listed = false,
                    storedContactCount = 0,
                    visibleContactCount = 0,
                    missingContactCount = 0,
                    contactExamples = PhonebookDebugExamplePage(totalCount = 0, limit = 2, examples = emptyList()),
                    missingContactExamples = PhonebookDebugExamplePage(totalCount = 0, limit = 2, examples = emptyList()),
                ),
            ),
            model.characters,
        )
    }

    @Test
    fun `inspector bounds malformed persisted contact examples in the debug model`() {
        val account = uuid(1)
        val first = PhonebookMalformedEntry("contacts.first", "first malformed")
        val second = PhonebookMalformedEntry("contacts.second", "second malformed")
        val third = PhonebookMalformedEntry("contacts.third", "third malformed")

        val model = PhonebookDebugInspector(
            phonebooks = DiagnosticStore(
                PhonebookPersistenceDiagnostics(
                    data = PhonebookData(),
                    malformedContacts = listOf(first, second, third),
                )
            ),
            activeCharacters = FakePhonebookActiveCharacters(emptyMap()),
            directory = FakePhonebookDirectory(characters = emptyList(), onlineAccounts = emptySet()),
            exampleLimit = 2,
        ).inspect(account)

        assertEquals(
            PhonebookDebugExamplePage(
                totalCount = 3,
                limit = 2,
                examples = listOf(first, second),
            ),
            model.malformedPersistedContacts,
        )
        assertEquals(1, model.malformedPersistedContacts.omittedCount)
    }

    private fun contacts(vararg contacts: PhonebookContact): Map<String, PhonebookContact> =
        contacts.associateBy { PhonebookContactKeys.forCharacters(it.lowerCharacterId, it.higherCharacterId) }

    private fun uuid(value: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private class FakePhonebookActiveCharacters(
        private val activeCharacters: Map<UUID, UUID>,
    ) : PhonebookActiveCharacters {
        override fun activeCharacter(accountId: UUID): UUID? = activeCharacters[accountId]
    }

    private class FakePhonebookDirectory(
        characters: List<PhonebookCharacter>,
        private val onlineAccounts: Set<UUID>,
    ) : PhonebookDirectory {
        private val charactersByAccount = characters.groupBy { it.accountId }
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }

        override fun characters(accountId: UUID): List<PhonebookCharacter> =
            charactersByAccount[accountId].orEmpty()

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts
    }

    private class DiagnosticStore(
        private val diagnostics: PhonebookPersistenceDiagnostics,
    ) : PhonebookDiagnosticStore {
        override fun load(): PhonebookData = diagnostics.data

        override fun diagnostics(): PhonebookPersistenceDiagnostics = diagnostics
    }
}
