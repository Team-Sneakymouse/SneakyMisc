package com.danidipp.sneakymisc.crates

import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.EventExecutor

object CrateGui {
    val storageSlots = setOf(3, 4, 5, 12, 13, 14, 21, 22, 23)
    private val decorationKey = NamespacedKey("sneakymisc", "crate_decoration")
    val fillerItem: ItemStack by lazy {
        markDecoration(CrateUtils.makeItem(Material.RABBIT_FOOT, 7))
    }

    fun registerExternalEvents(plugin: SneakyMisc) {
        val eventClass = runCatching {
            Class.forName("com.Zrips.CMI.events.CMIBackpackOpenEvent").asSubclass(Event::class.java)
        }.getOrNull() ?: return

        Bukkit.getPluginManager().registerEvent(
            eventClass,
            listener,
            EventPriority.NORMAL,
            EventExecutor { _, event -> handleBackpackOpenEvent(eventClass, event) },
            plugin,
            false,
        )
    }

    private fun handleBackpackOpenEvent(eventClass: Class<out Event>, event: Event) {
        if (!eventClass.isInstance(event)) return

        runCatching {
            event.javaClass.getMethod("setCancelled", Boolean::class.javaPrimitiveType).invoke(event, true)
            val backpack = event.javaClass.getMethod("getShulkerbackpack").invoke(event) ?: return@runCatching
            val player = event.javaClass.getMethod("getPlayer").invoke(event) as? Player ?: return@runCatching
            val original = player.inventory.itemInMainHand
            val prepared = CrateMigration.prepare(original)
            if (prepared.resolution.definitionOrNull(player) == null) return@runCatching
            if (prepared.item !== original) {
                if (Crate.get(backpack)?.inventory?.viewers?.isNotEmpty() == true) {
                    CrateResolution.Failure("Close the crate before migrating it.").definitionOrNull(player)
                    return@runCatching
                }
                val source = backpack.javaClass.getMethod("getShulkerBox").invoke(backpack) as? ItemStack
                if (source != original) {
                    CrateResolution.Failure("The backpack source does not match the held item.").definitionOrNull(player)
                    return@runCatching
                }
                backpack.javaClass.getMethod("setShulkerBox", ItemStack::class.java).invoke(backpack, prepared.item)
                player.inventory.setItemInMainHand(prepared.item)
                Crate.forget(backpack)
            }
            val crate = Crate.get(backpack) ?: Crate(backpack)
            if (crate.isValid(player)) player.openInventory(crate.inventory)
        }.onFailure {
            SneakyMisc.getInstance().logger.warning("Could not open CMI backpack as a crate: ${it.message}")
        }
    }

    val listener: Listener = object : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onInventoryDrag(event: InventoryDragEvent) {
            val crate = event.inventory.holder as? Crate ?: return
            val definition = crate.definition
            if (!crate.isValid(event.whoClicked as? Player) || definition == null) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            for ((rawSlot, item) in event.newItems) {
                if (rawSlot >= event.view.topInventory.size) continue
                if (rawSlot !in storageSlots || !definition.accepts(item)) {
                    event.isCancelled = true
                    return
                }
            }
        }

        @EventHandler(ignoreCancelled = true)
        fun onInventoryClick(event: InventoryClickEvent) {
            val crate = event.inventory.holder as? Crate ?: return
            if (!crate.isValid(event.whoClicked as? Player)) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            val inCrate = event.clickedInventory == event.view.topInventory
            if (inCrate && (event.slot !in storageSlots || isDecoration(event.currentItem))) {
                event.isCancelled = true
                return
            }

            val incoming = when (event.action) {
                InventoryAction.MOVE_TO_OTHER_INVENTORY -> if (!inCrate) event.currentItem else null
                InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME,
                InventoryAction.SWAP_WITH_CURSOR -> if (inCrate) event.cursor else null
                InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD -> if (inCrate) {
                    if (event.click == ClickType.SWAP_OFFHAND) event.whoClicked.inventory.itemInOffHand
                    else event.hotbarButton.takeIf { it >= 0 }?.let(event.whoClicked.inventory::getItem)
                } else null
                InventoryAction.UNKNOWN -> {
                    event.isCancelled = true
                    return
                }
                else -> null
            }
            if (incoming != null && incoming.type != Material.AIR && crate.definition?.accepts(incoming) != true) {
                event.isCancelled = true
            }
        }

        @EventHandler(ignoreCancelled = true)
        fun onInventoryOpen(event: InventoryOpenEvent) {
            if (event.inventory.holder is org.bukkit.block.ShulkerBox) {
                event.isCancelled = true
                return
            }
            val player = event.player as? Player ?: return
            val crate = event.inventory.holder as? Crate ?: return
            if (!crate.isValid(player)) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            crate.definition?.let { event.inventory.maxStackSize = it.maxStackSize }
            player.playSound(crate.location, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f)
            if (player.hasPermission(DEBUG_PERMISSION)) {
                player.sendMessage(Component.text(
                    "Crate ${crate.inventory.hashCode()}: ${crate.definition?.contentItemId}",
                    NamedTextColor.GRAY,
                ))
            }
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val player = event.player as? Player ?: return
            val crate = event.inventory.holder as? Crate ?: return
            // A reload can remove the definition while the GUI is open. Still persist its contents.
            crate.save()
            if (crate.isValid(player)) {
                player.playSound(crate.location, Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f)
            } else {
                player.sendActionBar(Component.text("Inconsistent crate state", NamedTextColor.RED))
            }
        }
    }

    fun isDecoration(item: ItemStack?): Boolean =
        item?.itemMeta?.persistentDataContainer?.has(decorationKey, PersistentDataType.BYTE) == true

    private fun markDecoration(item: ItemStack): ItemStack {
        val meta = item.itemMeta
        meta.persistentDataContainer.set(decorationKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    fun getFiller(slot: Int, definition: CrateDefinition?): ItemStack? = when (slot) {
        in storageSlots -> null
        0 -> definition?.let {
            val item = ItemStack(Material.JIGSAW)
            val meta = item.itemMeta
            meta.itemModel = it.itemModel
            val modelData = meta.customModelDataComponent
            modelData.strings = listOf("background")
            meta.setCustomModelDataComponent(modelData)
            meta.setHideTooltip(true)
            item.itemMeta = meta
            markDecoration(item)
        } ?: fillerItem
        else -> fillerItem
    }

    private const val DEBUG_PERMISSION = "dipp.debug"
}
