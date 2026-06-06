package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookCharacter(
    val accountId: UUID,
    val characterId: UUID,
    val displayName: String,
    val skin: PhonebookCharacterSkin? = null,
)

data class PhonebookCharacterSkin(
    val skin: String? = null,
    val texture: String? = null,
    val signature: String? = null,
    val slim: Boolean = false,
)

data class VisiblePhonebookContact(
    val accountId: UUID,
    val characterId: UUID,
    val displayName: String,
    val skin: PhonebookCharacterSkin? = null,
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
            VisiblePhonebookContact(character.accountId, character.characterId, character.displayName, character.skin)
        }.sortedWith(
            compareBy<VisiblePhonebookContact> { it.displayName.lowercase() }
                .thenBy { it.characterId.toString() }
        )
}
