package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookBrowserState(
    val viewerAccountId: UUID,
    val ownerCharacterId: UUID,
    val page: Int,
    val renderToken: Long,
)

data class PhonebookBrowserContactSelection(
    val holderState: PhonebookBrowserState,
    val itemRenderToken: Long,
    val itemPage: Int,
    val itemSlot: Int,
    val contactCharacterId: UUID,
) {
    fun isCurrentForHolder(): Boolean =
        itemRenderToken == holderState.renderToken &&
            itemPage == holderState.page &&
            itemSlot in PhonebookBrowserRenderer.CONTACT_SLOTS
}

data class PhonebookBrowserActionSelection(
    val holderState: PhonebookBrowserState,
    val itemRenderToken: Long,
    val itemPage: Int,
    val action: PhonebookBrowserAction,
) {
    fun isCurrentForHolder(): Boolean =
        itemRenderToken == holderState.renderToken &&
            itemPage == holderState.page
}

enum class PhonebookContactClickResult {
    Called,
    TargetOffline,
    StaleOwner,
    MissingContact,
    StaleBrowserAction,
}

interface PhonebookCaller {
    fun startOrReuseCall(callerAccountId: UUID, targetAccountId: UUID, targetDisplayName: String)
}
