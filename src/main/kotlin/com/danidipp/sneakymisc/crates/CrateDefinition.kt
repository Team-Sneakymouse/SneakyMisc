package com.danidipp.sneakymisc.crates

import com.nisovin.magicspells.util.magicitems.MagicItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class CrateDefinition(
    val crateItemId: String,
    val contentItemId: String,
    val itemModel: NamespacedKey,
    val maxStackSize: Int,
) {
    val packingSpell: String = "${crateItemId.removeSuffix("WIP")}-success"

    fun accepts(item: ItemStack?): Boolean =
        item != null && item.type != Material.AIR && CrateDefinitions.magicItemId(item) == contentItemId

    fun isFull(contents: Array<ItemStack?>): Boolean {
        val stacks = contents.filterNotNull().filterNot { it.type == Material.AIR }
        return stacks.size == CrateGui.storageSlots.size &&
            stacks.all { accepts(it) && it.amount == maxStackSize }
    }
}

sealed interface CrateResolution {
    data class Success(val definition: CrateDefinition) : CrateResolution
    data class Failure(val reason: String) : CrateResolution

    fun definitionOrNull(player: Player? = null): CrateDefinition? = when (this) {
        is Success -> definition
        is Failure -> {
            if (player?.hasPermission("dipp.debug") == true) {
                player.sendMessage(Component.text("Crate resolution failed: $reason", NamedTextColor.GRAY))
            }
            null
        }
    }
}

object CrateDefinitions {
    private val magicItemKey = NamespacedKey("magicspells", "magicitem")
    private val contentItemKey = NamespacedKey("magicspells", "magicspellpermanentdata_crate_item")

    fun magicItemId(item: ItemStack): String? = stringData(item, magicItemKey)

    private fun stringData(item: ItemStack, key: NamespacedKey): String? {
        val data = item.itemMeta?.persistentDataContainer ?: return null
        if (!data.has(key, PersistentDataType.STRING)) return null
        return data.get(key, PersistentDataType.STRING)
    }

    // Resolve from the current registry each time so MagicSpells reloads take effect.
    fun resolve(item: ItemStack?, lookup: ((String) -> ItemStack?)? = null): CrateResolution {
        if (item == null || item.type == Material.AIR) return CrateResolution.Failure("No crate item was provided.")
        if (item.type != Material.SHULKER_BOX) {
            return CrateResolution.Failure("Expected an uncolored SHULKER_BOX, got ${item.type}.")
        }
        val crateId = magicItemId(item)?.takeIf { it.isNotBlank() }
            ?: return CrateResolution.Failure("Missing, blank, or non-string PDC '$magicItemKey'.")
        val contentId = stringData(item, contentItemKey)?.takeIf { it.isNotBlank() }
            ?: return CrateResolution.Failure("Missing, blank, or non-string PDC '$contentItemKey'.")
        val findItem = lookup ?: run {
            if (!Bukkit.getPluginManager().isPluginEnabled("MagicSpells")) {
                return CrateResolution.Failure("MagicSpells is not enabled.")
            }
            MagicItems::getItemByInternalName
        }
        if (findItem(crateId) == null) return CrateResolution.Failure("Crate MagicItem '$crateId' is not registered.")
        val content = findItem(contentId)
            ?: return CrateResolution.Failure("Contained MagicItem '$contentId' is not registered.")
        val model = item.itemMeta?.itemModel
            ?: return CrateResolution.Failure("Crate item '$crateId' has no explicit item model.")
        val maxStackSize = content.maxStackSize
        if (maxStackSize <= 0) {
            return CrateResolution.Failure("Contained MagicItem '$contentId' has invalid max stack size $maxStackSize.")
        }
        return CrateResolution.Success(CrateDefinition(crateId, contentId, model, maxStackSize))
    }
}
