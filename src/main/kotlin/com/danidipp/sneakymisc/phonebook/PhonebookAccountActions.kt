package com.danidipp.sneakymisc.phonebook

import java.util.UUID

interface PhonebookDataStore {
    fun load(): PhonebookData
}

data class PhonebookMalformedEntry(
    val path: String,
    val message: String,
)

data class PhonebookPersistenceDiagnostics(
    val data: PhonebookData,
    val malformedListings: List<PhonebookMalformedEntry> = emptyList(),
    val malformedContacts: List<PhonebookMalformedEntry> = emptyList(),
)

interface PhonebookDiagnosticStore : PhonebookDataStore {
    fun diagnostics(): PhonebookPersistenceDiagnostics
}

data class PhonebookContactRemoval(
    val data: PhonebookData,
    val removedContact: PhonebookContact?,
)

interface PhonebookContactStore : PhonebookDataStore {
    fun removeContact(firstCharacterId: UUID, secondCharacterId: UUID): PhonebookContactRemoval
}

enum class PhonebookListingMode {
    Toggle,
    Listed,
    Unlisted,
}

enum class PhonebookListingResult {
    Listed,
    Unlisted,
    NoPermission,
    NoActiveCharacter,
}

data class PhonebookListingChange(
    val data: PhonebookData,
    val listed: Boolean,
)

interface PhonebookListingStore : PhonebookDataStore {
    fun changeListing(characterId: UUID, mode: PhonebookListingMode): PhonebookListingChange
}

class PhonebookAccountActions(
    private val phonebooks: PhonebookListingStore,
    private val activeCharacters: PhonebookActiveCharacters,
    private val browser: PhonebookBrowser,
) {
    fun openPhonebook(viewer: PhonebookViewer): PhonebookOpenResult =
        withActiveCharacter(
            viewer = viewer,
            noPermission = PhonebookOpenResult.NoPermission,
            noActiveCharacter = PhonebookOpenResult.NoActiveCharacter,
        ) { ownerCharacterId ->
            val data = phonebooks.load()
            viewer.openInventory(browser.firstPage(data, viewer.accountId, ownerCharacterId))
            PhonebookOpenResult.Opened
        }

    fun changeListing(viewer: PhonebookViewer, mode: PhonebookListingMode): PhonebookListingResult =
        withActiveCharacter(
            viewer = viewer,
            noPermission = PhonebookListingResult.NoPermission,
            noActiveCharacter = PhonebookListingResult.NoActiveCharacter,
        ) { characterId ->
            val change = phonebooks.changeListing(characterId, mode)
            if (change.listed) {
                viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.listed"))
                PhonebookListingResult.Listed
            } else {
                viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.unlisted"))
                PhonebookListingResult.Unlisted
            }
        }

    private inline fun <T> withActiveCharacter(
        viewer: PhonebookViewer,
        noPermission: T,
        noActiveCharacter: T,
        action: (UUID) -> T,
    ): T {
        if (!viewer.permitted) return noPermission

        val characterId = activeCharacters.activeCharacter(viewer.accountId)
            ?: return noActiveCharacter.also {
                viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.no_active_character"))
            }

        return action(characterId)
    }
}
