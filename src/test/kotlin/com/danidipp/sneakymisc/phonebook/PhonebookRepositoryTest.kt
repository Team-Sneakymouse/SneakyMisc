package com.danidipp.sneakymisc.phonebook

import java.io.File
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonebookRepositoryTest {
    private val logger = Logger.getLogger(PhonebookRepositoryTest::class.java.name)

    @Test
    fun `missing file starts empty and saves new contacts`() {
        val path = createTempFile(prefix = "phonebook-missing", suffix = ".yml")
        path.deleteIfExists()
        val owner = UUID.fromString("11111111-1111-1111-1111-111111111111")

        val repository = PhonebookRepository(logger, path.toFile())

        assertEquals(emptyList(), repository.getContacts(owner))
        assertTrue(repository.addContact(owner, "character-a"))
        assertEquals(listOf("character-a"), repository.getContacts(owner))
        assertTrue(path.toFile().readText().contains("character-a"))
    }

    @Test
    fun `loaded contacts are deduplicated and blank entries are skipped`() {
        val owner = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val file = tempPhonebook(
            """
            phonebooks:
              $owner:
                contacts:
                  - characterUuid: character-a
                  - characterUuid: character-a
                  - characterUuid: ""
                  - characterUuid: character-b
            """.trimIndent()
        )

        val repository = PhonebookRepository(logger, file)

        assertEquals(listOf("character-a", "character-b"), repository.getContacts(owner))
        assertEquals(
            listOf(PhonebookContactId("character-a"), PhonebookContactId("character-b")),
            repository.getContactIds(owner),
        )
    }

    @Test
    fun `add remove and contains are idempotent`() {
        val owner = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val file = tempPhonebook("phonebooks: {}")
        val repository = PhonebookRepository(logger, file)
        val contactId = PhonebookContactId("character-a")

        assertFalse(repository.containsContact(owner, contactId))
        assertTrue(repository.addContact(owner, contactId))
        assertFalse(repository.addContact(owner, contactId))
        assertTrue(repository.containsContact(owner, contactId))
        assertEquals(listOf("character-a"), repository.getContacts(owner))

        assertTrue(repository.removeContact(owner, contactId))
        assertFalse(repository.removeContact(owner, contactId))
        assertFalse(repository.containsContact(owner, contactId))
        assertEquals(emptyList(), repository.getContacts(owner))
    }

    @Test
    fun `invalid owner keys are skipped`() {
        val validOwner = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val file = tempPhonebook(
            """
            phonebooks:
              invalid-owner:
                contacts:
                  - characterUuid: skipped
              $validOwner:
                contacts:
                  - characterUuid: character-a
            """.trimIndent()
        )

        val repository = PhonebookRepository(logger, file)

        assertEquals(listOf("character-a"), repository.getContacts(validOwner))
    }

    private fun tempPhonebook(contents: String): File {
        val path = createTempFile(prefix = "phonebook", suffix = ".yml")
        path.writeText(contents)
        return path.toFile()
    }
}
