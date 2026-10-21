package com.danidipp.sneakymisc.elevators

import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.*
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.entity.ItemFrame
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.mockito.Mockito.*

class ElevatorsModuleTest {
    private class Fixture {
        val doors = mockConstruction(ElevatorDoors::class.java)
        val directory = Files.createTempDirectory("elevator-test").toFile()
        val plugin = mock(Plugin::class.java)
        val world = mock(World::class.java)
        val scheduler = mock(BukkitScheduler::class.java)
        val particleTicks = mutableListOf<Runnable>()
        val particleTasks = mutableListOf<BukkitTask>()
        val callbacks = mutableListOf<Runnable>()
        val accounts = mutableListOf<Player>()
        val logger = Logger.getLogger("elevators-test")
        init {
            `when`(plugin.dataFolder).thenReturn(directory)
            `when`(world.name).thenReturn("world")
            `when`(world.key()).thenReturn(net.kyori.adventure.key.Key.key("minecraft:overworld"))
            `when`(world.minHeight).thenReturn(-64)
            `when`(world.maxHeight).thenReturn(320)
            `when`(world.players).thenAnswer { accounts.toList() }
            val chunk = mock(Chunk::class.java)
            `when`(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk)
            `when`(scheduler.runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())).thenAnswer {
                callbacks.add(it.getArgument(1)); mock(BukkitTask::class.java)
            }
            `when`(scheduler.runTaskTimer(eq(plugin), any(Runnable::class.java), anyLong(), anyLong())).thenAnswer {
                particleTicks.add(it.getArgument(1))
                mock(BukkitTask::class.java).also { task -> particleTasks.add(task) }
            }
        }
        fun player(position: Location): Player {
            val player = mock(Player::class.java)
            var current = position
            `when`(player.uniqueId).thenReturn(UUID.randomUUID())
            `when`(player.location).thenAnswer { current.clone() }
            `when`(player.teleport(any(Location::class.java))).thenAnswer { current = it.getArgument(0); true }
            accounts.add(player)
            return player
        }
        fun floor(module: ElevatorsModule, label: String, x: Double, y: Double = 60.0): ElevatorFloor {
            val floor = mock(ElevatorFloor::class.java)
            val elevator = module.getElevator("tower")!!
            val cab = ElevatorCab.at(Location(world, x, y, .5))
            `when`(floor.id).thenReturn("tower-$label")
            `when`(floor.elevator).thenReturn(elevator)
            `when`(floor.cab).thenReturn(cab)
            `when`(floor.location).thenReturn(cab.location)
            elevator.addFloor(floor)
            return floor
        }
    }

    @Test fun `new setup persists style tags and locations but no travel or recovery state`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld("world") }.thenReturn(f.world)
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                val module = ElevatorsModule(f.logger, f.plugin)
                module.createElevator("tower", "stone")
                module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5, 90f, 0f))
                val config = YamlConfiguration.loadConfiguration(f.directory.resolve("elevators.yml"))
                assertEquals(setOf("version", "elevators", "transit-cabs"), config.getKeys(false))
                assertEquals("stone", config.getString("elevators.tower.style"))
                val loaded = ElevatorsModule(f.logger, f.plugin)
                assertEquals("stone", loaded.getElevator("tower")!!.style)
                val cab = loaded.getTransitCabs().single()
                assertEquals("stone", cab.style)
                assertEquals(100.5, cab.cab.location.x)
                assertEquals(90f, cab.cab.location.yaw)
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `floor creation rejects missing or malformed name label separators`() {
        val f = Fixture()
        try {
            val module = ElevatorsModule(f.logger, f.plugin)
            listOf("1", "B", "G", "tower").forEach { module.createElevator(it, "stone") }
            mockConstruction(ElevatorFloor::class.java) { floor, context ->
                val id = context.arguments()[0] as String
                val cab = context.arguments()[1] as ElevatorCab
                `when`(floor.id).thenReturn(id)
                `when`(floor.cab).thenReturn(cab)
                `when`(floor.location).thenReturn(cab.location)
            }.use {
                for (id in listOf("1", "B", "G", "tower", "tower-", "-1", "tower-1-2")) {
                    val failure = assertFailsWith<IllegalArgumentException>("Accepted malformed floor ID: $id") {
                        module.createFloor(id, Location(f.world, .5, 60.0, .5))
                    }
                    assertEquals("Format: <name>-<floor>", failure.message)
                    assertNull(module.getFloor(id))
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `accepted floor IDs stay selectable and unchanged after saving and loading`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                mockConstruction(ElevatorFloor::class.java) { floor, context ->
                    val id = context.arguments()[0] as String
                    val cab = context.arguments()[1] as ElevatorCab
                    `when`(floor.id).thenReturn(id)
                    `when`(floor.cab).thenReturn(cab)
                    `when`(floor.location).thenReturn(cab.location)
                }.use {
                    val module = ElevatorsModule(f.logger, f.plugin)
                    listOf("1", "B", "G", "tower").forEach { module.createElevator(it, "stone") }
                    val ids = listOf("1-1", "B-B", "G-G", "tower-0", "tower-99", "tower-B", "tower-G")
                    ids.forEachIndexed { index, id ->
                        module.createFloor(id, Location(f.world, index * 10.0 + .5, 60.0, .5))
                    }
                    val loaded = ElevatorsModule(f.logger, f.plugin)
                    for (id in ids) {
                        assertEquals(id, loaded.getFloor(id)?.id)
                        val elevator = loaded.getElevator(id.substringBefore('-'))!!
                        assertEquals(id, elevator.getFloor("${elevator.name}-${id.substringAfter('-')}")?.id)
                    }
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `arrival interrupts only GUI users inside destination before moving current transit occupants`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(f.scheduler)
                val module = ElevatorsModule(f.logger, f.plugin)
                module.createElevator("tower", "stone")
                val origin = f.floor(module, "1", .5)
                val destination = f.floor(module, "2", 20.5)
                module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
                val incoming = f.player(origin.location.clone())
                val selecting = f.player(destination.location.clone())
                val outside = f.player(Location(f.world, 50.5, 60.0, .5))
                val bystander = f.player(destination.location.clone().add(1.0, 0.0, 0.0))
                var closed: (() -> Unit)? = null
                doAnswer { call -> closed = call.getArgument(0); ElevatorTask {} }.`when`(origin).close(callbackMatcher())
                fun gui(viewer: Player): ElevatorGUI {
                    val gui = mock(ElevatorGUI::class.java)
                    val inventory = mock(Inventory::class.java)
                    `when`(gui.floor).thenReturn(destination)
                    `when`(gui.inventory).thenReturn(inventory)
                    `when`(inventory.viewers).thenReturn(listOf(viewer))
                    module.guis.add(gui)
                    return gui
                }
                val selectedGui = gui(selecting)
                val outsideGui = gui(outside)
                assertEquals(ElevatorDepartureResult.STARTED, module.travel.start(origin, destination))
                verify(f.doors.constructed().last(), times(2)).initialize()
                assertTrue(f.particleTicks.isEmpty())
                closed!!()
                assertEquals(1, f.particleTicks.size)
                f.particleTicks.single().run()
                assertEquals(100.5, incoming.location.x)
                // A summoned account is discovered at arrival without registration or music.
                val summoned = f.player(incoming.location.clone().add(1.0, 0.0, 0.0))
                // Music starts immediately, attached to the account, with only arrival scheduled.
                verify(incoming).playSound(incoming, "lom:elevator", SoundCategory.MASTER, 1f, 1f)
                f.callbacks.single().run()
                assertEquals(20.5, incoming.location.x)
                assertEquals(21.5, summoned.location.x)
                verify(selectedGui).invalidate()
                verify(outsideGui).invalidate()
                verify(selecting).closeInventory()
                verify(selecting).damage(1.0)
                verify(selecting).playSound(selecting, "minecraft:block.amethyst_block.chime", SoundCategory.MASTER, 1f, 1.5f)
                verify(outside, never()).damage(anyDouble())
                verify(bystander, never()).damage(anyDouble())
                verify(summoned, never()).playSound(summoned, "lom:elevator", SoundCategory.MASTER, 1f, 1f)
                verify(incoming, never()).playSound(any(Location::class.java), eq("lom:elevator"), eq(SoundCategory.MASTER), anyFloat(), anyFloat())
                verify(incoming, never()).stopSound("lom:elevator", SoundCategory.MASTER)
                verify(summoned, never()).stopSound("lom:elevator", SoundCategory.MASTER)
                verify(destination).open()
                verify(f.particleTasks.single()).cancel()
                val emitted = mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" }
                assertEquals(24, emitted) // Six during transit and eighteen at arrival.
                val arrivalParticles = mockingDetails(f.world).invocations.filter { it.method.name == "spawnParticle" }.takeLast(18)
                for (particle in arrivalParticles) {
                    val position = particle.arguments[1] as Location
                    assertTrue(destination.cab.contains(position))
                    assertEquals(Particle.DUST, particle.arguments[0])
                    assertEquals(0, particle.arguments[2])
                    assertEquals(listOf(0.0, 0.0, 0.0, 0.0), particle.arguments.slice(3..6))
                    val dust = particle.arguments[7] as Particle.DustOptions
                    assertTrue(dust.color.blue > dust.color.green && dust.color.red > dust.color.green)
                }
                f.particleTicks.single().run() // A stale tick must not emit after arrival.
                assertEquals(emitted, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `transit doors initialize on creation and reload and are removed on deletion`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                val module = ElevatorsModule(f.logger, f.plugin)
                module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
                val created = f.doors.constructed().single()
                verify(created).initialize()
                val loaded = ElevatorsModule(f.logger, f.plugin)
                verify(f.doors.constructed().last()).initialize()
                loaded.deleteTransitCab("stone1")
                verify(f.doors.constructed().last()).cleanup()
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `transit keypad uses the keypad model and cancels interaction without opening a GUI`() {
        val f = Fixture()
        try {
            val module = ElevatorsModule(f.logger, f.plugin)
            module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
            val frame = mock(ItemFrame::class.java)
            val pdc = mock(PersistentDataContainer::class.java)
            `when`(frame.location).thenReturn(Location(f.world, 102.96875, -38.5, .5))
            `when`(frame.persistentDataContainer).thenReturn(pdc)
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java))
            }.use {
                module.linkTransitKeypad(frame, "stone1")
                verify(frame).setVisible(false)
                verify(frame).setItem(any(ItemStack::class.java))
                verify(pdc).remove(ElevatorsModule.CONTROL_FLOOR)
                verify(pdc).remove(ElevatorsModule.CONTROL_MODE)
                verify(pdc).set(ElevatorsModule.TRANSIT_KEYPAD, PersistentDataType.STRING, "stone1")
            }
            `when`(pdc.has(ElevatorsModule.TRANSIT_KEYPAD, PersistentDataType.STRING)).thenReturn(true)
            val player = f.player(Location(f.world, 100.5, -40.0, .5))
            for (hand in org.bukkit.inventory.EquipmentSlot.entries.filter { it == org.bukkit.inventory.EquipmentSlot.HAND || it == org.bukkit.inventory.EquipmentSlot.OFF_HAND }) {
                val event = org.bukkit.event.player.PlayerInteractEntityEvent(player, frame, hand)
                module.onFrame(event)
                assertTrue(event.isCancelled)
            }
            verify(player, never()).openInventory(any(Inventory::class.java))
            verify(frame, times(1)).setItem(any(ItemStack::class.java))
            `when`(frame.location).thenReturn(Location(f.world, 110.5, -38.5, .5))
            assertFailsWith<IllegalArgumentException> { module.linkTransitKeypad(frame, "stone1") }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `transit sparkles oppose elevator height direction skip empty cabs and stop on shutdown`() {
        for (destinationY in listOf(40.0, 60.0, 80.0)) {
            val f = Fixture()
            try {
                mockStatic(Bukkit::class.java).use { bukkit ->
                    bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(f.scheduler)
                    val module = ElevatorsModule(f.logger, f.plugin)
                    module.createElevator("tower", "stone")
                    val origin = f.floor(module, "1", .5)
                    val destination = f.floor(module, "2", 20.5, destinationY)
                    module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
                    val player = f.player(origin.location.clone())
                    var closed: (() -> Unit)? = null
                    doAnswer { call -> closed = call.getArgument(0); ElevatorTask {} }.`when`(origin).close(callbackMatcher())
                    assertEquals(ElevatorDepartureResult.STARTED, module.travel.start(origin, destination))
                    closed!!()
                    f.particleTicks.single().run()
                    val particles = mockingDetails(f.world).invocations.filter { it.method.name == "spawnParticle" }
                    assertEquals(6, particles.size)
                    for (particle in particles) {
                        assertTrue(module.getTransitCabs().single().cab.contains(particle.arguments[1] as Location))
                        assertEquals(-destinationY.compareTo(60.0).toDouble(), particle.arguments[4])
                        assertEquals(0.0, particle.arguments[3])
                        assertEquals(0.0, particle.arguments[5])
                        assertEquals(.2, particle.arguments[6])
                    }
                    player.teleport(origin.location)
                    f.particleTicks.single().run()
                    assertEquals(6, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
                    module.onDisable(org.bukkit.event.server.PluginDisableEvent(f.plugin))
                    verify(f.particleTasks.single()).cancel()
                    player.teleport(module.getTransitCabs().single().cab.location)
                    f.particleTicks.single().run()
                    assertEquals(6, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
                }
            } finally { f.doors.close(); f.directory.deleteRecursively() }
        }
    }

    private fun callbackMatcher(): () -> Unit {
        any<() -> Unit>()
        // A real function avoids passing null through Kotlin's parameter check at this seam.
        return {}
    }

    @Test fun `frames just inside every cab wall become keypads when linked`() {
        val f = Fixture()
        try {
            val module = ElevatorsModule(f.logger, f.plugin)
            val floor = ElevatorFloor("tower-1", ElevatorCab.at(Location(f.world, .5, 60.0, .5)), Elevator("tower", "stone"))
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java))
            }.use {
                val positions = listOf(
                    Location(f.world, -1.96875, 61.5, .5),
                    Location(f.world, 2.96875, 61.5, .5),
                    Location(f.world, .5, 61.5, -1.96875),
                    Location(f.world, .5, 61.5, 2.96875),
                )
                for (position in positions) {
                    val frame = mock(ItemFrame::class.java)
                    val pdc = mock(PersistentDataContainer::class.java)
                    `when`(frame.location).thenReturn(position)
                    `when`(frame.persistentDataContainer).thenReturn(pdc)
                    assertEquals("keypad", module.link(frame, floor))
                    verify(pdc).set(ElevatorsModule.CONTROL_FLOOR, PersistentDataType.STRING, "tower-1")
                    verify(pdc).set(ElevatorsModule.CONTROL_MODE, PersistentDataType.STRING, "select")
                    verify(frame).setItem(any(ItemStack::class.java))
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `outside frames including other floors and worlds become call controls for the named floor`() {
        val f = Fixture()
        try {
            val module = ElevatorsModule(f.logger, f.plugin)
            val floor = ElevatorFloor("tower-1", ElevatorCab.at(Location(f.world, .5, 60.0, .5)), Elevator("tower", "stone"))
            val positions = listOf(
                Location(f.world, -2.03125, 61.5, .5),
                Location(f.world, 3.03125, 61.5, .5),
                Location(f.world, .5, 61.5, -2.03125),
                Location(f.world, .5, 61.5, 3.03125),
                Location(f.world, .5, 65.5, .5),
                Location(mock(World::class.java), .5, 61.5, .5),
            )
            for (position in positions) {
                val frame = mock(ItemFrame::class.java)
                val pdc = mock(PersistentDataContainer::class.java)
                `when`(frame.location).thenReturn(position)
                `when`(frame.persistentDataContainer).thenReturn(pdc)
                assertEquals("call control", module.link(frame, floor))
                verify(pdc).set(ElevatorsModule.CONTROL_FLOOR, PersistentDataType.STRING, "tower-1")
                verify(pdc).set(ElevatorsModule.CONTROL_MODE, PersistentDataType.STRING, "call")
                verify(frame).setItem(null)
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }
}
