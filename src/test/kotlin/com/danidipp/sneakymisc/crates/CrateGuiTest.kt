package com.danidipp.sneakymisc.crates

import kotlin.test.Test
import kotlin.test.assertTrue
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.mockito.Mockito.*

class CrateGuiTest {
    @Test
    fun `decorations cannot be picked up with an empty cursor`() {
        // GUI item construction normally requires a running Bukkit server.
        mockConstruction(ItemStack::class.java) { item, _ ->
            `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java))
        }.use {
            val listener = CrateGui.listener
            for (material in listOf(Material.JIGSAW, Material.RABBIT_FOOT)) {
                val decoration = mock(ItemStack::class.java)
                `when`(decoration.type).thenReturn(material)
                `when`(decoration.isSimilar(CrateGui.fillerItem)).thenReturn(material == Material.RABBIT_FOOT)
                val cursor = mock(ItemStack::class.java)
                `when`(cursor.type).thenReturn(Material.AIR)
                // AIR has no item metadata.
                `when`(cursor.itemMeta).thenReturn(null)
                val crate = mock(Crate::class.java)
                `when`(crate.isValid()).thenReturn(true)
                val inventory = mock(Inventory::class.java)
                `when`(inventory.holder).thenReturn(crate)
                val view = mock(InventoryView::class.java)
                `when`(view.topInventory).thenReturn(inventory)
                `when`(view.getInventory(0)).thenReturn(inventory)
                `when`(view.getItem(0)).thenReturn(decoration)
                `when`(view.cursor).thenReturn(cursor)
                val event = InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                    ClickType.LEFT, InventoryAction.PICKUP_ALL)

                listener.javaClass.getMethod("onInventoryClick", InventoryClickEvent::class.java)
                    .invoke(listener, event)

                assertTrue(event.isCancelled, "$material must remain in the crate")
            }
        }
    }
}
