package com.danidipp.sneakymisc.crates

import com.nisovin.magicspells.util.magicitems.MagicItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType

object CrateMigration {
    val backpackKey = NamespacedKey("cmilib", "cmibackpack")

    data class Prepared(val item: ItemStack, val resolution: CrateResolution)

    // Returns a fresh replacement only after migration succeeds. Neither input nor registry items are mutated.
    fun prepare(
        item: ItemStack,
        lookup: ((String) -> ItemStack?)? = null,
        itemIds: (() -> Collection<String>)? = null,
    ): Prepared {
        val original = CrateDefinitions.resolve(item, lookup)
        if (original is CrateResolution.Success) return Prepared(item, original)
        if (item.type != Material.SHULKER_BOX && !item.type.name.endsWith("_SHULKER_BOX")) {
            return Prepared(item, original)
        }
        val storedId = CrateDefinitions.magicItemId(item)?.takeIf { it.isNotBlank() }
            ?: return Prepared(item, original)
        val id = if (storedId.startsWith("magicitem:", ignoreCase = true)) storedId.substringAfter(':') else storedId
        val findItem = lookup ?: run {
            if (!Bukkit.getPluginManager().isPluginEnabled("MagicSpells")) return Prepared(item, original)
            MagicItems::getItemByInternalName
        }
        fun failure(reason: String) = Prepared(item, CrateResolution.Failure("Crate migration failed: $reason"))

        val matches = (itemIds?.invoke() ?: MagicItems.getMagicItemKeys()).filter { it.equals(id, ignoreCase = true) }
        if (matches.isEmpty()) return failure("No registered MagicItem matches '$storedId' ignoring case.")
        if (matches.size != 1) return failure("Multiple registered MagicItems match '$storedId': ${matches.sorted().joinToString()}.")
        val matchedId = matches.single()
        val template = findItem(matchedId) ?: return failure("MagicItem '$matchedId' is no longer registered.")
        val resolved = CrateDefinitions.resolve(template, findItem)
        if (resolved is CrateResolution.Failure) return failure("MagicItem '$matchedId' is not a valid crate: ${resolved.reason}")

        val sourceMeta = item.itemMeta as? BlockStateMeta ?: return failure("The original item has no shulker inventory.")
        val source = sourceMeta.blockState as? ShulkerBox ?: return failure("The original item has no shulker inventory.")
        val replacement = template.clone()
        val targetMeta = replacement.itemMeta as? BlockStateMeta ?: return failure("MagicItem '$matchedId' has no shulker inventory.")
        val target = targetMeta.blockState as? ShulkerBox ?: return failure("MagicItem '$matchedId' has no shulker inventory.")
        val contents = source.inventory.contents
        if (contents.size != target.inventory.size) return failure("Source and target inventory sizes differ.")
        target.inventory.contents = contents.map { it?.clone() }.toTypedArray()
        targetMeta.blockState = target

        // The CMI source ID links an existing backpack to its item, but other metadata comes from the template.
        targetMeta.persistentDataContainer.remove(backpackKey)
        if (sourceMeta.persistentDataContainer.has(backpackKey, PersistentDataType.INTEGER)) {
            val backpackId = sourceMeta.persistentDataContainer.get(backpackKey, PersistentDataType.INTEGER)
            if (backpackId != null) targetMeta.persistentDataContainer.set(backpackKey, PersistentDataType.INTEGER, backpackId)
        }
        replacement.itemMeta = targetMeta
        replacement.amount = item.amount
        return Prepared(replacement, resolved)
    }
}
