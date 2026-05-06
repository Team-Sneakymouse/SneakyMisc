package com.danidipp.sneakymisc.phonebook

import java.util.UUID

@JvmInline
value class PhonebookContactId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Phonebook contact id must not be blank" }
    }
}

data class PhonebookEntry(
    val contactId: PhonebookContactId,
) {
    val characterUuid: String
        get() = contactId.value
}

fun PhonebookEntry(
    characterUuid: String,
): PhonebookEntry = PhonebookEntry(
    PhonebookContactId(characterUuid),
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

sealed interface TargetContactExchangeResult {
    data object NotTargeting : TargetContactExchangeResult
    data object SelfTarget : TargetContactExchangeResult
    data object RequesterMissingCharacter : TargetContactExchangeResult
    data object TargetMissingCharacter : TargetContactExchangeResult
    data class AlreadyContact(val targetCharacterName: String) : TargetContactExchangeResult
    data class Created(val request: PendingExchangeRequest) : TargetContactExchangeResult
    data object RequesterBusy : TargetContactExchangeResult
    data object TargetBusy : TargetContactExchangeResult
}

sealed interface ContactExchangeResponseResult {
    data object Missing : ContactExchangeResponseResult
    data object RequesterOffline : ContactExchangeResponseResult
    data class CharacterChanged(val request: PendingExchangeRequest) : ContactExchangeResponseResult
    data class Declined(
        val request: PendingExchangeRequest,
        val targetCharacterName: String,
    ) : ContactExchangeResponseResult
    data class Accepted(
        val request: PendingExchangeRequest,
        val requesterCharacterName: String,
        val targetCharacterName: String,
    ) : ContactExchangeResponseResult
    data class Failed(
        val request: PendingExchangeRequest,
        val message: String,
    ) : ContactExchangeResponseResult
}

data class ContactExchangeCancellation(
    val request: PendingExchangeRequest,
    val playerWasRequester: Boolean,
)
