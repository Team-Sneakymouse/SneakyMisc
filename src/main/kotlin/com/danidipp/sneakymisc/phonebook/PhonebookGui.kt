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

        return inventory
    }

    fun selectedContactCharacter(item: ItemStack?): UUID? {
        val value = item?.itemMeta
            ?.persistentDataContainer
            ?.get(contactCharacterKey, PersistentDataType.STRING)
            ?: return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun contactItem(contact: VisiblePhonebookContact): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta
        meta.displayName(Component.text(contact.displayName))
        meta.persistentDataContainer.set(contactCharacterKey, PersistentDataType.STRING, contact.characterId.toString())
        item.itemMeta = meta
        return item
    }
}

class PhonebookGuiListener(
    private val guiActions: PhonebookGuiActions,
    private val inventoryFactory: PhonebookInventoryFactory,
) : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? PhonebookBrowsingHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) return

        val selectedContactCharacterId = inventoryFactory.selectedContactCharacter(event.currentItem) ?: return
        val player = event.whoClicked as? Player ?: return

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
