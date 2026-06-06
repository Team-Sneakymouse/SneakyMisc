package com.danidipp.sneakymisc.phonebook

import java.util.UUID

enum class PhonebookRemoveContactResult {
    Removed,
    AlreadyRemoved,
    StaleOwner,
    StaleBrowserAction,
}

enum class PhonebookPageActionResult {
    PageChanged,
    NoPage,
    StaleOwner,
    StaleBrowserAction,
}

class PhonebookGuiActions(
    private val phonebooks: PhonebookContactStore,
    private val activeCharacters: PhonebookActiveCharacters,
    private val directory: PhonebookDirectory,
    private val caller: PhonebookCaller,
    private val browser: PhonebookBrowser,
) {
    fun previousPage(viewer: PhonebookViewer, state: PhonebookBrowserState): PhonebookPageActionResult =
        previousPage(viewer, currentActionSelection(state, PhonebookBrowserAction.PreviousPage))

    fun nextPage(viewer: PhonebookViewer, state: PhonebookBrowserState): PhonebookPageActionResult =
        nextPage(viewer, currentActionSelection(state, PhonebookBrowserAction.NextPage))

    fun previousPage(viewer: PhonebookViewer, selection: PhonebookBrowserActionSelection): PhonebookPageActionResult =
        showSelectedPage(viewer, selection, selection.holderState.page - 1)

    fun nextPage(viewer: PhonebookViewer, selection: PhonebookBrowserActionSelection): PhonebookPageActionResult =
        showSelectedPage(viewer, selection, selection.holderState.page + 1)

    fun callContact(
        viewer: PhonebookViewer,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ): PhonebookContactClickResult =
        callContact(
            viewer,
            PhonebookBrowserContactSelection(
                holderState = state,
                itemRenderToken = state.renderToken,
                itemPage = state.page,
                itemSlot = PhonebookBrowserRenderer.CONTACT_SLOTS.first(),
                contactCharacterId = selectedContactCharacterId,
            ),
        )

    fun callContact(
        viewer: PhonebookViewer,
        selection: PhonebookBrowserContactSelection,
    ): PhonebookContactClickResult {
        if (!selection.isCurrentForHolder()) return PhonebookContactClickResult.StaleBrowserAction

        val state = selection.holderState
        val selectedContactCharacterId = selection.contactCharacterId
        val data = phonebooks.load()
        if (activeCharacters.activeCharacter(state.viewerAccountId) != state.ownerCharacterId) {
            viewer.sendMessage(PhonebookMessageCatalog.staleOwner())
            return PhonebookContactClickResult.StaleOwner
        }

        val contact = data.contactBetween(state.ownerCharacterId, selectedContactCharacterId)
        val targetAccountId = contact?.accountFor(selectedContactCharacterId)
        if (targetAccountId == null) {
            viewer.refreshInventory(browser.refresh(data, state))
            return PhonebookContactClickResult.MissingContact
        }

        if (!directory.isOnline(targetAccountId)) {
            sendTargetOffline(viewer, data, state, selectedContactCharacterId)
            viewer.refreshInventory(browser.refresh(data, state))
            return PhonebookContactClickResult.TargetOffline
        }

        val targetCharacter = directory.character(targetAccountId, selectedContactCharacterId)
        if (targetCharacter == null) {
            viewer.refreshInventory(browser.refresh(data, state))
            return PhonebookContactClickResult.MissingContact
        }

        caller.startOrReuseCall(state.viewerAccountId, targetAccountId, targetCharacter.displayName)
        viewer.closeInventory()
        return PhonebookContactClickResult.Called
    }

    fun removeContact(
        viewer: PhonebookViewer,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ): PhonebookRemoveContactResult =
        removeContact(
            viewer,
            PhonebookBrowserContactSelection(
                holderState = state,
                itemRenderToken = state.renderToken,
                itemPage = state.page,
                itemSlot = PhonebookBrowserRenderer.CONTACT_SLOTS.first(),
                contactCharacterId = selectedContactCharacterId,
            ),
        )

    fun removeContact(
        viewer: PhonebookViewer,
        selection: PhonebookBrowserContactSelection,
    ): PhonebookRemoveContactResult {
        if (!selection.isCurrentForHolder()) return PhonebookRemoveContactResult.StaleBrowserAction

        val state = selection.holderState
        val selectedContactCharacterId = selection.contactCharacterId
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
            viewer.refreshInventory(browser.refresh(removal.data, state))
            return PhonebookRemoveContactResult.AlreadyRemoved
        }

        viewer.sendMessage(
            PhonebookMessageCatalog.contactRemoved(removedContactName(removedContact, selectedContactCharacterId)),
        )
        viewer.refreshInventory(browser.refresh(removal.data, state))
        return PhonebookRemoveContactResult.Removed
    }

    private fun showSelectedPage(
        viewer: PhonebookViewer,
        selection: PhonebookBrowserActionSelection,
        page: Int,
    ): PhonebookPageActionResult {
        if (!selection.isCurrentForHolder()) return PhonebookPageActionResult.StaleBrowserAction

        val state = selection.holderState
        if (activeCharacters.activeCharacter(state.viewerAccountId) != state.ownerCharacterId) {
            viewer.sendMessage(PhonebookMessageCatalog.staleOwner())
            return PhonebookPageActionResult.StaleOwner
        }

        val data = phonebooks.load()
        val model = browser.page(data, state, page)
        if (model.state.page == state.page) return PhonebookPageActionResult.NoPage

        viewer.refreshInventory(model)
        return PhonebookPageActionResult.PageChanged
    }

    private fun currentActionSelection(
        state: PhonebookBrowserState,
        action: PhonebookBrowserAction,
    ): PhonebookBrowserActionSelection =
        PhonebookBrowserActionSelection(
            holderState = state,
            itemRenderToken = state.renderToken,
            itemPage = state.page,
            action = action,
        )

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
