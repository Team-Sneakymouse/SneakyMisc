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
