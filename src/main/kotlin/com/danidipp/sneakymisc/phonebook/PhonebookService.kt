package com.danidipp.sneakymisc.phonebook

import ca.bungo.sneakycellphones.handler.CallManager
import net.sneakycharactermanager.paper.SneakyCharacterManager
import net.sneakycharactermanager.paper.handlers.character.Character
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class PhonebookService(
    private val logger: Logger,
    private val repository: PhonebookRepository,
) {
    @Volatile
    private var characterIndex = emptyMap<String, CharacterIndexEntry>()

    init {
        refreshCharacterIndex()
    }

    fun refreshCharacterIndex() {
        val dataFolder = SneakyCharacterManager.getCharacterDataFolder()
        if (!dataFolder.exists() || !dataFolder.isDirectory) {
            characterIndex = emptyMap()
            return
        }

        val nextIndex = linkedMapOf<String, CharacterIndexEntry>()
        for (file in dataFolder.listFiles().orEmpty().filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }) {
            val ownerUuid = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
            if (ownerUuid == null) {
                logger.warning("Skipping invalid SneakyCharacterManager data file '${file.name}'")
                continue
            }

            loadCharactersForOwner(file, ownerUuid, nextIndex)
        }

        characterIndex = nextIndex
    }

    fun getVisibleContacts(owner: Player): List<RenderedContact> {
        refreshCharacterIndex()
        val index = characterIndex
        return repository.getContacts(owner.uniqueId).mapNotNull { characterUuid ->
            val entry = index[characterUuid] ?: return@mapNotNull null
            val owningPlayer = Bukkit.getPlayer(entry.ownerPlayerUuid) ?: return@mapNotNull null
            if (!owningPlayer.isOnline) return@mapNotNull null

            RenderedContact(
                characterUuid = characterUuid,
                characterName = entry.characterName,
                ownerPlayerUuid = entry.ownerPlayerUuid.toString(),
                ownerPlayerName = owningPlayer.name,
            )
        }
    }

    fun removeContact(owner: Player, characterUuid: String): Boolean =
        repository.removeContact(owner.uniqueId, characterUuid)

    fun callContact(owner: Player, rendered: RenderedContact): Result<Unit> {
        val targetUuid = runCatching { UUID.fromString(rendered.ownerPlayerUuid) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Stored contact owner is invalid"))
        val targetPlayer = Bukkit.getPlayer(targetUuid)
            ?: return Result.failure(IllegalStateException("That contact is no longer online"))
        if (!targetPlayer.isOnline) return Result.failure(IllegalStateException("That contact is no longer online"))

        refreshCharacterIndex()
        val currentEntry = characterIndex[rendered.characterUuid]
            ?: return Result.failure(IllegalStateException("That contact could not be resolved"))
        if (currentEntry.ownerPlayerUuid != targetUuid) {
            return Result.failure(IllegalStateException("That contact no longer belongs to the same player"))
        }

        val call = CallManager.O_createCallIfNotExists(
            owner.uniqueId.toString(),
            rendered.characterName,
            rendered.ownerPlayerUuid,
        ) ?: return Result.failure(IllegalStateException("Failed to start the phone call"))

        return if (call.invitedMembers.contains(rendered.ownerPlayerUuid) || call.activeMembers.contains(rendered.ownerPlayerUuid)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to invite that contact"))
        }
    }

    fun exchangeContacts(requester: Player, target: Player): Result<Unit> {
        val requesterCharacter = Character.get(requester)
            ?: return Result.failure(IllegalStateException("You do not have an active character"))
        val targetCharacter = Character.get(target)
            ?: return Result.failure(IllegalStateException("That player does not have an active character"))

        repository.addContact(requester.uniqueId, targetCharacter.characterUUID)
        repository.addContact(target.uniqueId, requesterCharacter.characterUUID)
        return Result.success(Unit)
    }

    fun containsContact(ownerPlayerUuid: UUID, characterUuid: String): Boolean =
        repository.containsContact(ownerPlayerUuid, characterUuid)

    fun getCurrentCharacter(player: Player): Character? = Character.get(player)

    private fun loadCharactersForOwner(
        file: File,
        ownerUuid: UUID,
        index: MutableMap<String, CharacterIndexEntry>,
    ) {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            if (key.equals("lastPlayedCharacter", ignoreCase = true)) continue
            val section = yaml.getConfigurationSection(key) ?: continue
            val name = section.getString("name") ?: continue
            index[key] = CharacterIndexEntry(ownerUuid, name)
        }
    }
}
