package com.danidipp.sneakymisc.phonebook

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

internal object PhonebookGuiItems {
    private val orbItemModel = NamespacedKey.fromString("lom:mlm_orb")
        ?: error("Failed to create Phonebook GUI orb item model key")
    private val invisibleItemModel = NamespacedKey.fromString("lom:invisible")
        ?: error("Failed to create Phonebook GUI invisible item model key")

    fun background(customModelData: String): ItemStack {
        val item = ItemStack(Material.BRICK)
        val meta = item.itemMeta
        meta.setItemModel(orbItemModel)
        val customModelDataComponent = meta.customModelDataComponent
        customModelDataComponent.setStrings(listOf(customModelData))
        meta.setCustomModelDataComponent(customModelDataComponent)
        meta.setHideTooltip(true)
        item.itemMeta = meta
        return item
    }

    fun applyInvisibleModel(meta: ItemMeta) {
        meta.setItemModel(invisibleItemModel)
    }
}
