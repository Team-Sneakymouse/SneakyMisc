package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookListingChangeRequest(
    val characterId: UUID,
    val mode: PhonebookListingMode,
)

class RecordingPhonebookStore(initialData: PhonebookData) :
    PhonebookListingStore,
    PhonebookContactStore,
    PhonebookExchangeStore {
    private var data = initialData
    val savedData = mutableListOf<PhonebookData>()
    val listingChangeRequests = mutableListOf<PhonebookListingChangeRequest>()
    var loadCount = 0
        private set

    override fun load(): PhonebookData {
        loadCount++
        return data
    }

    fun save(data: PhonebookData) {
        this.data = data
        savedData += data
    }

    override fun changeListing(characterId: UUID, mode: PhonebookListingMode): PhonebookListingChange {
        listingChangeRequests += PhonebookListingChangeRequest(characterId, mode)
        val shouldList = when (mode) {
            PhonebookListingMode.Toggle -> characterId !in data.listings
            PhonebookListingMode.Listed -> true
            PhonebookListingMode.Unlisted -> false
        }

        val updatedData = if (shouldList) {
            data.copy(listings = data.listings + characterId)
        } else {
            data.copy(listings = data.listings - characterId)
        }
        save(updatedData)
        return PhonebookListingChange(updatedData, listed = shouldList)
    }

    override fun removeContact(firstCharacterId: UUID, secondCharacterId: UUID): PhonebookContactRemoval {
        val key = PhonebookContactKeys.forCharacters(firstCharacterId, secondCharacterId)
        val removedContact = data.contacts[key] ?: return PhonebookContactRemoval(data, null)
        val updatedData = data.copy(contacts = data.contacts - key)
        save(updatedData)
        return PhonebookContactRemoval(updatedData, removedContact)
    }

    override fun acceptExchange(
        initiator: PhonebookCharacter,
        target: PhonebookCharacter,
    ): PhonebookExchangePersistenceResult {
        val key = PhonebookContactKeys.forCharacters(initiator.characterId, target.characterId)
        if (key in data.contacts) {
            return PhonebookExchangePersistenceResult.AlreadyExists(data)
        }

        val updatedData = data.copy(
            listings = data.listings + initiator.characterId + target.characterId,
            contacts = data.contacts + (key to PhonebookContact.between(
                initiator.characterId,
                initiator.accountId,
                target.characterId,
                target.accountId,
            )),
        )
        save(updatedData)
        return PhonebookExchangePersistenceResult.Created(updatedData)
    }
}
