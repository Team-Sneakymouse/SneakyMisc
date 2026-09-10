package com.danidipp.sneakymisc.crates

import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.EventExecutor

object CrateGui {
    val fillerItem: ItemStack = CrateUtils.makeItem(Material.RABBIT_FOOT, 7)

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
            val crate = Crate.get(backpack) ?: Crate(backpack)
            if (crate.isValid()) player.openInventory(crate.inventory)
        }.onFailure {
            SneakyMisc.getInstance().logger.warning("Could not open CMI backpack as a crate: ${it.message}")
        }
    }

    val listener: Listener = object : Listener {
        @EventHandler
        fun onInventoryDrag(event: InventoryDragEvent) {
            val crate = event.inventory.holder as? Crate ?: return
            if (!crate.isValid()) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            val crateLabel = crate.label
            val labelType = crateLabel?.type
            val labelModelData = crateLabel?.itemMeta?.customModelData ?: 0
            for ((rawSlot, item) in event.newItems) {
                if (event.view.getInventory(rawSlot) != event.view.topInventory) continue
                val itemModelData = item.itemMeta.takeIf { it.hasCustomModelData() }?.customModelData ?: 0
                if (item.type != labelType || itemModelData != labelModelData) {
                    event.isCancelled = true
                    return
                }
            }
        }

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val crate = event.inventory.holder as? Crate ?: return
            if (!crate.isValid()) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            val clickedItem = event.currentItem
            val cursorItem = event.cursor
            if ((clickedItem == null || clickedItem.type == Material.AIR) &&
                (cursorItem.type == Material.AIR) &&
                event.hotbarButton == -1
            ) {
                return
            }

            val crateLabel = crate.label
            val labelType = crateLabel?.type
            val labelModelData = crateLabel?.itemMeta?.customModelData ?: 0

            val itemMaterial = clickedItem?.type ?: Material.AIR
            val itemModelData = clickedItem?.itemMeta?.takeIf { it.hasCustomModelData() }?.customModelData ?: 0
            val cursorMaterial = cursorItem.type
            val cursorModelData = cursorItem.itemMeta.takeIf { it.hasCustomModelData() }?.customModelData ?: 0

            val swapItem = when (event.click) {
                ClickType.NUMBER_KEY -> event.whoClicked.inventory.getItem(event.hotbarButton)
                ClickType.SWAP_OFFHAND -> event.whoClicked.inventory.itemInOffHand
                else -> null
            }
            val swapMaterial = swapItem?.type ?: Material.AIR
            val swapModelData = swapItem?.itemMeta?.takeIf { it.hasCustomModelData() }?.customModelData ?: 0

            val pickup = itemMaterial != Material.AIR
            val drop = cursorMaterial != Material.AIR
            val swap = swapMaterial != Material.AIR

            if (pickup) {
                if (event.clickedInventory == event.view.topInventory) {
                    if (clickedItem?.isSimilar(fillerItem) == true || itemMaterial == Material.JIGSAW) {
                        event.isCancelled = true
                        return
                    }

                    val clickedInventory = event.clickedInventory
                    Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable {
                        if (clickedInventory != null) {
                            setFiller(clickedInventory, event.slot, crate.shulkerBox?.type ?: Material.SHULKER_BOX)
                        }
                    })
                } else if ((!drop || !cursorItem.isSimilar(clickedItem)) &&
                    (itemMaterial != labelType || itemModelData != labelModelData)
                ) {
                    event.isCancelled = true
                    return
                }
            }

            if (drop && event.clickedInventory == event.view.topInventory &&
                (cursorMaterial != labelType || cursorModelData != labelModelData)
            ) {
                event.isCancelled = true
            } else if (swap && (swapMaterial != labelType || swapModelData != labelModelData)) {
                event.isCancelled = true
            }
        }

        @EventHandler
        fun onInventoryOpen(event: InventoryOpenEvent) {
            val player = event.player as? Player ?: return
            val crate = event.inventory.holder as? Crate ?: return
            if (!crate.isValid()) {
                event.isCancelled = true
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable { event.view.close() })
                return
            }

            player.playSound(crate.location, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f)
            if (player.hasPermission(DEBUG_PERMISSION)) {
                val crateLabel = crate.label
                val labelType = crateLabel?.type
                val labelModelData = crateLabel?.itemMeta?.customModelData ?: 0
                player.sendMessage(
                    Component.text(
                        "Crate ${crate.inventory.hashCode()}: $labelType ($labelModelData)",
                        NamedTextColor.GRAY,
                    ),
                )
            }
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val player = event.player as? Player ?: return
            val crate = event.inventory.holder as? Crate ?: return
            if (crate.isValid()) {
                player.playSound(crate.location, Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.0f)
                crate.save()
            } else {
                player.sendActionBar(Component.text("Inconsistent crate state", NamedTextColor.RED))
            }
        }
    }

    private fun getShulkerBackground(shulkerMaterial: Material): ItemStack? = when (shulkerMaterial) {
        Material.SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3204)
        Material.ORANGE_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3207)
        Material.LIGHT_BLUE_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3209)
        Material.YELLOW_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3210)
        Material.LIME_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3206)
        Material.PINK_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3205)
        Material.LIGHT_GRAY_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3203)
        Material.BROWN_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3208)
        Material.RED_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3201)
        Material.BLACK_SHULKER_BOX -> CrateUtils.makeItem(Material.JIGSAW, 3202)
        else -> null
    }

    fun getFiller(slot: Int, shulkerMaterial: Material): ItemStack? = when (slot) {
        3, 4, 5, 12, 13, 14, 21, 22, 23 -> null
        8 -> getShulkerBackground(shulkerMaterial)
        else -> fillerItem
    }

    private fun setFiller(inventory: Inventory, slot: Int, shulkerMaterial: Material) {
        val item = inventory.getItem(slot)
        if (item == null || item.type == Material.AIR) {
            inventory.setItem(slot, getFiller(slot, shulkerMaterial))
        }
    }

    private const val DEBUG_PERMISSION = "dipp.debug"
}
