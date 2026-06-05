package com.danidipp.sneakymisc.phonebook

import ca.bungo.sneakycellphones.handler.CallManager
import java.util.Locale
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.translation.GlobalTranslator
import net.sneakycharactermanager.paper.handlers.character.Character
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class BukkitPhonebookDirectory : PhonebookDirectory {
    override fun activeCharacter(accountId: UUID): UUID? {
        val player = Bukkit.getPlayer(accountId) ?: return null
        return Character.get(player)?.characterUUID?.let(UUID::fromString)
    }

    override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
        Character.getPlayerCharacters(accountId)
            .firstOrNull { it.characterUUID.equals(characterId.toString(), ignoreCase = true) }
            ?.let { PhonebookCharacter(accountId, characterId, it.displayName) }

    override fun isOnline(accountId: UUID): Boolean =
        Bukkit.getPlayer(accountId)?.isOnline == true
}

class SneakyCellPhonesCaller : PhonebookCaller {
    override fun startOrReuseCall(callerAccountId: UUID, targetAccountId: UUID, targetDisplayName: String) {
        CallManager.O_createCallIfNotExists(
            callerAccountId.toString(),
            targetDisplayName,
            targetAccountId.toString(),
        )
    }
}

class BukkitPhonebookViewer(private val player: Player, private val inventoryFactory: PhonebookInventoryFactory) : PhonebookViewer {
    override val accountId: UUID = player.uniqueId
    override val permitted: Boolean = player.hasPermission(PhonebookCommand.PERMISSION)

    override fun sendMessage(message: PhonebookMessage) {
        player.sendMessage(message.asComponent())
    }

    override fun openInventory(model: PhonebookBrowserModel) {
        player.openInventory(inventoryFactory.create(model))
    }
}

fun PhonebookMessage.asComponent(): Component = Component.translatable(key,PhonebookMessageKeys.argumentNames(key).map { name ->
    Component.text(requireNotNull(arguments[name]) { "Missing Phonebook message argument '$name' for '$key'" })
})

object PhonebookTranslations {
    private var registered = false

    fun registerDefaults() {
        if (registered) return

        val store = MiniMessageTranslationStore.create(
            Key.key("sneakymisc", "phonebook"),
            MiniMessage.miniMessage(),
        )
        store.defaultLocale(Locale.US)
        store.register(PhonebookMessageKeys.NO_ACTIVE_CHARACTER, Locale.US, "<red>You have no active Character.")
        store.register(PhonebookMessageKeys.TARGET_OFFLINE, Locale.US, "<red><gold>{0}</gold> is no longer reachable.")
        store.register(PhonebookMessageKeys.STALE_OWNER, Locale.US, "<red>This Phonebook is no longer active.")
        store.register(PhonebookMessageKeys.PLAYER_ONLY, Locale.US, "<red>Only players can open a Phonebook.")
        store.register(PhonebookMessageKeys.TITLE, Locale.US, "<gold>Phonebook")
        GlobalTranslator.translator().addSource(store)
        registered = true
    }
}
