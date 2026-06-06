package com.danidipp.sneakymisc.phonebook

import java.util.UUID

data class PhonebookDebugModel(
    val accountId: UUID,
    val activeCharacterId: UUID?,
    val characters: List<PhonebookDebugCharacterSummary>,
    val malformedPersistedContacts: PhonebookDebugExamplePage<PhonebookMalformedEntry> =
        PhonebookDebugExamplePage(totalCount = 0, limit = 0, examples = emptyList()),
)

data class PhonebookDebugCharacterSummary(
    val character: PhonebookCharacter,
    val active: Boolean,
    val listed: Boolean,
    val storedContactCount: Int,
    val visibleContactCount: Int,
    val missingContactCount: Int,
    val contactExamples: PhonebookDebugExamplePage<PhonebookDebugContactExample>,
    val missingContactExamples: PhonebookDebugExamplePage<PhonebookDebugMissingContact>,
)

data class PhonebookDebugContactExample(
    val characterId: UUID,
    val accountId: UUID,
    val displayName: String?,
    val listed: Boolean,
    val online: Boolean,
    val visible: Boolean,
    val missingCharacter: Boolean,
)

data class PhonebookDebugMissingContact(
    val accountId: UUID,
    val characterId: UUID,
)

data class PhonebookDebugExamplePage<T>(
    val totalCount: Int,
    val limit: Int,
    val examples: List<T>,
) {
    val omittedCount: Int = (totalCount - examples.size).coerceAtLeast(0)
}

interface PhonebookDebugInspection {
    fun inspect(accountId: UUID): PhonebookDebugModel
}

class PhonebookDebugInspector(
    private val phonebooks: PhonebookDataStore,
    private val activeCharacters: PhonebookActiveCharacters,
    private val directory: PhonebookDirectory,
    private val exampleLimit: Int = 5,
) : PhonebookDebugInspection {
    private val resolver = PhonebookResolver(directory)

    override fun inspect(accountId: UUID): PhonebookDebugModel {
        val persistence = (phonebooks as? PhonebookDiagnosticStore)?.diagnostics()
            ?: PhonebookPersistenceDiagnostics(data = phonebooks.load())
        val data = persistence.data
        val activeCharacterId = activeCharacters.activeCharacter(accountId)
        val characters = directory.characters(accountId)
            .sortedWith(compareBy<PhonebookCharacter> { it.displayName.lowercase() }.thenBy { it.characterId.toString() })
            .map { character ->
                summarizeCharacter(data, character, activeCharacterId)
            }

        return PhonebookDebugModel(
            accountId = accountId,
            activeCharacterId = activeCharacterId,
            characters = characters,
            malformedPersistedContacts = PhonebookDebugExamplePage(
                totalCount = persistence.malformedContacts.size,
                limit = exampleLimit,
                examples = persistence.malformedContacts.take(exampleLimit),
            ),
        )
    }

    private fun summarizeCharacter(
        data: PhonebookData,
        character: PhonebookCharacter,
        activeCharacterId: UUID?,
    ): PhonebookDebugCharacterSummary {
        val contacts = data.contacts.values
            .filter { it.otherCharacter(character.characterId) != null }
            .sortedBy { it.otherCharacter(character.characterId).toString() }
        val examples = contacts.map { contact -> contactExample(data, character.characterId, contact) }
        val missingContacts = examples
            .filter { it.missingCharacter }
            .map { PhonebookDebugMissingContact(it.accountId, it.characterId) }

        return PhonebookDebugCharacterSummary(
            character = character,
            active = character.characterId == activeCharacterId,
            listed = character.characterId in data.listings,
            storedContactCount = contacts.size,
            visibleContactCount = examples.count { it.visible },
            missingContactCount = missingContacts.size,
            contactExamples = PhonebookDebugExamplePage(
                totalCount = examples.size,
                limit = exampleLimit,
                examples = examples.take(exampleLimit),
            ),
            missingContactExamples = PhonebookDebugExamplePage(
                totalCount = missingContacts.size,
                limit = exampleLimit,
                examples = missingContacts.take(exampleLimit),
            ),
        )
    }

    private fun contactExample(data: PhonebookData, ownerCharacterId: UUID, contact: PhonebookContact): PhonebookDebugContactExample {
        val resolution = requireNotNull(resolver.resolveStoredContact(data, ownerCharacterId, contact))

        return PhonebookDebugContactExample(
            characterId = resolution.characterId,
            accountId = requireNotNull(resolution.accountId),
            displayName = resolution.character?.displayName,
            listed = resolution.listed,
            online = resolution.online,
            visible = resolution is ResolvedPhonebookContact,
            missingCharacter = resolution.character == null,
        )
    }
}
