package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhonebookResolutionTest {
    @Test
    fun `visible contacts are listed characters with online controlling accounts`() {
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val ownerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val reachableCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val reachableAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val unlistedCharacter = UUID.fromString("30000000-0000-0000-0000-000000000000")
        val unlistedAccount = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val offlineCharacter = UUID.fromString("40000000-0000-0000-0000-000000000000")
        val offlineAccount = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val data = PhonebookData(
            listings = setOf(reachableCharacter, offlineCharacter),
            contacts = contacts(
                PhonebookContact(ownerCharacter, ownerAccount, reachableCharacter, reachableAccount),
                PhonebookContact(ownerCharacter, ownerAccount, unlistedCharacter, unlistedAccount),
                PhonebookContact(ownerCharacter, ownerAccount, offlineCharacter, offlineAccount),
            ),
        )
        val directory = FakePhonebookDirectory(
            characters = listOf(
                PhonebookCharacter(ownerAccount, ownerCharacter, "Owner"),
                PhonebookCharacter(reachableAccount, reachableCharacter, "Reachable"),
                PhonebookCharacter(unlistedAccount, unlistedCharacter, "Unlisted"),
                PhonebookCharacter(offlineAccount, offlineCharacter, "Offline"),
            ),
            onlineAccounts = setOf(reachableAccount, unlistedAccount),
        )

        val visible = PhonebookResolver(directory).visibleContacts(data, ownerCharacter)

        assertEquals(
            listOf(VisiblePhonebookContact(reachableAccount, reachableCharacter, "Reachable")),
            visible,
        )
    }

    @Test
    fun `contact resolution reports why a stored Phonebook Contact is not reachable`() {
        val ownerCharacter = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val ownerAccount = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val reachableCharacter = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val reachableAccount = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val unlistedCharacter = UUID.fromString("30000000-0000-0000-0000-000000000000")
        val unlistedAccount = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val offlineCharacter = UUID.fromString("40000000-0000-0000-0000-000000000000")
        val offlineAccount = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val missingCharacter = UUID.fromString("50000000-0000-0000-0000-000000000000")
        val missingAccount = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val absentCharacter = UUID.fromString("60000000-0000-0000-0000-000000000000")
        val data = PhonebookData(
            listings = setOf(reachableCharacter, offlineCharacter, missingCharacter),
            contacts = contacts(
                PhonebookContact.between(ownerCharacter, ownerAccount, reachableCharacter, reachableAccount),
                PhonebookContact.between(ownerCharacter, ownerAccount, unlistedCharacter, unlistedAccount),
                PhonebookContact.between(ownerCharacter, ownerAccount, offlineCharacter, offlineAccount),
                PhonebookContact.between(ownerCharacter, ownerAccount, missingCharacter, missingAccount),
            ),
        )
        val directory = FakePhonebookDirectory(
            characters = listOf(
                PhonebookCharacter(reachableAccount, reachableCharacter, "Reachable"),
                PhonebookCharacter(unlistedAccount, unlistedCharacter, "Unlisted"),
                PhonebookCharacter(offlineAccount, offlineCharacter, "Offline"),
            ),
            onlineAccounts = setOf(reachableAccount, unlistedAccount, missingAccount),
        )
        val resolver = PhonebookResolver(directory)

        val reachable = assertIs<ResolvedPhonebookContact>(resolver.resolveContact(data, ownerCharacter, reachableCharacter))
        val unlisted = assertIs<UnresolvedPhonebookContact>(resolver.resolveContact(data, ownerCharacter, unlistedCharacter))
        val offline = assertIs<UnresolvedPhonebookContact>(resolver.resolveContact(data, ownerCharacter, offlineCharacter))
        val missing = assertIs<UnresolvedPhonebookContact>(resolver.resolveContact(data, ownerCharacter, missingCharacter))
        val absent = assertIs<UnresolvedPhonebookContact>(resolver.resolveContact(data, ownerCharacter, absentCharacter))

        assertEquals(VisiblePhonebookContact(reachableAccount, reachableCharacter, "Reachable"), reachable.contact)
        assertEquals(PhonebookContactResolutionFailure.Unlisted, unlisted.reason)
        assertEquals("Unlisted", unlisted.character?.displayName)
        assertEquals(PhonebookContactResolutionFailure.Offline, offline.reason)
        assertEquals("Offline", offline.character?.displayName)
        assertEquals(PhonebookContactResolutionFailure.MissingCharacter, missing.reason)
        assertEquals(missingAccount, missing.accountId)
        assertEquals(PhonebookContactResolutionFailure.MissingContact, absent.reason)
        assertEquals(null, absent.accountId)
    }

    private fun contacts(vararg contacts: PhonebookContact): Map<String, PhonebookContact> =
        contacts.associateBy { PhonebookContactKeys.forCharacters(it.lowerCharacterId, it.higherCharacterId) }

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
