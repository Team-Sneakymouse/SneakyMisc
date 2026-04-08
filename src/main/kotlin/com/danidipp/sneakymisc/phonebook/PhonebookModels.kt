package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookEntry(
    val characterUuid: String,
)

data class PhonebookRecord(
    val ownerPlayerUuid: String,
    val contacts: MutableList<PhonebookEntry> = mutableListOf(),
)

data class RenderedContact(
    val characterUuid: String,
    val characterName: String,
    val ownerPlayerUuid: String,
    val ownerPlayerName: String,
)

data class PendingAddMode(
    val requesterPlayerUuid: UUID,
    val expiresAtMillis: Long,
)

data class PendingExchangeRequest(
    val requestId: String,
    val requesterPlayerUuid: UUID,
    val requesterCharacterUuid: String,
    val requesterCharacterName: String,
    val targetPlayerUuid: UUID,
    val targetCharacterUuid: String,
    val targetCharacterName: String,
    val expiresAtMillis: Long,
)

data class CharacterIndexEntry(
    val ownerPlayerUuid: UUID,
    val characterName: String,
)

sealed interface CreateExchangeRequestResult {
    data class Created(val request: PendingExchangeRequest) : CreateExchangeRequestResult
    data object RequesterBusy : CreateExchangeRequestResult
    data object TargetBusy : CreateExchangeRequestResult
}

sealed interface ExchangeResponseResult {
    data object Accepted : ExchangeResponseResult
    data object Declined : ExchangeResponseResult
    data object Invalid : ExchangeResponseResult
    data object Missing : ExchangeResponseResult
}
