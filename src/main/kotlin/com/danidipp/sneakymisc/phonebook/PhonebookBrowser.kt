package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookBrowserContactItem(
    val slot: Int,
    val contact: VisiblePhonebookContact,
)

data class PhonebookBrowserModel(
    val state: PhonebookBrowserState,
    val visibleContacts: List<VisiblePhonebookContact>,
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val contactItems: List<PhonebookBrowserContactItem> =
        visibleContacts.mapIndexed { index, contact ->
            PhonebookBrowserContactItem(PhonebookBrowserRenderer.CONTACT_SLOTS[index], contact)
        },
)

interface PhonebookViewer {
    val accountId: UUID
    val permitted: Boolean
    fun sendMessage(message: PhonebookMessage)
    fun openInventory(model: PhonebookBrowserModel)
    fun refreshInventory(model: PhonebookBrowserModel) = openInventory(model)
    fun closeInventory() = Unit
}

enum class PhonebookOpenResult {
    Opened,
    NoPermission,
    NoActiveCharacter,
}

class PhonebookBrowserRenderer {
    fun render(state: PhonebookBrowserState, contacts: List<VisiblePhonebookContact>): PhonebookBrowserModel {
        val lastPage = ((contacts.size - 1).coerceAtLeast(0)) / CONTACTS_PER_PAGE
        val page = state.page.coerceIn(0, lastPage)
        val pageContacts = contacts
            .drop(page * CONTACTS_PER_PAGE)
            .take(CONTACTS_PER_PAGE)

        return PhonebookBrowserModel(
            state = state.copy(page = page),
            visibleContacts = pageContacts,
            hasPreviousPage = page > 0,
            hasNextPage = page < lastPage,
        )
    }

    companion object {
        const val INVENTORY_SIZE = 54
        const val CONTACTS_PER_PAGE = 42
        val CONTACT_SLOTS = (0 until INVENTORY_SIZE).filter { it % 9 in 1..7 }.take(CONTACTS_PER_PAGE)
        const val ADD_CONTACT_SLOT = 8
        const val PREVIOUS_PAGE_SLOT = 45
        const val NEXT_PAGE_SLOT = 53
    }
}

class PhonebookBrowser(
    private val resolver: PhonebookResolver,
    private val renderer: PhonebookBrowserRenderer,
    private val renderTokenProvider: () -> Long = System::nanoTime,
) {
    fun firstPage(data: PhonebookData, viewerAccountId: UUID, ownerCharacterId: UUID): PhonebookBrowserModel =
        render(
            data,PhonebookBrowserState(viewerAccountId, ownerCharacterId, page = 0, renderToken = renderTokenProvider()),
        )

    fun refresh(data: PhonebookData, state: PhonebookBrowserState): PhonebookBrowserModel =
        render(data, state.copy(renderToken = renderTokenProvider()))

    fun page(data: PhonebookData, state: PhonebookBrowserState, page: Int): PhonebookBrowserModel =
        render(data, state.copy(page = page, renderToken = renderTokenProvider()))

    private fun render(data: PhonebookData, state: PhonebookBrowserState): PhonebookBrowserModel {
        val visibleContacts = resolver.visibleContacts(data, state.ownerCharacterId)
        return renderer.render(state, visibleContacts)
    }
}
