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
                    val ids = listOf("1-2", "B-B1", "G-G", "tower-2", "tower-99", "tower-B9", "tower-G")
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

    private fun Fixture.floorConstruction() = mockConstruction(ElevatorFloor::class.java) { floor, context ->
        val id = context.arguments()[0] as String
        val cab = context.arguments()[1] as ElevatorCab
        `when`(floor.id).thenReturn(id)
        `when`(floor.cab).thenReturn(cab)
        `when`(floor.location).thenReturn(cab.location)
    }

    @Test fun `floor creation rejects floor 0 plain B and B0 and stores aliases canonically`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                f.floorConstruction().use {
                    val module = ElevatorsModule(f.logger, f.plugin)
                    module.createElevator("tower", "stone")
                    for (id in listOf("tower-0", "tower-B", "tower-B0", "tower-0B", "tower-B10", "tower-01", "tower-100")) {
                        assertFailsWith<IllegalArgumentException>("Accepted $id") { module.createFloor(id, Location(f.world, .5, 60.0, .5)) }
                    }
                    assertEquals("tower-G", module.createFloor("tower-1", Location(f.world, .5, 60.0, .5)).id)
                    assertEquals("tower-B3", module.createFloor("tower-3B", Location(f.world, 10.5, 60.0, .5)).id)
                    assertFailsWith<IllegalArgumentException> { module.createFloor("tower-G", Location(f.world, 20.5, 60.0, .5)) }
                    assertEquals("tower-G", module.getFloor("tower-1")?.id)
                    assertEquals("tower-B3", module.getFloor("tower-3B")?.id)
                    val saved = YamlConfiguration.loadConfiguration(f.directory.resolve("elevators.yml"))
                    assertEquals(setOf("G", "B3"), saved.getConfigurationSection("elevators.tower.floors")!!.getKeys(false))
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `invalid config labels disable only elevators and block saving until a reload succeeds`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                f.floorConstruction().use {
                    ElevatorsModule(f.logger, f.plugin).apply {
                        createElevator("tower", "stone")
                        createFloor("tower-G", Location(f.world, .5, 60.0, .5))
                    }
                    val file = f.directory.resolve("elevators.yml")
                    val good = file.readText()
                    for (label in listOf("0", "1", "B", "B0", "1B")) {
                        file.writeText(good.replace("G:", "'$label':"))
                        val module = ElevatorsModule(f.logger, f.plugin) // Must not throw.
                        assertTrue(module.getElevators().isEmpty(), label)
                        assertFailsWith<IllegalArgumentException> { module.createElevator("other", "stone") }
                        assertEquals(good.replace("G:", "'$label':"), file.readText(), "Config was overwritten for $label")
                        file.writeText(good)
                        assertEquals("Reloaded 1 elevators and 0 transit cabs", module.reload())
                        assertEquals("tower-G", module.getFloor("tower-G")?.id)
                        module.createElevator("other", "stone")
                        module.deleteElevator(module.getElevator("other")!!)
                    }
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `failed reload keeps running elevators and blocks saving`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                f.floorConstruction().use { floors ->
                    val module = ElevatorsModule(f.logger, f.plugin)
                    module.createElevator("tower", "stone")
                    module.createFloor("tower-G", Location(f.world, .5, 60.0, .5))
                    val file = f.directory.resolve("elevators.yml")
                    val broken = file.readText().replace("G:", "'0':")
                    file.writeText(broken)
                    assertFailsWith<IllegalArgumentException> { module.reload() }
                    assertEquals("tower-G", module.getFloor("tower-G")?.id)
                    verify(floors.constructed().first(), never()).cleanup()
                    assertFailsWith<IllegalArgumentException> { module.createElevator("other", "stone") }
                    assertEquals(broken, file.readText())
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
                val transitCount = mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" }
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
                assertEquals(transitCount + 144, emitted)
                val arrivalParticles = mockingDetails(f.world).invocations.filter { it.method.name == "spawnParticle" }.takeLast(144)
                for (particle in arrivalParticles) {
                    val position = particle.arguments[1] as Location
                    assertParticleBounds(destination.cab, position)
                    assertEquals(Particle.TRAIL, particle.arguments[0])
                    assertEquals(1, particle.arguments[2])
                    assertEquals(listOf(0.0, 0.0, 0.0, 0.0), particle.arguments.slice(3..6))
                    val trail = particle.arguments[7] as Particle.Trail
                    assertParticleBounds(destination.cab, trail.target)
                    assertEquals(4.2 / 3, (position.y - trail.target.y) / trail.duration, .00001) // Same-height travel settles downward.
                    assertEquals(2, trail.duration)
                    assertTrue(trail.color.blue > trail.color.green && trail.color.red > trail.color.green)
                }
                f.particleTicks.single().run() // A stale tick must not emit after arrival.
                assertEquals(emitted, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
                verify(f.scheduler).runTaskLater(eq(f.plugin), any(Runnable::class.java), eq(2L))
                f.callbacks.last().run()
                val restingParticles = mockingDetails(f.world).invocations.filter { it.method.name == "spawnParticle" }.drop(emitted)
                assertEquals(144, restingParticles.size)
                for ((arrival, resting) in arrivalParticles.zip(restingParticles)) {
                    assertEquals((arrival.arguments[7] as Particle.Trail).target, resting.arguments[1])
                    assertEquals(Particle.DUST, resting.arguments[0])
                    assertEquals(listOf(0.0, 0.0, 0.0, 0.0), resting.arguments.slice(3..6))
                    assertEquals((arrival.arguments[7] as Particle.Trail).color, (resting.arguments[7] as Particle.DustOptions).color)
                }
            }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `restricted floors need the selector's permission and persist`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(f.scheduler)
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                val module = ElevatorsModule(f.logger, f.plugin)
                module.createElevator("tower", "stone")
                val origin = f.floor(module, "1", .5)
                val vip = f.floor(module, "2", 20.5)
                module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
                module.setRestricted(vip, true)
                val guest = f.player(origin.location.clone())
                val allowed = f.player(origin.location.clone())
                `when`(allowed.hasPermission("sneakymisc.elevator.floor.tower-2")).thenReturn(true)
                assertEquals("You do not have access to that floor.", module.select(guest, origin, "2"))
                assertNull(module.select(allowed, origin, "2"))
                assertTrue(module.travel.isDeparting(origin))
                val saved = YamlConfiguration.loadConfiguration(f.directory.resolve("elevators.yml"))
                assertEquals(listOf("2"), saved.getStringList("elevators.tower.restricted-floors"))
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

    @Test fun `transit keypad uses the keypad model and only the main hand click tries to start travel`() {
        val f = Fixture()
        try {
            val module = ElevatorsModule(f.logger, f.plugin)
            module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
            val frame = mock(ItemFrame::class.java)
            val pdc = mock(PersistentDataContainer::class.java)
            `when`(frame.location).thenReturn(Location(f.world, 102.96875, -38.5, .5))
            `when`(frame.persistentDataContainer).thenReturn(pdc)
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java, RETURNS_DEEP_STUBS))
            }.use {
                module.linkTransitKeypad(frame, "stone1")
                verify(frame).setVisible(false)
                verify(frame).setItem(any(ItemStack::class.java))
                verify(pdc).remove(ElevatorsModule.CONTROL_FLOOR)
                verify(pdc).remove(ElevatorsModule.CONTROL_MODE)
                verify(pdc).set(ElevatorsModule.TRANSIT_KEYPAD, PersistentDataType.STRING, "stone1")
            }
            `when`(pdc.has(ElevatorsModule.TRANSIT_KEYPAD, PersistentDataType.STRING)).thenReturn(true)
            `when`(pdc.get(ElevatorsModule.TRANSIT_KEYPAD, PersistentDataType.STRING)).thenReturn("stone1")
            val player = f.player(Location(f.world, 100.5, -40.0, .5))
            for (hand in org.bukkit.inventory.EquipmentSlot.entries.filter { it == org.bukkit.inventory.EquipmentSlot.HAND || it == org.bukkit.inventory.EquipmentSlot.OFF_HAND }) {
                val event = org.bukkit.event.player.PlayerInteractEntityEvent(player, frame, hand)
                module.onFrame(event)
                assertTrue(event.isCancelled)
            }
            verify(player, never()).openInventory(any(Inventory::class.java))
            // No stone elevator floors exist, so the single main-hand attempt fails audibly.
            verify(player, times(1)).playSound(player, "lom:fail_wrong", SoundCategory.MASTER, 1f, 1f)
            verify(frame, times(1)).setItem(any(ItemStack::class.java))
            `when`(frame.location).thenReturn(Location(f.world, 110.5, -38.5, .5))
            assertFailsWith<IllegalArgumentException> { module.linkTransitKeypad(frame, "stone1") }
        } finally { f.doors.close(); f.directory.deleteRecursively() }
    }

    @Test fun `transit keypad sends idle cab occupants to an accessible floor of the cab style`() {
        val f = Fixture()
        try {
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(f.scheduler)
                bukkit.`when`<World> { Bukkit.getWorld(NamespacedKey.minecraft("overworld")) }.thenReturn(f.world)
                val module = ElevatorsModule(f.logger, f.plugin)
                module.createElevator("tower", "stone")
                module.createElevator("hut", "wood")
                val restricted = f.floor(module, "G", .5)
                val destination = f.floor(module, "2", 20.5, 80.0)
                module.setRestricted(restricted, true)
                module.createTransitCab("stone1", "stone", Location(f.world, 100.5, -40.0, .5))
                module.createTransitCab("wood1", "wood", Location(f.world, 200.5, -40.0, .5))
                val stone = module.getTransitCabs().single { it.name == "stone1" }
                val wood = module.getTransitCabs().single { it.name == "wood1" }
                val stranded = f.player(stone.cab.location.clone())
                val companion = f.player(stone.cab.location.clone().add(1.0, 0.0, 0.0))
                val outside = f.player(Location(f.world, 50.5, 60.0, .5))
                val woodRider = f.player(wood.cab.location.clone())

                module.startRandomTransit(outside, stone)
                module.startRandomTransit(woodRider, wood) // No wood elevator floors exist.
                for (player in listOf(outside, woodRider)) verify(player).playSound(player, "lom:fail_wrong", SoundCategory.MASTER, 1f, 1f)
                assertTrue(module.travel.isIdle())

                module.startRandomTransit(stranded, stone)
                assertTrue(module.travel.uses(stone))
                assertTrue(module.travel.involves(destination)) // The restricted floor is never picked.
                for (player in listOf(stranded, companion)) verify(player).playSound(player, "lom:elevator", SoundCategory.MASTER, 1f, 1f)
                assertEquals(1, f.particleTicks.size)
                module.startRandomTransit(companion, stone) // Clicks during travel do nothing.
                verify(companion, never()).playSound(companion, "lom:fail_wrong", SoundCategory.MASTER, 1f, 1f)

                f.callbacks.first().run()
                assertEquals(20.5, stranded.location.x)
                assertEquals(80.0, stranded.location.y)
                assertEquals(21.5, companion.location.x)
                verify(destination).open()
                verify(restricted, never()).open()
                assertTrue(module.travel.isIdle())
            }
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
                    assertTrue(particles.size in 1..3)
                    val emitted = particles.size
                    for (particle in particles) {
                        val cab = module.getTransitCabs().single().cab
                        val position = particle.arguments[1] as Location
                        assertParticleBounds(cab, position)
                        if (destinationY == 60.0) {
                            assertEquals(Particle.DUST, particle.arguments[0])
                        } else {
                            assertEquals(Particle.TRAIL, particle.arguments[0])
                            val trail = particle.arguments[7] as Particle.Trail
                            assertParticleBounds(cab, trail.target)
                            assertEquals(position.x, trail.target.x)
                            assertEquals(position.z, trail.target.z)
                            val travelDirection = destinationY.compareTo(60.0)
                            assertEquals(-travelDirection, trail.target.y.compareTo(position.y))
                            val distance = kotlin.math.abs(trail.target.y - position.y)
                            assertTrue(distance in 1.47..4.2)
                            assertTrue(trail.duration in 45..128)
                            assertEquals(4.2 / 128, distance / trail.duration, .0005)
                            assertTrue(trail.color.blue > trail.color.green && trail.color.red > trail.color.green)
                        }
                        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), particle.arguments.slice(3..6))
                    }
                    player.teleport(origin.location)
                    f.particleTicks.single().run()
                    assertEquals(emitted, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
                    module.onDisable(org.bukkit.event.server.PluginDisableEvent(f.plugin))
                    verify(f.particleTasks.single()).cancel()
                    player.teleport(module.getTransitCabs().single().cab.location)
                    f.particleTicks.single().run()
                    assertEquals(emitted, mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" })
                }
            } finally { f.doors.close(); f.directory.deleteRecursively() }
        }
    }

    @Test fun `transit trails accelerate over the ride within a particle volume one block taller`() {
        for (movementY in listOf(-1.0, 1.0)) {
            val f = Fixture()
            try {
                mockStatic(Bukkit::class.java).use { bukkit ->
                    bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(f.scheduler)
                    val cab = ElevatorCab.at(Location(f.world, .5, 60.0, .5))
                    f.player(cab.location.clone())
                    val effects = ElevatorParticles(f.plugin, cab, movementY, 330L)
                    effects.start()
                    val speeds = mutableListOf<Double>()
                    val counts = mutableListOf<Int>()
                    val startingHeights = mutableSetOf<Double>()
                    repeat(331) {
                        val before = mockingDetails(f.world).invocations.count { it.method.name == "spawnParticle" }
                        f.particleTicks.single().run()
                        val frame = mockingDetails(f.world).invocations.filter { it.method.name == "spawnParticle" }.drop(before)
                        counts.add(frame.size)
                        val frameSpeeds = frame.map { particle ->
                            val start = particle.arguments[1] as Location
                            val trail = particle.arguments[7] as Particle.Trail
                            assertParticleBounds(cab, start)
                            assertParticleBounds(cab, trail.target)
                            assertEquals(movementY.toInt(), trail.target.y.compareTo(start.y))
                            startingHeights.add(start.y)
                            kotlin.math.abs(trail.target.y - start.y) / trail.duration
                        }
                        speeds.add(frameSpeeds.average())
                    }
                    assertTrue(counts.first() in 1..3)
                    assertTrue(counts.last() in 36..60)
                    assertTrue(counts.takeLast(30).sum() > counts.take(30).sum() * 10)
                    assertTrue(speeds.first() < .034) // At least four times slower than the old opening drift.
                    assertTrue(speeds.last() > 1.0)
                    assertTrue(speeds[165] > speeds.first() * 5)
                    assertTrue(speeds.last() - speeds[165] > speeds[165] - speeds.first())
                    assertTrue(startingHeights.size > 100) // Spawn phases must not form uniform horizontal sheets.
                    verify(f.scheduler).runTaskTimer(eq(f.plugin), any(Runnable::class.java), eq(0L), eq(1L))
                    effects.stop()
                }
            } finally { f.doors.close(); f.directory.deleteRecursively() }
        }
    }

    private fun assertParticleBounds(cab: ElevatorCab, position: Location) {
        assertEquals(cab.location.world, position.world)
        val offset = position.toVector().subtract(cab.location.toVector())
        assertTrue(offset.x in -2.5..2.5 && offset.z in -2.5..2.5)
        assertTrue(offset.y >= 0.0 && offset.y < 5.0)
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
                `when`(item.itemMeta).thenReturn(mock(ItemMeta::class.java, RETURNS_DEEP_STUBS))
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
            // Location only weakly references its world, so keep the other world reachable for the whole test.
            val otherWorld = mock(World::class.java)
            val positions = listOf(
                Location(f.world, -2.03125, 61.5, .5),
                Location(f.world, 3.03125, 61.5, .5),
                Location(f.world, .5, 61.5, -2.03125),
                Location(f.world, .5, 61.5, 3.03125),
                Location(f.world, .5, 65.5, .5),
                Location(otherWorld, .5, 61.5, .5),
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
