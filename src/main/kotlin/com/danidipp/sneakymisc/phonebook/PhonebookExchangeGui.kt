package com.danidipp.sneakymisc.phonebook

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class PhonebookExchangeGui(
    val requestId: String,
    val targetPlayerUuid: UUID,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 9, Component.text("Exchange Contacts"))

    override fun getInventory(): Inventory = inventory
}
