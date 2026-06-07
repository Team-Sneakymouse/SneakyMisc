package com.danidipp.sneakymisc.phonebook

import com.destroystokyo.paper.profile.ProfileProperty
import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

enum class PhonebookExchangeDecision {
    Accept,
    Decline,
}

class PhonebookExchangeHolder(val model: PhonebookExchangeDecisionModel) : InventoryHolder {
    private lateinit var backingInventory: Inventory

    fun attach(inventory: Inventory) {
        backingInventory = inventory
    }

    override fun getInventory(): Inventory = backingInventory
}

class PhonebookExchangeInventoryFactory(
    private val plugin: Plugin,
    private val headResolver: PhonebookHeadResolver = SneakyCharacterPhonebookHeadResolver,
) {
    private val decisionKey = NamespacedKey(plugin, "phonebook_exchange_decision")

    fun create(model: PhonebookExchangeDecisionModel, player: Player? = null): Inventory {
        val holder = PhonebookExchangeHolder(model)
        val inventory = Bukkit.createInventory(
            holder,
            INVENTORY_SIZE,
            PhonebookMessage("sneakymisc.phonebook.exchange.title").asComponent(),
        )
        holder.attach(inventory)
        inventory.setItem(BACKGROUND_SLOT, PhonebookGuiItems.background("addcontact"))
        inventory.setItem(ACCEPT_SLOT, decisionItem(Material.LIME_WOOL, "sneakymisc.phonebook.exchange.accept", PhonebookExchangeDecision.Accept))
        inventory.setItem(INITIATOR_SLOT, initiatorItem(model.initiator))
        inventory.setItem(DECLINE_SLOT, decisionItem(Material.RED_WOOL, "sneakymisc.phonebook.exchange.decline", PhonebookExchangeDecision.Decline))
        if (player != null) {
            resolveInitiatorHeadSkin(player, inventory, model)
        }
        return inventory
    }

    fun selectedDecision(item: ItemStack?): PhonebookExchangeDecision? {
        val value = item?.itemMeta
            ?.persistentDataContainer
            ?.get(decisionKey, PersistentDataType.STRING)
            ?: return null
        return runCatching { PhonebookExchangeDecision.valueOf(value) }.getOrNull()
    }

    private fun decisionItem(material: Material, key: String, decision: PhonebookExchangeDecision): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.itemName(PhonebookMessage(key).asComponent())
        PhonebookGuiItems.applyInvisibleModel(meta)
        meta.persistentDataContainer.set(decisionKey, PersistentDataType.STRING, decision.name)
        item.itemMeta = meta
        return item
    }

    private fun initiatorItem(initiator: PhonebookCharacter): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta
        meta.itemName(Component.text(initiator.displayName))
        item.itemMeta = meta
        return item
    }

    private fun resolveInitiatorHeadSkin(player: Player, inventory: Inventory, model: PhonebookExchangeDecisionModel) {
        val initiator = model.initiator
        val contact = VisiblePhonebookContact(
            accountId = initiator.accountId,
            characterId = initiator.characterId,
            displayName = initiator.displayName,
            skin = initiator.skin,
        )
        headResolver.resolve(player, contact)
            .exceptionally { null }
            .thenAccept { skin ->
                if (skin == null) return@thenAccept
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    applyInitiatorHeadSkin(inventory, model.exchangeId, initiator.characterId, skin)
                })
            }
    }

    private fun applyInitiatorHeadSkin(
        inventory: Inventory,
        exchangeId: PhonebookExchangeId,
        initiatorCharacterId: UUID,
        skin: PhonebookResolvedHeadSkin,
    ) {
        val holder = inventory.holder as? PhonebookExchangeHolder ?: return
        if (holder.model.exchangeId != exchangeId) return

        val currentItem = inventory.getItem(INITIATOR_SLOT) ?: return
        val meta = currentItem.itemMeta as? SkullMeta ?: return
        val profile = Bukkit.createProfile(initiatorCharacterId)
        profile.setProperty(ProfileProperty("textures", skin.texture, skin.signature))
        meta.playerProfile = profile
        currentItem.itemMeta = meta
        inventory.setItem(INITIATOR_SLOT, currentItem)
    }

    companion object {
        const val INVENTORY_SIZE = 9
        const val BACKGROUND_SLOT = 0
        const val ACCEPT_SLOT = 3
        const val INITIATOR_SLOT = 4
        const val DECLINE_SLOT = 5
    }
}

