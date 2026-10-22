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
        f.click(39) // 0
        assertTrue(f.queued.isEmpty())
        f.click(22) // 5
        verify(f.module, never()).select(f.player, f.floor, "5")
        f.next()
        verify(f.module).select(f.player, f.floor, "5")
        verify(f.player).closeInventory()
    }

    @Test fun `arrival or manual close invalidates an already queued selection`() = scenario { f ->
        f.click(39)
        f.click(22)
        f.close()
        f.next()
        verify(f.module, never()).select(f.player, f.floor, "5")
    }

    @Test fun `ground and basement use the new bottom row and ignore old ground slot`() {
        scenario { f ->
            f.click(48) // Previous G slot is now inactive.
            assertTrue(f.queued.isEmpty())
            f.click(30) // Partial numeric input must not prevent G selection.
            f.click(40)
            f.next()
            verify(f.module).select(f.player, f.floor, "G")
        }
        scenario { f ->
            f.click(41) // Previous clear slot is now B.
            f.next()
            verify(f.module).select(f.player, f.floor, "B")
        }
    }

    @Test fun `background stays in slot zero with no button models names or tooltips`() = scenario { f ->
        val placed = mockingDetails(f.inventory).invocations.filter { it.method.name == "setItem" }
        assertEquals(setOf(0, 4, 5), placed.map { it.arguments[0] }.toSet())
        val background = placed.single { it.arguments[0] == 0 }.arguments[1] as ItemStack
        verify(background.itemMeta).setCustomModelData(3046)
        verify(background.itemMeta).setHideTooltip(true)
        assertTrue(mockingDetails(background.itemMeta).invocations.none {
            it.method.name in setOf("displayName", "customName") && it.arguments.isNotEmpty()
        })
        f.click(39)
        f.click(22)
        verify(f.inventory, times(1)).setItem(eq(0), any(ItemStack::class.java))
    }

    @Test fun `invalid selection resets but its delayed error cannot revive a closed GUI`() = scenario { f ->
        `when`(f.module.select(f.player, f.floor, "B")).thenReturn("That floor does not exist.")
        f.click(41)
        f.next()
        verify(f.player).sendMessage("That floor does not exist.")
        f.close()
        f.next()
        f.click(12)
        assertTrue(f.queued.isEmpty())
        verify(f.module, times(1)).select(f.player, f.floor, "B")
    }
}
