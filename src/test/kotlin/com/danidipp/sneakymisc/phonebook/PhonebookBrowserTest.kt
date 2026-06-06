package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonebookBrowserTest {
    @Test
    fun `browser paginates reachable contacts into stable contact slots`() {
        val ownerAccount = uuid(1)
        val ownerCharacter = uuid(100)
        val contacts = (1..43).map { index ->
            val account = uuid(1_000 + index)
            val character = uuid(2_000 + index)
            IndexedContact(account, character, "Contact ${index.toString().padStart(2, '0')}")
        }
        val data = PhonebookData(
            listings = contacts.map { it.characterId }.toSet(),
            contacts = contacts.associate { contact ->
                PhonebookContactKeys.forCharacters(ownerCharacter, contact.characterId) to
                    PhonebookContact.between(ownerCharacter, ownerAccount, contact.characterId, contact.accountId)
            },
        )
        val directory = FakePhonebookDirectory(
            contacts.map { PhonebookCharacter(it.accountId, it.characterId, it.displayName) },
            contacts.map { it.accountId }.toSet(),
        )
        val browser = PhonebookBrowser(
            resolver = PhonebookResolver(directory),
            renderer = PhonebookBrowserRenderer(),
            renderTokenProvider = IteratorRenderTokens(10L, 11L)::next,
        )

        val firstPage = browser.firstPage(data, ownerAccount, ownerCharacter)
        val secondPage = browser.page(data, firstPage.state, page = 1)

        assertEquals(54, PhonebookBrowserRenderer.INVENTORY_SIZE)
        assertEquals(PhonebookBrowserRenderer.CONTACT_SLOTS, firstPage.contactItems.map { it.slot })
        assertEquals(42, firstPage.visibleContacts.size)
        assertEquals("Contact 01", firstPage.visibleContacts.first().displayName)
        assertEquals("Contact 42", firstPage.visibleContacts.last().displayName)
        assertFalse(firstPage.hasPreviousPage)
        assertTrue(firstPage.hasNextPage)

        assertEquals(PhonebookBrowserState(ownerAccount, ownerCharacter, page = 1, renderToken = 11L), secondPage.state)
        assertEquals(listOf(PhonebookBrowserRenderer.CONTACT_SLOTS.first()), secondPage.contactItems.map { it.slot })
        assertEquals(listOf("Contact 43"), secondPage.visibleContacts.map { it.displayName })
        assertTrue(secondPage.hasPreviousPage)
        assertFalse(secondPage.hasNextPage)
    }

    @Test
    fun `skin updates apply only to matching holder and contact item metadata`() {
        val ownerAccount = uuid(1)
        val ownerCharacter = uuid(100)
        val contactCharacter = uuid(200)
        val holderState = PhonebookBrowserState(ownerAccount, ownerCharacter, page = 1, renderToken = 20)
        val update = PhonebookSkinUpdateTarget(
            renderToken = 20,
            page = 1,
            slot = PhonebookBrowserRenderer.CONTACT_SLOTS.first(),
            contactCharacterId = contactCharacter,
        )
        val matchingItem = PhonebookContactItemMetadata(
            renderToken = 20,
            page = 1,
            slot = PhonebookBrowserRenderer.CONTACT_SLOTS.first(),
            contactCharacterId = contactCharacter,
        )

        assertTrue(update.canApply(holderState, matchingItem))
        assertFalse(update.copy(renderToken = 19).canApply(holderState, matchingItem))
        assertFalse(update.copy(page = 0).canApply(holderState, matchingItem))
        assertFalse(update.copy(slot = PhonebookBrowserRenderer.CONTACT_SLOTS[1]).canApply(holderState, matchingItem))
        assertFalse(update.copy(contactCharacterId = uuid(201)).canApply(holderState, matchingItem))
        assertFalse(update.canApply(holderState.copy(renderToken = 21), matchingItem))
    }


    private data class IndexedContact(
        val accountId: UUID,
        val characterId: UUID,
        val displayName: String,
    )

    private fun uuid(value: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private class IteratorRenderTokens(vararg tokens: Long) {
        private val iterator = tokens.iterator()

        fun next(): Long = iterator.next()
    }

    private class FakePhonebookDirectory(
        characters: List<PhonebookCharacter>,
        private val onlineAccounts: Set<UUID>,
    ) : PhonebookDirectory {
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts
    }
}
