package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookCharacter(
    val accountId: UUID,
    val characterId: UUID,
    val displayName: String,
)

data class VisiblePhonebookContact(
    val accountId: UUID,
    val characterId: UUID,
    val displayName: String,
)

interface PhonebookDirectory {
    fun character(accountId: UUID, characterId: UUID): PhonebookCharacter?
    fun isOnline(accountId: UUID): Boolean
}

interface PhonebookActiveCharacters {
    fun activeCharacter(accountId: UUID): UUID?
}

class PhonebookResolver(private val directory: PhonebookDirectory) {
    fun visibleContacts(data: PhonebookData, ownerCharacterId: UUID): List<VisiblePhonebookContact> =
        data.contacts.values.mapNotNull { contact ->
            val contactCharacterId = contact.otherCharacter(ownerCharacterId) ?: return@mapNotNull null
            if (contactCharacterId !in data.listings) return@mapNotNull null

            val accountId = contact.accountFor(contactCharacterId) ?: return@mapNotNull null
            if (!directory.isOnline(accountId)) return@mapNotNull null

            val character = directory.character(accountId, contactCharacterId) ?: return@mapNotNull null
            VisiblePhonebookContact(character.accountId, character.characterId, character.displayName)
        }.sortedWith(
            compareBy<VisiblePhonebookContact> { it.displayName.lowercase() }
                .thenBy { it.characterId.toString() }
        )
}
