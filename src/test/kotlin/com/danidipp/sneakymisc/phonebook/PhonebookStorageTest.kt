package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PhonebookStorageTest {
    @Test
    fun `contact keys use lowercase lexicographic character uuid ordering`() {
        val lowerByString = UUID.fromString("7fffffff-0000-0000-0000-000000000000")
        val higherByString = UUID.fromString("80000000-0000-0000-0000-000000000000")
        val lowerAccount = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val higherAccount = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val configPath = createTempFile(prefix = "phonebook", suffix = ".yml")
        configPath.writeText(
            """
            contacts:
              ${lowerByString}_${higherByString}:
                accountA: $lowerAccount
                accountB: $higherAccount
            """.trimIndent()
        )

        val loaded = PhonebookStorage(configPath, logger = noOpLogger()).load()

        assertEquals(
            PhonebookContact(lowerByString, lowerAccount, higherByString, higherAccount),
            loaded.contactBetween(lowerByString, higherByString),
        )
    }

    @Test
    fun `listings are a semantic set and saved output is sorted without version`() {
        val firstListing = UUID.fromString("22222222-0000-0000-0000-000000000000")
        val secondListing = UUID.fromString("11111111-0000-0000-0000-000000000000")
        val firstContact = PhonebookContact(
            UUID.fromString("30000000-0000-0000-0000-000000000000"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("40000000-0000-0000-0000-000000000000"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
        )
        val secondContact = PhonebookContact(
            UUID.fromString("10000000-0000-0000-0000-000000000000"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000000"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )
        val configPath = createTempFile(prefix = "phonebook-save", suffix = ".yml")
        val storage = PhonebookStorage(configPath, logger = noOpLogger())

        storage.save(
            PhonebookData(
                listings = setOf(firstListing, secondListing, firstListing),
                contacts = mapOf(
                    PhonebookContactKeys.forCharacters(firstContact.lowerCharacterId, firstContact.higherCharacterId) to firstContact,
                    PhonebookContactKeys.forCharacters(secondContact.lowerCharacterId, secondContact.higherCharacterId) to secondContact,
                ),
            )
        )

        val saved = configPath.readText()
        assertFalse(saved.contains("version"))
        assertEquals(
            listOf(secondListing.toString(), firstListing.toString()),
            storage.load().listings.map { it.toString() },
        )
        assertEquals(
            listOf(
                "10000000-0000-0000-0000-000000000000_20000000-0000-0000-0000-000000000000",
                "30000000-0000-0000-0000-000000000000_40000000-0000-0000-0000-000000000000",
            ),
            storage.load().contacts.keys.toList(),
        )
    }

    @Test
    fun `malformed persisted entries are skipped and omitted on next save`() {
        val validListing = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val validLowerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val validHigherCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val validLowerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val validHigherAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val configPath = createTempFile(prefix = "phonebook-malformed", suffix = ".yml")
        configPath.writeText(
            """
            listings:
              - not-a-uuid
              - $validListing

            contacts:
              malformed-key:
                accountA: $validLowerAccount
                accountB: $validHigherAccount
              ${validHigherCharacter}_${validLowerCharacter}:
                accountA: $validHigherAccount
                accountB: $validLowerAccount
              ${validLowerCharacter}_${validHigherCharacter}:
                accountA: $validLowerAccount
                accountB: $validHigherAccount
            """.trimIndent()
        )
        val warnings = mutableListOf<String>()
        val storage = PhonebookStorage(configPath, logger = warningLogger(warnings))

        val loaded = storage.load()
        storage.save(loaded)

        val saved = configPath.readText()
        assertEquals(setOf(validListing), loaded.listings)
        assertEquals(1, loaded.contacts.size)
        assertFalse(saved.contains("not-a-uuid"))
        assertFalse(saved.contains("malformed-key"))
        assertFalse(saved.contains("${validHigherCharacter}_${validLowerCharacter}"))
        assertEquals(3, warnings.size)
    }

    @Test
    fun `adding an existing contact does not duplicate persisted relationships`() {
        val firstCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val firstAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val secondCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val secondAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val configPath = createTempFile(prefix = "phonebook-duplicate", suffix = ".yml")
        val storage = PhonebookStorage(configPath, logger = noOpLogger())

        val firstAdd = storage.addContact(firstCharacter, firstAccount, secondCharacter, secondAccount)
        val secondAdd = storage.addContact(secondCharacter, secondAccount, firstCharacter, firstAccount)

        assertEquals(true, firstAdd)
        assertEquals(false, secondAdd)
        assertEquals(1, storage.load().contacts.size)
    }

    @Test
    fun `removing a contact deletes the bidirectional relationship without changing listings`() {
        val ownerCharacter = UUID.fromString("30000000-0000-0000-0000-000000000000")
        val ownerAccount = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val removedCharacter = UUID.fromString("40000000-0000-0000-0000-000000000000")
        val removedAccount = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val remainingLowerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val remainingLowerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val remainingHigherCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val remainingHigherAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val configPath = createTempFile(prefix = "phonebook-remove", suffix = ".yml")
        val storage = PhonebookStorage(configPath, logger = noOpLogger())
        val listings = setOf(ownerCharacter, removedCharacter, remainingLowerCharacter)
        val remainingContact = PhonebookContact.between(
            remainingLowerCharacter,
            remainingLowerAccount,
            remainingHigherCharacter,
            remainingHigherAccount,
        )
        storage.save(
            PhonebookData(
                listings = listings,
                contacts = mapOf(
                    PhonebookContactKeys.forCharacters(ownerCharacter, removedCharacter) to
                        PhonebookContact.between(ownerCharacter, ownerAccount, removedCharacter, removedAccount),
                    PhonebookContactKeys.forCharacters(remainingLowerCharacter, remainingHigherCharacter) to remainingContact,
                ),
            )
        )

        val result = storage.removeContact(removedCharacter, ownerCharacter)

        assertEquals(
            PhonebookContact.between(ownerCharacter, ownerAccount, removedCharacter, removedAccount),
            result.removedContact,
        )
        assertEquals(listings, result.data.listings)
        assertEquals(
            mapOf(PhonebookContactKeys.forCharacters(remainingLowerCharacter, remainingHigherCharacter) to remainingContact),
            storage.load().contacts,
        )
        assertEquals(listings, storage.load().listings)
    }

    @Test
    fun `saving canonicalizes contact keys before sorting and deduplicating output`() {
        val lowerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val lowerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val higherCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val higherAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val contact = PhonebookContact(lowerCharacter, lowerAccount, higherCharacter, higherAccount)
        val configPath = createTempFile(prefix = "phonebook-canonical-save", suffix = ".yml")
        val warnings = mutableListOf<String>()

        PhonebookStorage(configPath, logger = warningLogger(warnings)).save(
            PhonebookData(
                contacts = mapOf(
                    "${higherCharacter}_${lowerCharacter}" to contact,
                    PhonebookContactKeys.forCharacters(lowerCharacter, higherCharacter) to contact,
                ),
            )
        )

        val saved = configPath.readText()
        val canonicalKey = PhonebookContactKeys.forCharacters(lowerCharacter, higherCharacter)
        assertEquals(1, Regex("^  $canonicalKey:", RegexOption.MULTILINE).findAll(saved).count())
        assertEquals(listOf("Phonebook contact '${higherCharacter}_${lowerCharacter}' is not canonical; saving as '$canonicalKey'"), warnings)
    }

    private fun noOpLogger(): Logger = warningLogger(mutableListOf())

    private fun warningLogger(warnings: MutableList<String>): Logger =
        Logger.getAnonymousLogger().apply {
            useParentHandlers = false
            handlers.forEach(::removeHandler)
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) {
                    warnings += record.message
                }

                override fun flush() = Unit

                override fun close() = Unit
            })
        }
}
