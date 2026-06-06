package com.danidipp.sneakymisc.phonebook

import ca.bungo.sneakycellphones.handler.CallManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.text.minimessage.translation.Argument
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
        characters(accountId).firstOrNull { it.characterId == characterId }

    override fun characters(accountId: UUID): List<PhonebookCharacter> =
        Character.getPlayerCharacters(accountId).mapNotNull {
            val characterId = runCatching { UUID.fromString(it.characterUUID) }.getOrNull()
                ?: return@mapNotNull null
            PhonebookCharacter(
                accountId = accountId,
                characterId = characterId,
                displayName = it.displayName,
                skin = PhonebookCharacterSkin(
                    skin = it.skin,
                    texture = it.texture,
                    signature = it.signature,
                    slim = it.isSlim,
                ),
            )
        }

    override fun isOnline(accountId: UUID): Boolean =
        Bukkit.getPlayer(accountId)?.isOnline == true
}

class BukkitPhonebookDebugTargetResolver : PhonebookDebugTargetResolver {
    override fun onlineAccount(name: String): UUID? =
        Bukkit.getPlayerExact(name)?.uniqueId
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
        player.openInventory(inventoryFactory.create(model, player))
    }

    override fun refreshInventory(model: PhonebookBrowserModel) {
        val topInventory = player.openInventory.topInventory
        if (topInventory.holder is PhonebookBrowsingHolder) {
            inventoryFactory.populate(topInventory, model, player)
        } else {
            openInventory(model)
        }
    }

    override fun closeInventory() {
        player.closeInventory()
    }
}

fun PhonebookMessage.asComponent(): Component =
    GlobalTranslator.render(
        Component.translatable(key, arguments.map { (name, value) -> Argument.component(name, value) }),
        Locale.US,
    )

object PhonebookTranslations {
    private var registered = false

    fun registerDefaults(messagesPath: Path) {
        if (registered) return
        require(Files.isRegularFile(messagesPath)) { "Missing Phonebook messages file '$messagesPath'" }

        val store = MiniMessageTranslationStore.create(
            Key.key("sneakymisc", "phonebook"),
            MiniMessage.miniMessage(),
        )
        store.defaultLocale(Locale.US)
        store.registerAll(Locale.US, messagesPath, false)
        GlobalTranslator.translator().addSource(store)
        registered = true
    }
}
