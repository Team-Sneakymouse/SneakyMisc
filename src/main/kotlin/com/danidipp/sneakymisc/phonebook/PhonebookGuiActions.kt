package com.danidipp.sneakymisc.phonebook

import java.util.UUID

enum class PhonebookRemoveContactResult {
    Removed,
    AlreadyRemoved,
    StaleOwner,
}

class PhonebookGuiActions(
    private val phonebooks: PhonebookContactStore,
    private val activeCharacters: PhonebookActiveCharacters,
    private val directory: PhonebookDirectory,
    private val callRouter: PhonebookCallRouter,
    private val browser: PhonebookBrowser,
) {
    fun callContact(
        viewer: PhonebookViewer,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ): PhonebookContactClickResult {
        val data = phonebooks.load()

        return when (val result = callRouter.leftClickContact(data, state, selectedContactCharacterId)) {
            PhonebookContactClickResult.Called -> {
                viewer.closeInventory()
                result
            }
            PhonebookContactClickResult.TargetOffline -> {
                sendTargetOffline(viewer, data, state, selectedContactCharacterId)
                viewer.openInventory(browser.refresh(data, state))
                result
            }
            PhonebookContactClickResult.StaleOwner -> {
                viewer.sendMessage(PhonebookMessageCatalog.staleOwner())
                result
            }
            PhonebookContactClickResult.MissingContact -> {
                viewer.openInventory(browser.refresh(data, state))
                result
            }
        }
    }

    fun removeContact(
        viewer: PhonebookViewer,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ): PhonebookRemoveContactResult {
        if (activeCharacters.activeCharacter(state.viewerAccountId) != state.ownerCharacterId) {
            viewer.sendMessage(PhonebookMessageCatalog.staleOwner())
            return PhonebookRemoveContactResult.StaleOwner
        }

        val removal = phonebooks.removeContact(state.ownerCharacterId, selectedContactCharacterId)
        val removedContact = removal.removedContact
        if (removedContact == null) {
            viewer.sendMessage(
                PhonebookMessageCatalog.contactAlreadyRemoved(ownerCharacterName(state)),
            )
            viewer.openInventory(browser.refresh(removal.data, state))
            return PhonebookRemoveContactResult.AlreadyRemoved
        }

        viewer.sendMessage(
            PhonebookMessageCatalog.contactRemoved(removedContactName(removedContact, selectedContactCharacterId)),
        )
        viewer.openInventory(browser.refresh(removal.data, state))
        return PhonebookRemoveContactResult.Removed
    }

    private fun sendTargetOffline(
        viewer: PhonebookViewer,
        data: PhonebookData,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ) {
        viewer.sendMessage(
            PhonebookMessageCatalog.targetOffline(contactName(data, state, selectedContactCharacterId)),
        )
    }

    private fun contactName(data: PhonebookData, state: PhonebookBrowserState, selectedContactCharacterId: UUID): String {
        val contact = data.contactBetween(state.ownerCharacterId, selectedContactCharacterId)
        val accountId = contact?.accountFor(selectedContactCharacterId)
        return accountId?.let { directory.character(it, selectedContactCharacterId)?.displayName }
            ?: selectedContactCharacterId.toString()
    }

    private fun removedContactName(contact: PhonebookContact, selectedContactCharacterId: UUID): String {
        val accountId = contact.accountFor(selectedContactCharacterId) ?: return selectedContactCharacterId.toString()
        return directory.character(accountId, selectedContactCharacterId)?.displayName
            ?: selectedContactCharacterId.toString()
    }

    private fun ownerCharacterName(state: PhonebookBrowserState): String =
        directory.character(state.viewerAccountId, state.ownerCharacterId)?.displayName
            ?: state.ownerCharacterId.toString()
}
