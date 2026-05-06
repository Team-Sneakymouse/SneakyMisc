package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyMisc
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class PhonebookRepository(
    private val logger: Logger,
    private val file: File = File(SneakyMisc.getInstance().dataFolder, "phonebooks.yml"),
) {
    private val records = linkedMapOf<UUID, PhonebookRecord>()

    init {
        load()
    }

    fun getContacts(ownerPlayerUuid: UUID): List<String> =
        records[ownerPlayerUuid]?.contacts?.map { it.characterUuid }.orEmpty()

    fun getContactIds(ownerPlayerUuid: UUID): List<PhonebookContactId> =
        records[ownerPlayerUuid]?.contacts?.map { it.contactId }.orEmpty()

    fun addContact(ownerPlayerUuid: UUID, characterUuid: String): Boolean {
        val contactId = PhonebookContactId(characterUuid)
        return addContact(ownerPlayerUuid, contactId)
    }

    fun addContact(ownerPlayerUuid: UUID, contactId: PhonebookContactId): Boolean {
        val record = records.getOrPut(ownerPlayerUuid) {
            PhonebookRecord(ownerPlayerUuid.toString())
        }
        if (record.contacts.any { it.contactId == contactId }) return false
        record.contacts.add(PhonebookEntry(contactId))
        save()
        return true
    }

    fun removeContact(ownerPlayerUuid: UUID, characterUuid: String): Boolean {
        val contactId = PhonebookContactId(characterUuid)
        return removeContact(ownerPlayerUuid, contactId)
    }

    fun removeContact(ownerPlayerUuid: UUID, contactId: PhonebookContactId): Boolean {
        val record = records[ownerPlayerUuid] ?: return false
        val removed = record.contacts.removeIf { it.contactId == contactId }
        if (!removed) return false
        save()
        return true
    }

    fun containsContact(ownerPlayerUuid: UUID, characterUuid: String): Boolean =
        containsContact(ownerPlayerUuid, PhonebookContactId(characterUuid))

    fun containsContact(ownerPlayerUuid: UUID, contactId: PhonebookContactId): Boolean =
        records[ownerPlayerUuid]?.contacts?.any { it.contactId == contactId } == true

    fun save() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        val yaml = YamlConfiguration()
        val phonebooksSection = yaml.createSection("phonebooks")
        for ((ownerUuid, record) in records) {
            val recordSection = phonebooksSection.createSection(ownerUuid.toString())
            val contactMaps = record.contacts.map { mapOf("characterUuid" to it.contactId.value) }
            recordSection.set("contacts", contactMaps)
        }

        yaml.save(file)
    }

    private fun load() {
        records.clear()
        if (!file.exists()) return

        val yaml = YamlConfiguration.loadConfiguration(file)
        val phonebooksSection = yaml.getConfigurationSection("phonebooks") ?: return
        for (ownerKey in phonebooksSection.getKeys(false)) {
            val ownerUuid = runCatching { UUID.fromString(ownerKey) }.getOrNull()
            if (ownerUuid == null) {
                logger.warning("Skipping invalid phonebook owner UUID '$ownerKey'")
                continue
            }

            val recordSection = phonebooksSection.getConfigurationSection(ownerKey)
            val rawContacts = recordSection?.getMapList("contacts").orEmpty()
            val contacts = rawContacts.mapNotNull { raw ->
                val contactId = parseContactId(raw["characterUuid"]?.toString()) ?: return@mapNotNull null
                PhonebookEntry(contactId)
            }.distinctBy { it.contactId }.toMutableList()

            records[ownerUuid] = PhonebookRecord(ownerUuid.toString(), contacts)
        }
    }

    private fun parseContactId(raw: String?): PhonebookContactId? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return PhonebookContactId(value)
    }
}
