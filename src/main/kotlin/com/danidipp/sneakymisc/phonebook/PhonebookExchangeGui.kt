package com.danidipp.sneakymisc.phonebook

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
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

