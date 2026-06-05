package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

class PhonebookBrowsingHolder(val state: PhonebookBrowserState) : InventoryHolder {
    private lateinit var backingInventory: Inventory

    fun attach(inventory: Inventory) {
        backingInventory = inventory
    }

    override fun getInventory(): Inventory = backingInventory
}

class PhonebookInventoryFactory(plugin: Plugin) {
    val contactCharacterKey = NamespacedKey(plugin, "phonebook_contact_character")
    private val browserActionKey = NamespacedKey(plugin, "phonebook_browser_action")

    fun create(model: PhonebookBrowserModel): Inventory {
        val holder = PhonebookBrowsingHolder(model.state)
        val inventory = Bukkit.createInventory(
            holder,
            PhonebookBrowserRenderer.INVENTORY_SIZE,
            Component.translatable(PhonebookMessageKeys.TITLE),
        )
        holder.attach(inventory)

        for ((index, contact) in model.visibleContacts.withIndex()) {
            val slot = PhonebookBrowserRenderer.CONTACT_SLOTS.getOrNull(index) ?: break
            inventory.setItem(slot, contactItem(contact))
        }
        inventory.setItem(PhonebookBrowserRenderer.ADD_CONTACT_SLOT, addContactItem())

        return inventory
    }

    fun selectedContactCharacter(item: ItemStack?): UUID? {
        val value = item?.itemMeta
            ?.persistentDataContainer
            ?.get(contactCharacterKey, PersistentDataType.STRING)
            ?: return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    fun isAddContact(item: ItemStack?): Boolean =
        item?.itemMeta
            ?.persistentDataContainer
            ?.get(browserActionKey, PersistentDataType.STRING) == ADD_CONTACT_ACTION

    private fun contactItem(contact: VisiblePhonebookContact): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta
        meta.displayName(Component.text(contact.displayName))
        meta.persistentDataContainer.set(contactCharacterKey, PersistentDataType.STRING, contact.characterId.toString())
        item.itemMeta = meta
        return item
    }

    private fun addContactItem(): ItemStack {
        val item = ItemStack(Material.LIME_DYE)
        val meta = item.itemMeta
        meta.displayName(Component.translatable(PhonebookMessageKeys.EXCHANGE_ADD_CONTACT))
        meta.persistentDataContainer.set(browserActionKey, PersistentDataType.STRING, ADD_CONTACT_ACTION)
        item.itemMeta = meta
        return item
    }

    private companion object {
        const val ADD_CONTACT_ACTION = "add_contact"
    }
}

class PhonebookGuiListener(
    private val guiActions: PhonebookGuiActions,
    private val inventoryFactory: PhonebookInventoryFactory,
    private val exchangeGateway: PhonebookExchangeGateway = NoOpPhonebookExchangeGateway,
) : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? PhonebookBrowsingHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        if (inventoryFactory.isAddContact(event.currentItem)) {
            exchangeGateway.startSeeking(player.uniqueId)
            return
        }

        val selectedContactCharacterId = inventoryFactory.selectedContactCharacter(event.currentItem) ?: return

        if (event.click == ClickType.SWAP_OFFHAND) {
            guiActions.removeContact(BukkitPhonebookViewer(player, inventoryFactory), holder.state, selectedContactCharacterId)
            return
        }

        if (!event.isLeftClick) return

        guiActions.callContact(BukkitPhonebookViewer(player, inventoryFactory), holder.state, selectedContactCharacterId)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is PhonebookBrowsingHolder) {
            event.isCancelled = true
        }
    }

}

interface PhonebookExchangeGateway {
    fun startSeeking(initiatorAccountId: UUID)
}

private object NoOpPhonebookExchangeGateway : PhonebookExchangeGateway {
    override fun startSeeking(initiatorAccountId: UUID) = Unit
}

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

class PhonebookExchangeInventoryFactory(plugin: Plugin) {
    private val decisionKey = NamespacedKey(plugin, "phonebook_exchange_decision")

    fun create(model: PhonebookExchangeDecisionModel): Inventory {
        val holder = PhonebookExchangeHolder(model)
        val inventory = Bukkit.createInventory(
            holder,
            INVENTORY_SIZE,
            Component.translatable(PhonebookMessageKeys.EXCHANGE_TITLE),
        )
        holder.attach(inventory)
        inventory.setItem(ACCEPT_SLOT, decisionItem(Material.LIME_WOOL, PhonebookMessageKeys.EXCHANGE_ACCEPT, PhonebookExchangeDecision.Accept))
        inventory.setItem(DECLINE_SLOT, decisionItem(Material.RED_WOOL, PhonebookMessageKeys.EXCHANGE_DECLINE, PhonebookExchangeDecision.Decline))
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
        meta.displayName(Component.translatable(key))
        meta.persistentDataContainer.set(decisionKey, PersistentDataType.STRING, decision.name)
        item.itemMeta = meta
        return item
    }

    companion object {
        const val INVENTORY_SIZE = 27
        const val ACCEPT_SLOT = 11
        const val DECLINE_SLOT = 15
    }
}
