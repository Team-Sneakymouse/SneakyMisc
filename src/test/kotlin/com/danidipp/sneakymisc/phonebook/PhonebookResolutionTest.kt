package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

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
