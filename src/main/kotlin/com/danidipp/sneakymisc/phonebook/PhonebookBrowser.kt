package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookMessage(
    val key: String,
    val arguments: Map<String, String> = emptyMap(),
)

object PhonebookMessageKeys {
    const val NO_ACTIVE_CHARACTER = "sneakymisc.phonebook.no_active_character"
    const val TARGET_OFFLINE = "sneakymisc.phonebook.target_offline"
    const val STALE_OWNER = "sneakymisc.phonebook.stale_owner"
    const val PLAYER_ONLY = "sneakymisc.phonebook.player_only"
    const val LISTED = "sneakymisc.phonebook.listed"
    const val UNLISTED = "sneakymisc.phonebook.unlisted"
    const val TITLE = "sneakymisc.phonebook.title"

    fun argumentNames(key: String): List<String> = when (key) {
        TARGET_OFFLINE -> listOf("character")
        else -> emptyList()
    }
}

data class PhonebookBrowserModel(
    val state: PhonebookBrowserState,
    val visibleContacts: List<VisiblePhonebookContact>,
)

interface PhonebookViewer {
    val accountId: UUID
    val permitted: Boolean
    fun sendMessage(message: PhonebookMessage)
    fun openInventory(model: PhonebookBrowserModel)
}

enum class PhonebookOpenResult {
    Opened,
    NoPermission,
    NoActiveCharacter,
}

class PhonebookBrowserRenderer {
    fun render(state: PhonebookBrowserState, contacts: List<VisiblePhonebookContact>): PhonebookBrowserModel =
        PhonebookBrowserModel(state, contacts.take(CONTACTS_PER_PAGE))

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

    private fun render(data: PhonebookData, state: PhonebookBrowserState): PhonebookBrowserModel {
        val visibleContacts = resolver.visibleContacts(data, state.ownerCharacterId)
        return renderer.render(state, visibleContacts)
    }
}
