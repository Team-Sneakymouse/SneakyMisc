package com.danidipp.sneakymisc.elevators

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class ElevatorGUI(private val module: ElevatorsModule, val floor: ElevatorFloor) : InventoryHolder {
    private val contents = Bukkit.createInventory(this, 54, Component.text("Elevator ${floor.elevator.name}"))
    private val input = ElevatorFloorInput()
    private var valid = true
    internal var pending = false
        private set
    private var closing = false
    override fun getInventory(): Inventory = contents

    init {
        contents.setItem(0, getNumber(3046))
        renderInput()
    }

    internal fun invalidate() {
        valid = false
        module.guis.remove(this)
    }

    private fun renderInput() {
        contents.setItem(4, input.tens?.let { getNumber(6000 + it * 10) })
        contents.setItem(5, input.ones?.let { getNumber(6000 + it) })
    }

    private fun click(player: Player, slot: Int) {
        if (!valid || pending) return
        if (!floor.cab.contains(player.location) || module.travel.isDeparting(floor)) {
            invalidate()
            Bukkit.getScheduler().runTask(module.plugin, Runnable { if (player.openInventory.topInventory.holder === this) player.closeInventory() })
            return
        }
        val label = when (slot) {
            12, 13, 14 -> input.digit(slot - 11)
            21, 22, 23 -> input.digit(slot - 17)
            30, 31, 32 -> input.digit(slot - 23)
            39 -> input.digit(0)
            40 -> "G"
            41 -> "B"
            else -> return
        }
        player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
        renderInput()
        if (label == null) return
        pending = true
        // Inventory mutations/teleports must run after InventoryClickEvent. Invalidation makes this cancellable.
        Bukkit.getScheduler().runTask(module.plugin, Runnable {
            if (!valid || player.openInventory.topInventory.holder !== this) return@Runnable
            val error = module.select(player, floor, label)
            if (error == null) {
                player.playSound(player, "lom:cute.get", SoundCategory.MASTER, .5f, 1f)
                closeLater()
            } else {
                player.sendMessage(error)
                player.playSound(player, "lom:fail_wrong", SoundCategory.MASTER, 1f, 1f)
                contents.setItem(4, getNumber(5998))
                contents.setItem(5, getNumber(5998))
                Bukkit.getScheduler().runTaskLater(module.plugin, Runnable {
                    if (valid) { input.reset(); pending = false; renderInput() }
                }, 15)
            }
        })
    }

    /** Locks the GUI right away but keeps it open briefly so the complete floor selection stays readable. */
    internal fun closeLater() {
        invalidate()
        if (closing) return
        closing = true
        Bukkit.getScheduler().runTaskLater(module.plugin, Runnable {
            for (viewer in contents.viewers.toList()) {
                if (viewer is Player && viewer.openInventory.topInventory.holder === this) viewer.closeInventory()
            }
        }, CONFIRM_DELAY_TICKS)
    }

    companion object {
        private const val CONFIRM_DELAY_TICKS = 20L

        fun getNumber(model: Int): ItemStack = ItemStack(Material.JIGSAW).apply {
            itemMeta = itemMeta.apply {
                setCustomModelData(model)
                isHideTooltip = true
            }
        }

        fun listener(module: ElevatorsModule) = object : Listener {
            @EventHandler
            fun onOpen(event: InventoryOpenEvent) {
                val gui = event.inventory.holder as? ElevatorGUI ?: return
                val player = event.player as? Player ?: return
                if (!gui.valid || !gui.floor.cab.contains(player.location) || module.travel.isDeparting(gui.floor)) event.isCancelled = true
                else module.guis.add(gui)
            }
            @EventHandler
            fun onClose(event: InventoryCloseEvent) { (event.inventory.holder as? ElevatorGUI)?.invalidate() }
            @EventHandler
            fun onDrag(event: InventoryDragEvent) { if (event.inventory.holder is ElevatorGUI) event.isCancelled = true }
            @EventHandler
            fun onClick(event: InventoryClickEvent) {
                val gui = event.inventory.holder as? ElevatorGUI ?: return
                event.isCancelled = true
                if (event.clickedInventory != event.inventory) return
                gui.click(event.whoClicked as? Player ?: return, event.slot)
            }
        }
    }
}
