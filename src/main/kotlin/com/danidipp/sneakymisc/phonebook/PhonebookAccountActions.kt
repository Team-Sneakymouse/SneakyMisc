package com.danidipp.sneakymisc.phonebook

import java.util.UUID

interface PhonebookDataStore {
    fun load(): PhonebookData
    fun save(data: PhonebookData)
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

class PhonebookAccountActions(
    private val phonebooks: PhonebookDataStore,
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
            val data = phonebooks.load()
            val shouldList = when (mode) {
                PhonebookListingMode.Toggle -> characterId !in data.listings
                PhonebookListingMode.Listed -> true
                PhonebookListingMode.Unlisted -> false
            }

            if (shouldList) {
                phonebooks.save(data.copy(listings = data.listings + characterId))
                viewer.sendMessage(PhonebookMessage(PhonebookMessageKeys.LISTED))
                PhonebookListingResult.Listed
            } else {
                phonebooks.save(data.copy(listings = data.listings - characterId))
                viewer.sendMessage(PhonebookMessage(PhonebookMessageKeys.UNLISTED))
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
                viewer.sendMessage(PhonebookMessage(PhonebookMessageKeys.NO_ACTIVE_CHARACTER))
            }

        return action(characterId)
    }
}
