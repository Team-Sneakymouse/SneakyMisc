package com.danidipp.sneakymisc.phonebook

import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createDirectories
import org.bukkit.configuration.file.YamlConfiguration

data class PhonebookContact(
    val lowerCharacterId: UUID,
    val lowerAccountId: UUID,
    val higherCharacterId: UUID,
    val higherAccountId: UUID,
) {
    companion object {
        fun between(firstCharacterId: UUID, firstAccountId: UUID, secondCharacterId: UUID, secondAccountId: UUID): PhonebookContact {
            val first = firstCharacterId.toString().lowercase()
            val second = secondCharacterId.toString().lowercase()
            return if (first <= second)
                PhonebookContact(firstCharacterId, firstAccountId, secondCharacterId, secondAccountId)
            else
                PhonebookContact(secondCharacterId, secondAccountId, firstCharacterId, firstAccountId)
        }
    }

    fun accountFor(characterId: UUID): UUID? = when (characterId) {
        lowerCharacterId -> lowerAccountId
        higherCharacterId -> higherAccountId
        else -> null
    }

    fun otherCharacter(characterId: UUID): UUID? = when (characterId) {
        lowerCharacterId -> higherCharacterId
        higherCharacterId -> lowerCharacterId
        else -> null
    }
}

data class PhonebookData(val listings: Set<UUID> = emptySet(), val contacts: Map<String, PhonebookContact> = emptyMap()) {
    fun contactBetween(firstCharacterId: UUID, secondCharacterId: UUID): PhonebookContact? =
        contacts[PhonebookContactKeys.forCharacters(firstCharacterId, secondCharacterId)]
}

class PhonebookStorage(private val configPath: Path, private val logger: Logger) :
    PhonebookContactStore,
    PhonebookExchangeStore,
    PhonebookListingStore {
    fun addContact(firstCharacterId: UUID, firstAccountId: UUID, secondCharacterId: UUID, secondAccountId: UUID): Boolean {
        val data = load()
        val key = PhonebookContactKeys.forCharacters(firstCharacterId, secondCharacterId)
        if (key in data.contacts) return false

        val contact = PhonebookContact.between(firstCharacterId, firstAccountId, secondCharacterId, secondAccountId)
        save(data.copy(contacts = data.contacts + (key to contact)))
        return true
    }

    override fun removeContact(firstCharacterId: UUID, secondCharacterId: UUID): PhonebookContactRemoval {
        val data = load()
        val key = PhonebookContactKeys.forCharacters(firstCharacterId, secondCharacterId)
        val removedContact = data.contacts[key] ?: return PhonebookContactRemoval(data, removedContact = null)
        val updatedData = data.copy(contacts = data.contacts - key)
        save(updatedData)
        return PhonebookContactRemoval(updatedData, removedContact)
    }

    override fun changeListing(characterId: UUID, mode: PhonebookListingMode): PhonebookListingChange {
        val data = load()
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

    override fun acceptExchange(initiator: PhonebookCharacter, target: PhonebookCharacter): PhonebookExchangePersistenceResult {
        val data = load()
        val key = PhonebookContactKeys.forCharacters(initiator.characterId, target.characterId)
        if (key in data.contacts) return PhonebookExchangePersistenceResult.AlreadyExists(data)

        val contact = PhonebookContact.between(
            initiator.characterId,
            initiator.accountId,
            target.characterId,
            target.accountId,
        )
        val updatedData = data.copy(
            listings = data.listings + initiator.characterId + target.characterId,
            contacts = data.contacts + (key to contact),
        )
        save(updatedData)
        return PhonebookExchangePersistenceResult.Created(updatedData)
    }

    override fun load(): PhonebookData {
        if (!configPath.toFile().exists()) return PhonebookData()

        val yaml = YamlConfiguration.loadConfiguration(configPath.toFile())
        val listings = linkedSetOf<UUID>()
        for ((index, value) in yaml.getStringList("listings").withIndex()) {
            parseUuid(value, "Phonebook listing at index $index is malformed")?.let(listings::add)
        }

        val contacts = linkedMapOf<String, PhonebookContact>()
        val contactsSection = yaml.getConfigurationSection("contacts")

        if (contactsSection != null) {
            for (key in contactsSection.getKeys(false).sorted()) {
                val contact = parseContact(yaml, key) ?: continue
                contacts[key] = contact
            }
        }

        return PhonebookData(listings = listings, contacts = contacts)
    }

    fun save(data: PhonebookData) {
        configPath.parent?.createDirectories()

        val yaml = YamlConfiguration()
        val sortedListings = mutableListOf<String>()
        for (listing in data.listings.map { it.toString().lowercase() }.distinct().sorted()) {
            sortedListings += listing
        }
        yaml.set("listings", sortedListings)

        val canonicalContacts = sortedMapOf<String, PhonebookContact>()
        for ((key, contact) in data.contacts) {
            val canonicalKey = PhonebookContactKeys.forCharacters(contact.lowerCharacterId, contact.higherCharacterId)
            if (key != canonicalKey) {
                logger.warning("Phonebook contact '$key' is not canonical; saving as '$canonicalKey'")
            }
            canonicalContacts[canonicalKey] = contact
        }
        for ((canonicalKey, contact) in canonicalContacts) {
            yaml.set("contacts.$canonicalKey.accountA", contact.lowerAccountId.toString())
            yaml.set("contacts.$canonicalKey.accountB", contact.higherAccountId.toString())
        }

        yaml.save(configPath.toFile())
    }

    private fun parseContact(yaml: YamlConfiguration, key: String): PhonebookContact? {
        val parts = key.split("_")
        if (parts.size != 2) {
            logger.warning("Phonebook contact '$key' is malformed: expected '<lower-character-uuid>_<higher-character-uuid>'")
            return null
        }

        val lowerCharacterId = parseUuid(parts[0], "Phonebook contact '$key' has malformed lower Character UUID")
            ?: return null
        val higherCharacterId = parseUuid(parts[1], "Phonebook contact '$key' has malformed higher Character UUID")
            ?: return null
        val expectedKey = PhonebookContactKeys.forCharacters(lowerCharacterId, higherCharacterId)
        if (key != expectedKey) {
            logger.warning("Phonebook contact '$key' is not canonical; expected '$expectedKey'")
            return null
        }

        val lowerAccountId = parseUuid(yaml.getString("contacts.$key.accountA"), "Phonebook contact '$key' has malformed accountA")
            ?: return null
        val higherAccountId = parseUuid(yaml.getString("contacts.$key.accountB"), "Phonebook contact '$key' has malformed accountB")
            ?: return null

        return PhonebookContact(lowerCharacterId, lowerAccountId, higherCharacterId, higherAccountId)
    }

    private fun parseUuid(value: String?, warning: String): UUID? {
        if (value == null) {
            logger.warning(warning)
            return null
        }
        return runCatching { UUID.fromString(value.lowercase()) }
            .onFailure { logger.warning(warning) }
            .getOrNull()
    }
}

object PhonebookContactKeys {
    fun forCharacters(firstCharacterId: UUID, secondCharacterId: UUID): String {
        val first = firstCharacterId.toString().lowercase()
        val second = secondCharacterId.toString().lowercase()
        return if (first <= second) "${first}_${second}" else "${second}_${first}"
    }
}
