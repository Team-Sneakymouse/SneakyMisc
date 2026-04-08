package com.danidipp.sneakymisc.phonebook

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class PhonebookGui(
    val ownerPlayerUuid: UUID,
    val page: Int,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 54, Component.text("Phonebook"))

    override fun getInventory(): Inventory = inventory
}
