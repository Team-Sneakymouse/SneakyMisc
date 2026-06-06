package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import net.kyori.adventure.text.Component

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
    private val resolver = PhonebookResolver(directory)

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
            viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.stale_owner"))
            return PhonebookContactClickResult.StaleOwner
        }

        when (val resolution = resolver.resolveContact(data, state.ownerCharacterId, selectedContactCharacterId)) {
            is ResolvedPhonebookContact -> {
                caller.startOrReuseCall(
                    state.viewerAccountId,
                    resolution.contact.accountId,
                    resolution.contact.displayName,
                )
                viewer.closeInventory()
                return PhonebookContactClickResult.Called
            }
            is UnresolvedPhonebookContact -> {
                if (resolution.reason == PhonebookContactResolutionFailure.Offline) {
                    sendTargetOffline(viewer, resolution)
                    viewer.refreshInventory(browser.refresh(data, state))
                    return PhonebookContactClickResult.TargetOffline
                }
                viewer.refreshInventory(browser.refresh(data, state))
                return PhonebookContactClickResult.MissingContact
            }
        }
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
            viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.stale_owner"))
            return PhonebookRemoveContactResult.StaleOwner
        }

        val removal = phonebooks.removeContact(state.ownerCharacterId, selectedContactCharacterId)
        val removedContact = removal.removedContact
        if (removedContact == null) {
            viewer.sendMessage(
                PhonebookMessage(
                    "sneakymisc.phonebook.contact_already_removed",
                    mapOf("character" to Component.text(ownerCharacterName(state))),
                ),
            )
            viewer.refreshInventory(browser.refresh(removal.data, state))
            return PhonebookRemoveContactResult.AlreadyRemoved
        }

        viewer.sendMessage(
            PhonebookMessage(
                "sneakymisc.phonebook.contact_removed",
                mapOf("character" to Component.text(removedContactName(removedContact, selectedContactCharacterId))),
            ),
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
            viewer.sendMessage(PhonebookMessage("sneakymisc.phonebook.stale_owner"))
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

    private fun sendTargetOffline(viewer: PhonebookViewer, resolution: UnresolvedPhonebookContact) {
        viewer.sendMessage(
            PhonebookMessage(
                "sneakymisc.phonebook.target_offline",
                mapOf("character" to Component.text(contactName(resolution))),
            ),
        )
    }

    private fun contactName(resolution: PhonebookContactResolution): String =
        resolution.character?.displayName ?: resolution.characterId.toString()

    private fun removedContactName(contact: PhonebookContact, selectedContactCharacterId: UUID): String {
        val accountId = contact.accountFor(selectedContactCharacterId) ?: return selectedContactCharacterId.toString()
        return directory.character(accountId, selectedContactCharacterId)?.displayName
            ?: selectedContactCharacterId.toString()
    }

    private fun ownerCharacterName(state: PhonebookBrowserState): String =
        directory.character(state.viewerAccountId, state.ownerCharacterId)?.displayName
            ?: state.ownerCharacterId.toString()
}
