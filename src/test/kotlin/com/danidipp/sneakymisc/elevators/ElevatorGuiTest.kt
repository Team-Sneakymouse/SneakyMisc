package com.danidipp.sneakymisc.elevators

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.mockito.Mockito.*
import kotlin.test.*

class ElevatorGuiTest {
    private fun scenario(action: (Fixture) -> Unit) {
        val module = mock(ElevatorsModule::class.java)
        val plugin = mock(Plugin::class.java)
        @Suppress("UNCHECKED_CAST")
        val travel = mock(ElevatorTravelCoordinator::class.java) as ElevatorTravelCoordinator<ElevatorFloor, ElevatorTransitCab>
        `when`(module.plugin).thenReturn(plugin)
        `when`(module.travel).thenReturn(travel)
        `when`(module.guis).thenReturn(mutableSetOf())
        val scheduler = mock(BukkitScheduler::class.java)
        val inventory = mock(Inventory::class.java)
        val view = mock(InventoryView::class.java)
        val player = mock(Player::class.java)
        val world = mock(World::class.java)
        val floor = ElevatorFloor("tower-2", ElevatorCab.at(Location(world, .5, 60.0, .5)), Elevator("tower", "stone"))
        `when`(player.location).thenReturn(Location(world, .5, 60.0, .5))
        `when`(player.openInventory).thenReturn(view)
        `when`(view.topInventory).thenReturn(inventory)
        `when`(view.player).thenReturn(player)
        `when`(inventory.size).thenReturn(54)
        val queued = mutableListOf<Runnable>()
        `when`(scheduler.runTask(eq(plugin), any(Runnable::class.java))).thenAnswer {
            queued.add(it.getArgument(1)); mock(BukkitTask::class.java)
        }
        `when`(scheduler.runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())).thenAnswer {
            queued.add(it.getArgument(1)); mock(BukkitTask::class.java)
        }
        mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            bukkit.`when`<Inventory> { Bukkit.createInventory(any(InventoryHolder::class.java), eq(54), any(Component::class.java)) }.thenReturn(inventory)
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java))
            }.use {
                val gui = ElevatorGUI(module, floor)
                `when`(inventory.holder).thenReturn(gui)
                val listener = ElevatorGUI.listener(module)
                action(Fixture(module, floor, gui, player, inventory, view, listener, queued))
            }
        }
    }

    private class Fixture(
        val module: ElevatorsModule,
        val floor: ElevatorFloor,
        val gui: ElevatorGUI,
        val player: Player,
        val inventory: Inventory,
        val view: InventoryView,
        val listener: org.bukkit.event.Listener,
        val queued: MutableList<Runnable>,
    ) {
        fun click(slot: Int) {
            `when`(view.getInventory(slot)).thenReturn(inventory)
            `when`(view.convertSlot(slot)).thenReturn(slot)
            val event = InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL)
            listener.javaClass.getMethod("onClick", InventoryClickEvent::class.java).invoke(listener, event)
            assertTrue(event.isCancelled)
        }
        fun close() {
            listener.javaClass.getMethod("onClose", InventoryCloseEvent::class.java).invoke(listener, InventoryCloseEvent(view))
        }
        fun next() = queued.removeAt(0).run()
    }

    @Test fun `two digit input evaluates automatically after event and closes on success`() = scenario { f ->
        f.click(40) // 0
        assertTrue(f.queued.isEmpty())
        f.click(22) // 5
        verify(f.module, never()).select(f.player, f.floor, "5")
        f.next()
        verify(f.module).select(f.player, f.floor, "5")
        verify(f.player).closeInventory()
    }

    @Test fun `arrival or manual close invalidates an already queued selection`() = scenario { f ->
        f.click(40)
        f.click(22)
        f.close()
        f.next()
        verify(f.module, never()).select(f.player, f.floor, "5")
    }

    @Test fun `clear requires two new digits and dedicated ground needs only one click`() = scenario { f ->
        f.click(30) // 7
        f.click(41) // clear
        f.click(40) // 0 is tens again
        assertTrue(f.queued.isEmpty())
        f.click(48) // G is immediate
        f.next()
        verify(f.module).select(f.player, f.floor, "G")
    }

    @Test fun `invalid selection resets but its delayed error cannot revive a closed GUI`() = scenario { f ->
        `when`(f.module.select(f.player, f.floor, "B")).thenReturn("That floor does not exist.")
        f.click(39)
        f.next()
        verify(f.player).sendMessage("That floor does not exist.")
        f.close()
        f.next()
        f.click(12)
        assertTrue(f.queued.isEmpty())
        verify(f.module, times(1)).select(f.player, f.floor, "B")
    }
}
