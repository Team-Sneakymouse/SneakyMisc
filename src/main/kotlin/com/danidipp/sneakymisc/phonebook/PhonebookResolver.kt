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

enum class PhonebookContactResolutionFailure {
    MissingContact,
    Unlisted,
    Offline,
    MissingCharacter,
}

sealed interface PhonebookContactResolution {
    val characterId: UUID
    val accountId: UUID?
    val character: PhonebookCharacter?
    val listed: Boolean
    val online: Boolean
}

data class ResolvedPhonebookContact(
    val contact: VisiblePhonebookContact,
    override val character: PhonebookCharacter,
) : PhonebookContactResolution {
    override val characterId: UUID = contact.characterId
    override val accountId: UUID = contact.accountId
    override val listed: Boolean = true
    override val online: Boolean = true
}

data class UnresolvedPhonebookContact(
    override val characterId: UUID,
    override val accountId: UUID?,
    override val character: PhonebookCharacter?,
    override val listed: Boolean,
    override val online: Boolean,
    val reason: PhonebookContactResolutionFailure,
) : PhonebookContactResolution

interface PhonebookDirectory {
    fun characters(accountId: UUID): List<PhonebookCharacter> = emptyList()
    fun character(accountId: UUID, characterId: UUID): PhonebookCharacter?
    fun isOnline(accountId: UUID): Boolean
}

interface PhonebookActiveCharacters {
    fun activeCharacter(accountId: UUID): UUID?
}

class PhonebookResolver(private val directory: PhonebookDirectory) {
    fun resolveContact(data: PhonebookData, ownerCharacterId: UUID, contactCharacterId: UUID): PhonebookContactResolution {
        val contact = data.contactBetween(ownerCharacterId, contactCharacterId)
            ?: return UnresolvedPhonebookContact(
                characterId = contactCharacterId,
                accountId = null,
                character = null,
                listed = false,
                online = false,
                reason = PhonebookContactResolutionFailure.MissingContact,
            )
        return resolveStoredContact(data, ownerCharacterId, contact)
            ?: UnresolvedPhonebookContact(
                characterId = contactCharacterId,
                accountId = null,
                character = null,
                listed = false,
                online = false,
                reason = PhonebookContactResolutionFailure.MissingContact,
            )
    }

    fun resolveStoredContact(data: PhonebookData, ownerCharacterId: UUID, contact: PhonebookContact): PhonebookContactResolution? {
        val contactCharacterId = contact.otherCharacter(ownerCharacterId) ?: return null
        val accountId = contact.accountFor(contactCharacterId)
            ?: return UnresolvedPhonebookContact(
                characterId = contactCharacterId,
                accountId = null,
                character = null,
                listed = contactCharacterId in data.listings,
                online = false,
                reason = PhonebookContactResolutionFailure.MissingContact,
            )
        val listed = contactCharacterId in data.listings
        val online = directory.isOnline(accountId)
        val character = directory.character(accountId, contactCharacterId)

        val failure = when {
            !listed -> PhonebookContactResolutionFailure.Unlisted
            !online -> PhonebookContactResolutionFailure.Offline
            character == null -> PhonebookContactResolutionFailure.MissingCharacter
            else -> null
        }
        if (failure != null) {
            return UnresolvedPhonebookContact(
                characterId = contactCharacterId,
                accountId = accountId,
                character = character,
                listed = listed,
                online = online,
                reason = failure,
            )
        }

        val resolvedCharacter = requireNotNull(character)
        return ResolvedPhonebookContact(
            VisiblePhonebookContact(
                accountId = resolvedCharacter.accountId,
                characterId = resolvedCharacter.characterId,
                displayName = resolvedCharacter.displayName,
                skin = resolvedCharacter.skin,
            ),
            character = resolvedCharacter,
        )
    }

    fun resolveContacts(data: PhonebookData, ownerCharacterId: UUID): List<PhonebookContactResolution> =
        data.contacts.values.mapNotNull { contact ->
            resolveStoredContact(data, ownerCharacterId, contact)
        }.sortedBy { it.characterId.toString() }

    fun visibleContacts(data: PhonebookData, ownerCharacterId: UUID): List<VisiblePhonebookContact> =
        resolveContacts(data, ownerCharacterId).mapNotNull { resolution ->
            (resolution as? ResolvedPhonebookContact)?.contact
        }.sortedWith(
            compareBy<VisiblePhonebookContact> { it.displayName.lowercase() }
                .thenBy { it.characterId.toString() }
        )
}
