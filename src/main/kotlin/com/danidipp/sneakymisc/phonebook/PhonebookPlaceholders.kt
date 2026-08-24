package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyMisc
import java.util.UUID
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class PhonebookPlaceholders(
    private val listings: PhonebookListingLookup,
    private val activeCharacters: PhonebookActiveCharacters,
) : PlaceholderExpansion() {
    override fun getIdentifier() = SneakyMisc.IDENTIFIER
    override fun getAuthor() = SneakyMisc.AUTHORS
    override fun getVersion() = SneakyMisc.VERSION
    override fun persist() = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        return when (params) {
            "phonebook_listing" -> {
                if (player == null) "unlisted"
                else listingStatus(player.uniqueId)
            }
            else -> null
        }
    }

    fun listingStatus(accountId: UUID): String = when (val characterId = activeCharacters.activeCharacter(accountId)) {
        null -> "unlisted"
        else -> if (listings.isListed(characterId)) "listed" else "unlisted"
    }
}
