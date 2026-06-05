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

class BukkitPhonebookDirectory : PhonebookDirectory, PhonebookActiveCharacters {
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

    override fun closeInventory() {
        player.closeInventory()
    }
}

fun PhonebookMessage.asComponent(): Component =
    Component.translatable(
        key,
        PhonebookMessageCatalog.argumentNames(key).map { name ->
            Component.text(requireNotNull(arguments[name]) { "Missing Phonebook message argument '$name' for '$key'" })
        },
    )

object PhonebookTranslations {
    private var registered = false

    fun registerDefaults() {
        if (registered) return

        val store = MiniMessageTranslationStore.create(
            Key.key("sneakymisc", "phonebook"),
            MiniMessage.miniMessage(),
        )
        store.defaultLocale(Locale.US)
        PhonebookMessageCatalog.entries.forEach { entry ->
            store.register(entry.key, Locale.US, entry.defaultMiniMessage)
        }
        GlobalTranslator.translator().addSource(store)
        registered = true
    }
}
