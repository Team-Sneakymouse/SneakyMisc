package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookPlaceholdersTest {
    @Test
    fun `listing status reports listed for a listed active Character`() {
        val accountId = uuid(1)
        val characterId = uuid(100)
        val placeholders = PhonebookPlaceholders(
            listings = FixedPhonebookListingLookup(setOf(characterId)),
            activeCharacters = FixedActiveCharacters(mapOf(accountId to characterId)),
        )

        assertEquals("listed", placeholders.listingStatus(accountId))
    }

    @Test
    fun `listing status reports unlisted for an unlisted active Character`() {
        val accountId = uuid(1)
        val characterId = uuid(100)
        val placeholders = PhonebookPlaceholders(
            listings = FixedPhonebookListingLookup(emptySet()),
            activeCharacters = FixedActiveCharacters(mapOf(accountId to characterId)),
        )

        assertEquals("unlisted", placeholders.listingStatus(accountId))
    }

    @Test
    fun `listing status reports unlisted without an active Character`() {
        val accountId = uuid(1)
        val placeholders = PhonebookPlaceholders(
            listings = FixedPhonebookListingLookup(setOf(uuid(100))),
            activeCharacters = FixedActiveCharacters(emptyMap()),
        )

        assertEquals("unlisted", placeholders.listingStatus(accountId))
    }

    private class FixedPhonebookListingLookup(private val listedCharacters: Set<UUID>) : PhonebookListingLookup {
        override fun isListed(characterId: UUID): Boolean = characterId in listedCharacters
    }

    private class FixedActiveCharacters(private val activeCharacters: Map<UUID, UUID>) : PhonebookActiveCharacters {
        override fun activeCharacter(accountId: UUID): UUID? = activeCharacters[accountId]
    }

    private fun uuid(value: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
}
