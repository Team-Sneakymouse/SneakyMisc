package com.danidipp.sneakymisc.crates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.mockito.Mockito.*

internal fun magicItem(
    id: String? = "item-pearl-raw",
    material: Material = Material.RABBIT_FOOT,
    amount: Int = 1,
    contentId: String? = null,
    maxStackSize: Int = 64,
    itemModel: NamespacedKey? = null,
): ItemStack {
    val item = mock(ItemStack::class.java)
    val meta = mock(ItemMeta::class.java)
    val pdc = mock(PersistentDataContainer::class.java)
    `when`(item.type).thenReturn(material)
    `when`(item.amount).thenReturn(amount)
    `when`(item.maxStackSize).thenReturn(maxStackSize)
    `when`(item.itemMeta).thenReturn(meta)
    `when`(meta.itemModel).thenReturn(itemModel)
    `when`(meta.persistentDataContainer).thenReturn(pdc)
    `when`(pdc.has(NamespacedKey("magicspells", "magicitem"), PersistentDataType.STRING)).thenReturn(id != null)
    `when`(pdc.has(NamespacedKey("magicspells", "magicspellpermanentdata_crate_item"),
        PersistentDataType.STRING)).thenReturn(contentId != null)
    `when`(pdc.get(NamespacedKey("magicspells", "magicitem"), PersistentDataType.STRING)).thenReturn(id)
    `when`(pdc.get(NamespacedKey("magicspells", "magicspellpermanentdata_crate_item"),
        PersistentDataType.STRING)).thenReturn(contentId)
    return item
}

class CrateDefinitionTest {
    private val crateModel = NamespacedKey("custompack", "containers/raw-pearl-special")
    private val crate = magicItem("item-crate-pearlRawWIP", Material.SHULKER_BOX,
        contentId = "item-pearl-raw", itemModel = crateModel)
    private val registry = mutableMapOf(
        "item-crate-pearlRawWIP" to crate,
        "item-pearl-raw" to magicItem(maxStackSize = 16),
    )

    @Test
    fun `crate resolves its content stack limit and packing spell while preserving its item model`() {
        val definition = assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
        assertEquals("item-pearl-raw", definition.contentItemId)
        assertEquals(crateModel, definition.itemModel)
        assertEquals(16, definition.maxStackSize)
        assertEquals("item-crate-pearlRaw-success", definition.packingSpell)
    }

    @Test
    fun `only a trailing WIP is removed from the packing spell`() {
        val definition = assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
        assertEquals("item-crate-pearlRaw-success", definition.copy(crateItemId = "item-crate-pearlRaw").packingSpell)
        assertEquals("item-crate-WIP-pearl-success", definition.copy(crateItemId = "item-crate-WIP-pearl").packingSpell)
    }

    @Test
    fun `colored plain malformed and unregistered crates cannot resolve`() {
        assertNull(CrateDefinitions.resolve(null, registry::get).definitionOrNull())
        assertNull(CrateDefinitions.resolve(magicItem(material = Material.SHULKER_BOX), registry::get).definitionOrNull())
        assertNull(CrateDefinitions.resolve(magicItem(null, Material.SHULKER_BOX, contentId = "item-pearl-raw"), registry::get).definitionOrNull())
        assertNull(CrateDefinitions.resolve(magicItem("item-crate-pearlRawWIP", Material.ORANGE_SHULKER_BOX,
            contentId = "item-pearl-raw"), registry::get).definitionOrNull())
        registry.remove("item-pearl-raw")
        assertNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
        registry["item-pearl-raw"] = magicItem()
        registry.remove("item-crate-pearlRawWIP")
        assertNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
    }

    @Test
    fun `configuration changes are picked up on the next resolution`() {
        assertEquals(16, CrateDefinitions.resolve(crate, registry::get).definitionOrNull()?.maxStackSize)
        registry["item-pearl-raw"] = magicItem(maxStackSize = 32)
        assertEquals(32, CrateDefinitions.resolve(crate, registry::get).definitionOrNull()?.maxStackSize)
    }

    @Test
    fun `packing supports unstackable standard and extended stack limits`() {
        for (limit in listOf(1, 16, 64, 99)) {
            registry["item-pearl-raw"] = magicItem(maxStackSize = limit)
            val definition = assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
            assertTrue(definition.isFull(Array(9) { magicItem(amount = limit) }), "Stack limit $limit")
        }
    }

    @Test
    fun `content ids do not need to be valid resource model paths`() {
        val item = magicItem("item-crate-pearlRawWIP", Material.SHULKER_BOX,
            contentId = "item-Pearl", itemModel = crateModel)
        registry["item-Pearl"] = magicItem()
        assertEquals(crateModel, CrateDefinitions.resolve(item, registry::get).definitionOrNull()?.itemModel)
    }

    @Test
    fun `the actual crate item model takes precedence over registry templates`() {
        registry["item-crate-pearlRawWIP"] = magicItem(itemModel = NamespacedKey("templates", "crate"))
        registry["item-pearl-raw"] = magicItem(itemModel = NamespacedKey("templates", "pearl"))
        assertEquals(crateModel, CrateDefinitions.resolve(crate, registry::get).definitionOrNull()?.itemModel)
    }

    @Test
    fun `missing item model fails without falling back to the registered crate template`() {
        val item = magicItem("item-crate-pearlRawWIP", Material.SHULKER_BOX, contentId = "item-pearl-raw")
        assertEquals("Crate item 'item-crate-pearlRawWIP' has no explicit item model.", failure(item))
    }

    @Test
    fun `content matching uses only exact MagicItem identity`() {
        val definition = assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
        assertTrue(definition.accepts(magicItem(material = Material.JIGSAW)))
        assertFalse(definition.accepts(magicItem("item-pearl-fine")))
        assertFalse(definition.accepts(magicItem(null)))
        assertFalse(definition.accepts(magicItem("ITEM-PEARL-RAW")))
        assertFalse(definition.accepts(null))
    }

    @Test
    fun `packing requires exactly nine stacks at the configured limit`() {
        val definition = assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull())
        val contents = arrayOfNulls<ItemStack>(27)
        for (slot in CrateGui.storageSlots) contents[slot] = magicItem(amount = 16)
        assertTrue(definition.isFull(contents))
        contents[3] = magicItem(amount = 99)
        assertFalse(definition.isFull(contents))
        contents[3] = magicItem(amount = 15)
        assertFalse(definition.isFull(contents))
        contents[3] = magicItem("item-pearl-fine", amount = 16)
        assertFalse(definition.isFull(contents))
        contents[3] = null
        assertFalse(definition.isFull(contents))
        contents[3] = magicItem(amount = 16)
        contents[0] = magicItem(amount = 16)
        assertFalse(definition.isFull(contents))
    }

    private fun failure(item: ItemStack?): String =
        assertIs<CrateResolution.Failure>(CrateDefinitions.resolve(item, registry::get)).reason

    @Test
    fun `resolution reports the failed metadata or material requirement`() {
        assertEquals("No crate item was provided.", failure(null))
        assertEquals("Expected an uncolored SHULKER_BOX, got ORANGE_SHULKER_BOX.",
            failure(magicItem(material = Material.ORANGE_SHULKER_BOX)))
        assertEquals("Missing, blank, or non-string PDC 'magicspells:magicitem'.",
            failure(magicItem(null, Material.SHULKER_BOX)))
        assertEquals("Missing, blank, or non-string PDC 'magicspells:magicspellpermanentdata_crate_item'.",
            failure(magicItem("item-crate-pearlRawWIP", Material.SHULKER_BOX, contentId = " ")))
    }

    @Test
    fun `wrong PDC types report a failure without attempting to read a string`() {
        val data = crate.itemMeta.persistentDataContainer
        val key = NamespacedKey("magicspells", "magicspellpermanentdata_crate_item")
        `when`(data.has(key, PersistentDataType.STRING)).thenReturn(false)
        assertEquals("Missing, blank, or non-string PDC '$key'.", failure(crate))
        verify(data, never()).get(key, PersistentDataType.STRING)
    }

    @Test
    fun `resolution identifies missing registry entries and invalid configuration`() {
        registry.remove("item-pearl-raw")
        assertEquals("Contained MagicItem 'item-pearl-raw' is not registered.", failure(crate))
        registry["item-pearl-raw"] = magicItem(maxStackSize = 0)
        assertEquals("Contained MagicItem 'item-pearl-raw' has invalid max stack size 0.", failure(crate))
        registry.remove("item-crate-pearlRawWIP")
        assertEquals("Crate MagicItem 'item-crate-pearlRawWIP' is not registered.", failure(crate))
    }

    @Test
    fun `disabled MagicSpells has its own failure reason`() {
        val plugins = mock(PluginManager::class.java)
        mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<PluginManager> { Bukkit.getPluginManager() }.thenReturn(plugins)
            assertEquals("MagicSpells is not enabled.",
                assertIs<CrateResolution.Failure>(CrateDefinitions.resolve(crate)).reason)
        }
    }

    @Test
    fun `only the context player with debug permission receives failure chat`() {
        val player = mock(Player::class.java)
        val resolution = CrateDefinitions.resolve(null, registry::get)
        assertNull(resolution.definitionOrNull())
        assertNull(resolution.definitionOrNull(player))
        verify(player, never()).sendMessage(any(Component::class.java))

        `when`(player.hasPermission("dipp.debug")).thenReturn(true)
        assertNull(resolution.definitionOrNull(player))
        verify(player).sendMessage(Component.text("Crate resolution failed: No crate item was provided.", NamedTextColor.GRAY))
    }

    @Test
    fun `successful resolution is silent for debug players`() {
        val player = mock(Player::class.java)
        `when`(player.hasPermission("dipp.debug")).thenReturn(true)
        assertNotNull(CrateDefinitions.resolve(crate, registry::get).definitionOrNull(player))
        verify(player, never()).sendMessage(any(Component::class.java))
    }
}
