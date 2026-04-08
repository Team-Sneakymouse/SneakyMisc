package com.danidipp.sneakymisc.lomarchive

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.DoubleChest
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.joml.Vector2f
import org.joml.Vector3d
import org.joml.Vector3f
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class LomArchiveModule(
    private val logger: Logger,
    private val archiveWorldName: String,
) : SneakyModule, Listener {
    companion object {
        val deps = listOf("SneakyCharacterManager", "MagicSpells")

        private val ARCHIVE_POSITION = Vector3d(186.5, 72.0, 847.5)
        private val SPAWN_ROTATION = Vector2f(-45.0f, 0.0f)
    }

    private val plugin = SneakyMisc.getInstance()
    private val magicItemKey = NamespacedKey.fromString("magicspells:magicitem")
        ?: throw IllegalStateException("Failed to create NamespacedKey for magic items")

    override val commands: List<SneakyMiscCommand> = emptyList()
    override val listeners: List<Listener> = listOf(this)

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.player.gameMode == GameMode.SPECTATOR) return // Ignore spectators
        val inventory = event.view.topInventory
        if (inventory.location?.world?.name != archiveWorldName) return // Ignore non-archive worlds

        val holder = inventory.holder
        if (holder !is BlockInventoryHolder && holder !is DoubleChest) return // Only target block inventories (chests, etc.)

        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot)
            if (item == null || item.type.isAir) continue
            if (!isMagicSpellsItem(item)) {
                inventory.setItem(slot, null)
            }
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (player.world.name != archiveWorldName) return
        if (isMagicSpellsItem(event.item.itemStack)) return

        event.isCancelled = true
        event.item.remove()
    }

    private fun isMagicSpellsItem(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(magicItemKey, PersistentDataType.STRING)
    }
}
