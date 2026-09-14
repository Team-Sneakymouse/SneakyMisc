package com.danidipp.sneakymisc.crates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class CrateMigrationTest {
    private val canonicalId = "item-crate-spellThreadWIP"
    private val contentId = "item-spell-thread"
    private val model = NamespacedKey("lom", "crates/spell-thread")

    private data class Box(val item: ItemStack, val meta: BlockStateMeta, val state: ShulkerBox, val inventory: Inventory)

    private fun box(
        id: String? = canonicalId,
        material: Material = Material.SHULKER_BOX,
        contents: Array<ItemStack?> = arrayOfNulls(27),
        amount: Int = 1,
        model: NamespacedKey? = this.model,
    ): Box {
        val item = magicItem(id, material, amount, contentId, itemModel = model)
        val data = item.itemMeta.persistentDataContainer
        val meta = mock(BlockStateMeta::class.java)
        val state = mock(ShulkerBox::class.java)
        val inventory = mock(Inventory::class.java)
        `when`(item.itemMeta).thenReturn(meta)
        `when`(meta.persistentDataContainer).thenReturn(data)
        `when`(meta.itemModel).thenReturn(model)
        `when`(meta.blockState).thenReturn(state)
        `when`(state.inventory).thenReturn(inventory)
        `when`(inventory.size).thenReturn(contents.size)
        `when`(inventory.contents).thenReturn(contents)
        return Box(item, meta, state, inventory)
    }

    private val template = box()
    private val replacement = box()
    private val registry = mutableMapOf(canonicalId to template.item, contentId to magicItem(contentId))

    init {
        `when`(template.item.clone()).thenReturn(replacement.item)
    }

    private fun prepare(item: ItemStack) = CrateMigration.prepare(item, registry::get) { registry.keys }

    @Test
    fun `case insensitive migration replaces the crate while preserving every content slot and count`() {
        val storedItem = magicItem(contentId, amount = 42)
        val storedCopy = magicItem(contentId, amount = 42)
        `when`(storedItem.clone()).thenReturn(storedCopy)
        val foreignItem = magicItem("legacy-other-item", Material.JIGSAW, amount = 3)
        val foreignCopy = magicItem("legacy-other-item", Material.JIGSAW, amount = 3)
        `when`(foreignItem.clone()).thenReturn(foreignCopy)
        val contents = arrayOfNulls<ItemStack>(27).apply { this[3] = storedItem; this[26] = foreignItem }
        val original = box("magicitem:item-crate-spellthreadwip", Material.LIGHT_BLUE_SHULKER_BOX, contents, amount = 2)

        val result = prepare(original.item)

        assertSame(replacement.item, result.item)
        assertNotSame(template.item, result.item)
        val definition = assertIs<CrateResolution.Success>(result.resolution).definition
        assertEquals(canonicalId, definition.crateItemId)
        assertEquals(model, definition.itemModel)
        verify(replacement.item).setAmount(2)
        val transferred = ArgumentCaptor.forClass(Array<ItemStack?>::class.java)
        verify(replacement.inventory).setContents(transferred.capture())
        assertEquals(27, transferred.value.size)
        assertSame(storedCopy, transferred.value[3])
        assertSame(foreignCopy, transferred.value[26])
        assertEquals(25, transferred.value.count { it == null })
        verify(replacement.meta).setBlockState(replacement.state)
        verify(replacement.item).setItemMeta(replacement.meta)
        verify(original.item, never()).setItemMeta(any())
        verify(original.inventory, never()).setContents(any())
        verify(template.item, never()).setItemMeta(any())
        verify(template.inventory, never()).setContents(any())
    }

    @Test
    fun `all shulker colors can migrate with an unprefixed case insensitive id`() {
        val materials = Material.entries.filter { it == Material.SHULKER_BOX || it.name.endsWith("_SHULKER_BOX") }
        for (material in materials) {
            val original = box(canonicalId.lowercase(), material)
            assertSame(replacement.item, prepare(original.item).item, material.name)
        }
    }

    @Test
    fun `an already valid crate is not replaced or searched case insensitively`() {
        val customModel = NamespacedKey("custom", "different-crate")
        val valid = box(model = customModel)
        val result = CrateMigration.prepare(valid.item, registry::get) { error("Must not search for a valid crate") }
        assertSame(valid.item, result.item)
        assertEquals(customModel, assertIs<CrateResolution.Success>(result.resolution).definition.itemModel)
        verify(template.item, never()).clone()
    }

    @Test
    fun `a correctly cased but outdated crate can migrate`() {
        val outdated = box(model = null)
        assertSame(replacement.item, prepare(outdated.item).item)
    }

    @Test
    fun `non shulker items and shulkers without ids are unchanged`() {
        for (item in listOf(magicItem(canonicalId.lowercase()), box(null, Material.ORANGE_SHULKER_BOX).item)) {
            val result = CrateMigration.prepare(item, registry::get) { error("Not a migration candidate") }
            assertSame(item, result.item)
            assertIs<CrateResolution.Failure>(result.resolution)
        }
        verify(template.item, never()).clone()
    }

    @Test
    fun `missing or ambiguous registry matches leave the original untouched`() {
        val unknown = box("item-crate-unknown", Material.ORANGE_SHULKER_BOX)
        val missing = prepare(unknown.item)
        assertSame(unknown.item, missing.item)
        assertTrue(assertIs<CrateResolution.Failure>(missing.resolution).reason.contains("No registered MagicItem"))

        registry[canonicalId.lowercase()] = template.item
        val original = box(canonicalId.lowercase(), Material.ORANGE_SHULKER_BOX)
        val ambiguous = prepare(original.item)
        assertSame(original.item, ambiguous.item)
        assertTrue(assertIs<CrateResolution.Failure>(ambiguous.resolution).reason.contains("Multiple registered MagicItems"))
        verify(template.item, never()).clone()
    }

    @Test
    fun `an invalid matching template cannot migrate`() {
        val invalid = box(model = null)
        registry[canonicalId] = invalid.item
        val original = box(canonicalId.lowercase(), Material.ORANGE_SHULKER_BOX)
        val result = prepare(original.item)
        assertSame(original.item, result.item)
        assertTrue(assertIs<CrateResolution.Failure>(result.resolution).reason.contains("has no explicit item model"))
        verify(invalid.item, never()).clone()
    }

    @Test
    fun `missing source inventory or mismatched inventory size cannot lose contents`() {
        val missing = magicItem(canonicalId.lowercase(), Material.ORANGE_SHULKER_BOX)
        assertSame(missing, prepare(missing).item)
        verify(template.item, never()).clone()
        val wrongSize = box(canonicalId.lowercase(), Material.ORANGE_SHULKER_BOX, arrayOfNulls(36))
        val result = prepare(wrongSize.item)
        assertSame(wrongSize.item, result.item)
        assertTrue(assertIs<CrateResolution.Failure>(result.resolution).reason.contains("inventory sizes differ"))
        verify(replacement.inventory, never()).setContents(any())
    }

    @Test
    fun `backpack identity follows the original item`() {
        val original = box(canonicalId.lowercase(), Material.ORANGE_SHULKER_BOX)
        val data = original.meta.persistentDataContainer
        `when`(data.has(CrateMigration.backpackKey, PersistentDataType.INTEGER)).thenReturn(true)
        `when`(data.get(CrateMigration.backpackKey, PersistentDataType.INTEGER)).thenReturn(123)
        assertSame(replacement.item, prepare(original.item).item)
        verify(replacement.meta.persistentDataContainer).remove(CrateMigration.backpackKey)
        verify(replacement.meta.persistentDataContainer).set(CrateMigration.backpackKey, PersistentDataType.INTEGER, 123)
        verify(original.meta.persistentDataContainer, never()).remove(any())
    }
}
