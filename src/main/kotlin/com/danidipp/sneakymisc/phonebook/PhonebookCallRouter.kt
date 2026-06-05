package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookBrowserState(
    val viewerAccountId: UUID,
    val ownerCharacterId: UUID,
    val page: Int,
    val renderToken: Long,
)

enum class PhonebookContactClickResult {
    Called,
    TargetOffline,
    StaleOwner,
    MissingContact,
}

interface PhonebookCaller {
    fun startOrReuseCall(callerAccountId: UUID, targetAccountId: UUID, targetDisplayName: String)
}

class PhonebookCallRouter(
    private val directory: PhonebookDirectory,
    private val caller: PhonebookCaller,
) {
    fun leftClickContact(
        data: PhonebookData,
        state: PhonebookBrowserState,
        selectedContactCharacterId: UUID,
    ): PhonebookContactClickResult {
        if (directory.activeCharacter(state.viewerAccountId) != state.ownerCharacterId)
            return PhonebookContactClickResult.StaleOwner

        val contact = data.contactBetween(state.ownerCharacterId, selectedContactCharacterId)
            ?: return PhonebookContactClickResult.MissingContact
        val targetAccountId = contact.accountFor(selectedContactCharacterId)
            ?: return PhonebookContactClickResult.MissingContact

        if (!directory.isOnline(targetAccountId)) return PhonebookContactClickResult.TargetOffline

        val targetCharacter = directory.character(targetAccountId, selectedContactCharacterId)
            ?: return PhonebookContactClickResult.MissingContact

        caller.startOrReuseCall(state.viewerAccountId, targetAccountId, targetCharacter.displayName)
        return PhonebookContactClickResult.Called
    }
}
