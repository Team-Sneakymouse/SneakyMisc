package com.danidipp.sneakymisc.crates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.components.CustomModelDataComponent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.mockito.Mockito.*

class CrateGuiTest {
    private val definition = CrateDefinition("item-crate-pearlRawWIP", "item-pearl-raw",
        NamespacedKey("lom", "crates/pearl-raw"), 16)
    private val crate = mock(Crate::class.java)
    private val inventory = mock(Inventory::class.java)
    private val playerInventory = mock(PlayerInventory::class.java)
    private val player = mock(Player::class.java)
    private val view = mock(InventoryView::class.java)

    init {
        `when`(crate.isValid(player)).thenReturn(true)
        `when`(crate.definition).thenReturn(definition)
        `when`(inventory.holder).thenReturn(crate)
        `when`(inventory.size).thenReturn(36)
        `when`(view.topInventory).thenReturn(inventory)
        `when`(view.bottomInventory).thenReturn(playerInventory)
        `when`(view.player).thenReturn(player)
        `when`(player.inventory).thenReturn(playerInventory)
    }

    private fun click(
        action: InventoryAction,
        current: ItemStack? = null,
        cursor: ItemStack = magicItem(material = Material.AIR),
        slot: Int = 3,
        click: ClickType = ClickType.LEFT,
        hotbar: Int = -1,
    ): InventoryClickEvent {
        `when`(view.getInventory(slot)).thenReturn(if (slot < 36) inventory else playerInventory)
        `when`(view.convertSlot(slot)).thenReturn(if (slot < 36) slot else slot - 36)
        `when`(view.getItem(slot)).thenReturn(current)
        `when`(view.cursor).thenReturn(cursor)
        val event = InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, click, action, hotbar)
        CrateGui.listener.javaClass.getMethod("onInventoryClick", InventoryClickEvent::class.java)
            .invoke(CrateGui.listener, event)
        return event
    }

    @Test
    fun `decorations cannot be picked up with an empty cursor`() {
        for (material in listOf(Material.JIGSAW, Material.RABBIT_FOOT)) {
            assertTrue(click(InventoryAction.PICKUP_ALL, magicItem(material = material), slot = 0).isCancelled)
        }
    }

    @Test
    fun `matching items can be deposited and foreign items are rejected`() {
        assertFalse(click(InventoryAction.PLACE_ALL, cursor = magicItem()).isCancelled)
        assertTrue(click(InventoryAction.PLACE_ALL, cursor = magicItem("item-pearl-fine")).isCancelled)
        assertTrue(click(InventoryAction.SWAP_WITH_CURSOR, magicItem(), magicItem(null)).isCancelled)
        assertTrue(click(InventoryAction.PLACE_ALL, cursor = magicItem(), slot = 1).isCancelled)
    }

    @Test
    fun `shift transfers enforce identity without blocking the player inventory`() {
        assertFalse(click(InventoryAction.MOVE_TO_OTHER_INVENTORY, magicItem(), slot = 36,
            click = ClickType.SHIFT_LEFT).isCancelled)
        assertTrue(click(InventoryAction.MOVE_TO_OTHER_INVENTORY, magicItem("item-pearl-fine"), slot = 36,
            click = ClickType.SHIFT_LEFT).isCancelled)
        assertFalse(click(InventoryAction.PICKUP_ALL, magicItem("item-pearl-fine"), slot = 36).isCancelled)
        assertFalse(click(InventoryAction.MOVE_TO_OTHER_INVENTORY, magicItem()).isCancelled)
    }

    @Test
    fun `hotbar and offhand swaps enforce identity`() {
        doReturn(magicItem("item-pearl-fine")).`when`(playerInventory).getItem(2)
        assertTrue(click(InventoryAction.HOTBAR_SWAP, click = ClickType.NUMBER_KEY, hotbar = 2).isCancelled)
        doReturn(magicItem()).`when`(playerInventory).getItem(2)
        assertFalse(click(InventoryAction.HOTBAR_SWAP, click = ClickType.NUMBER_KEY, hotbar = 2).isCancelled)
        doReturn(magicItem(null)).`when`(playerInventory).itemInOffHand
        assertTrue(click(InventoryAction.HOTBAR_SWAP, click = ClickType.SWAP_OFFHAND).isCancelled)
        doReturn(magicItem()).`when`(playerInventory).itemInOffHand
        assertFalse(click(InventoryAction.HOTBAR_SWAP, click = ClickType.SWAP_OFFHAND).isCancelled)
    }

    @Test
    fun `a jigsaw MagicItem in a storage slot can be withdrawn`() {
        assertFalse(click(InventoryAction.PICKUP_ALL, magicItem(material = Material.JIGSAW)).isCancelled)
    }

    private fun drag(items: Map<Int, ItemStack>): InventoryDragEvent {
        val event = InventoryDragEvent(view, null, magicItem(), false, items)
        CrateGui.listener.javaClass.getMethod("onInventoryDrag", InventoryDragEvent::class.java)
            .invoke(CrateGui.listener, event)
        return event
    }

    @Test
    fun `drags reject foreign items and decoration slots`() {
        assertFalse(drag(mapOf(3 to magicItem(), 4 to magicItem())).isCancelled)
        assertTrue(drag(mapOf(3 to magicItem(), 4 to magicItem(null))).isCancelled)
        assertTrue(drag(mapOf(3 to magicItem(), 0 to magicItem())).isCancelled)
        assertFalse(drag(mapOf(36 to magicItem(null))).isCancelled)
    }

    @Test
    fun `background uses the crate model and string model data`() {
        val meta = mock(ItemMeta::class.java)
        val modelData = mock(CustomModelDataComponent::class.java)
        val pdc = mock(PersistentDataContainer::class.java)
        `when`(meta.customModelDataComponent).thenReturn(modelData)
        `when`(meta.persistentDataContainer).thenReturn(pdc)
        mockConstruction(ItemStack::class.java) { item, _ ->
            `when`(item.itemMeta).thenReturn(meta)
        }.use { construction ->
            CrateGui.getFiller(0, definition)
            assertEquals(1, construction.constructed().size)
            verify(meta).setItemModel(definition.itemModel)
            verify(modelData).setStrings(listOf("background"))
            verify(meta).setCustomModelDataComponent(modelData)
            verify(pdc).set(NamespacedKey("sneakymisc", "crate_decoration"), PersistentDataType.BYTE, 1.toByte())
        }
    }

    @Test
    fun `vanilla shulker block inventories remain unusable`() {
        `when`(inventory.holder).thenReturn(mock(ShulkerBox::class.java))
        val event = InventoryOpenEvent(view)
        CrateGui.listener.javaClass.getMethod("onInventoryOpen", InventoryOpenEvent::class.java)
            .invoke(CrateGui.listener, event)
        assertTrue(event.isCancelled)
    }
}
