package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookAccountActionsListingTest {
    @Test
    fun `toggling flips an unlisted active Character to listed and sends keyed feedback`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val characterId = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(accountId to characterId))
        val repository = RecordingPhonebookListingStore(PhonebookData())

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Toggle)

        assertEquals(PhonebookListingResult.Listed, result)
        assertEquals(listOf(PhonebookListingChangeRequest(characterId, PhonebookListingMode.Toggle)), repository.changeRequests)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.LISTED)), viewer.messages)
    }

    @Test
    fun `toggling flips a listed active Character to unlisted and sends keyed feedback`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val characterId = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(accountId to characterId))
        val repository = RecordingPhonebookListingStore(PhonebookData(listings = setOf(characterId)))

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Toggle)

        assertEquals(PhonebookListingResult.Unlisted, result)
        assertEquals(listOf(PhonebookListingChangeRequest(characterId, PhonebookListingMode.Toggle)), repository.changeRequests)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.UNLISTED)), viewer.messages)
    }

    @Test
    fun `explicit listed mode lists an unlisted active Character`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val characterId = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(accountId to characterId))
        val repository = RecordingPhonebookListingStore(PhonebookData())

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Listed)

        assertEquals(PhonebookListingResult.Listed, result)
        assertEquals(listOf(PhonebookListingChangeRequest(characterId, PhonebookListingMode.Listed)), repository.changeRequests)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.LISTED)), viewer.messages)
    }

    @Test
    fun `explicit unlisted mode unlists a listed active Character without changing contacts`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val characterId = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val otherCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val otherAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val contact = PhonebookContact.between(characterId, accountId, otherCharacter, otherAccount)
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(accountId to characterId))
        val repository = RecordingPhonebookListingStore(
            PhonebookData(
                listings = setOf(characterId, otherCharacter),
                contacts = mapOf(PhonebookContactKeys.forCharacters(characterId, otherCharacter) to contact),
            )
        )

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Unlisted)

        assertEquals(PhonebookListingResult.Unlisted, result)
        assertEquals(listOf(PhonebookListingChangeRequest(characterId, PhonebookListingMode.Unlisted)), repository.changeRequests)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.UNLISTED)), viewer.messages)
    }

    @Test
    fun `listing changes stop at the permission gate without resolving active Character`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = false)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = emptyMap())
        val repository = RecordingPhonebookListingStore(PhonebookData())

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Toggle)

        assertEquals(PhonebookListingResult.NoPermission, result)
        assertEquals(0, activeCharacters.lookups)
        assertEquals(emptyList(), repository.changeRequests)
        assertEquals(emptyList(), viewer.messages)
    }

    @Test
    fun `listing changes fail with a translation key when account has no active Character`() {
        val accountId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val viewer = RecordingPhonebookViewer(accountId = accountId, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = emptyMap())
        val repository = RecordingPhonebookListingStore(PhonebookData())

        val result = accountActions(repository, activeCharacters).changeListing(viewer, PhonebookListingMode.Toggle)

        assertEquals(PhonebookListingResult.NoActiveCharacter, result)
        assertEquals(emptyList(), repository.changeRequests)
        assertEquals(listOf(PhonebookMessage(PhonebookMessageKeys.NO_ACTIVE_CHARACTER)), viewer.messages)
    }

    @Test
    fun `listing changes affect future visible contact resolution`() {
        val ownerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val targetAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val viewer = RecordingPhonebookViewer(accountId = targetAccount, permitted = true)
        val activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(targetAccount to targetCharacter))
        val directory = FakePhonebookDirectory(
            characters = listOf(PhonebookCharacter(targetAccount, targetCharacter, "Target")),
            onlineAccounts = setOf(targetAccount),
        )
        val repository = RecordingPhonebookListingStore(
            PhonebookData(
                contacts = mapOf(
                    PhonebookContactKeys.forCharacters(ownerCharacter, targetCharacter) to
                        PhonebookContact.between(ownerCharacter, ownerAccount, targetCharacter, targetAccount)
                ),
            )
        )

        accountActions(repository, activeCharacters, directory).changeListing(viewer, PhonebookListingMode.Listed)
        val visible = PhonebookResolver(directory).visibleContacts(repository.load(), ownerCharacter)

        assertEquals(listOf(VisiblePhonebookContact(targetAccount, targetCharacter, "Target")), visible)
    }

    private class RecordingPhonebookViewer(
        override val accountId: UUID,
        override val permitted: Boolean,
    ) : PhonebookViewer {
        val messages = mutableListOf<PhonebookMessage>()

        override fun sendMessage(message: PhonebookMessage) {
            messages += message
        }

        override fun openInventory(model: PhonebookBrowserModel) = Unit
    }

    private fun accountActions(
        phonebooks: PhonebookListingStore,
        activeCharacters: PhonebookActiveCharacters,
        directory: PhonebookDirectory = FakePhonebookDirectory(),
    ): PhonebookAccountActions =
        PhonebookAccountActions(
            phonebooks = phonebooks,
            activeCharacters = activeCharacters,
            browser = PhonebookBrowser(
                resolver = PhonebookResolver(directory),
                renderer = PhonebookBrowserRenderer(),
                renderTokenProvider = { 1L },
            ),
        )

    private data class PhonebookListingChangeRequest(
        val characterId: UUID,
        val mode: PhonebookListingMode,
    )

    private class RecordingPhonebookListingStore(initialData: PhonebookData) : PhonebookListingStore {
        private var data = initialData
        val changeRequests = mutableListOf<PhonebookListingChangeRequest>()

        override fun load(): PhonebookData = data

        override fun changeListing(characterId: UUID, mode: PhonebookListingMode): PhonebookListingChange {
            changeRequests += PhonebookListingChangeRequest(characterId, mode)
            val shouldList = when (mode) {
                PhonebookListingMode.Toggle -> characterId !in data.listings
                PhonebookListingMode.Listed -> true
                PhonebookListingMode.Unlisted -> false
            }
            data = if (shouldList) {
                data.copy(listings = data.listings + characterId)
            } else {
                data.copy(listings = data.listings - characterId)
            }
            return PhonebookListingChange(data, listed = shouldList)
        }
    }

    private class FakePhonebookActiveCharacters(
        private val activeCharacters: Map<UUID, UUID>,
    ) : PhonebookActiveCharacters {
        var lookups = 0
            private set

        override fun activeCharacter(accountId: UUID): UUID? {
            lookups++
            return activeCharacters[accountId]
        }
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
