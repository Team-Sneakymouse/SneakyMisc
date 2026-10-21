package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.elevators.commands.*
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

class ElevatorsModule(private val logger: Logger, internal val plugin: Plugin = SneakyMisc.getInstance()) : SneakyModule(), Listener {
    companion object {
        val deps = emptyList<String>()
        val CONTROL_FLOOR = NamespacedKey("sneakymisc", "elevator_control_floor")
        val CONTROL_MODE = NamespacedKey("sneakymisc", "elevator_control_mode")
        const val SONG_DURATION = 310L
    }

    private val configFile = File(plugin.dataFolder, "elevators.yml")
    private val elevators = linkedMapOf<String, Elevator>()
    private val transitCabs = linkedMapOf<String, ElevatorTransitCab>()
    internal val guis = mutableSetOf<ElevatorGUI>()
    private val chunkUsers = mutableMapOf<Chunk, Int>()
    private val leases = mutableMapOf<ElevatorTransitCab, MutableList<Chunk>>()
    private var stopped = false
    private val utils = ElevatorCommandUtils(this)
    override val commands = listOf(
        SneakyMiscCommand(ElevatorCommands(this, utils).build(), "Manage elevators"),
        SneakyMiscCommand(ElevatorFloorCommands(this, utils).build(), "Manage elevator floors"),
        SneakyMiscCommand(ElevatorCabCommands(this, utils).build(), "Manage elevator transit cabs"),
    )
    override val listeners: List<Listener> get() = listOf(this, ElevatorGUI.listener(this))

    internal val travel = ElevatorTravelCoordinator(object : ElevatorTravelEnvironment<ElevatorFloor, ElevatorTransitCab> {
        override fun compatibleCabs(origin: ElevatorFloor) = transitCabs.values.filter { it.style == origin.elevator.style }
        override fun isEmpty(cab: ElevatorTransitCab) = cab.cab.players().isEmpty()
        override fun prepare(origin: ElevatorFloor, destination: ElevatorFloor, cab: ElevatorTransitCab) {
            val acquired = mutableListOf<Chunk>()
            leases[cab] = acquired
            for (chunk in listOf(origin.cab, destination.cab, cab.cab).flatMap { chunks(it) }.distinct()) {
                if (chunk !in chunkUsers) chunk.addPluginChunkTicket(plugin)
                chunkUsers[chunk] = (chunkUsers[chunk] ?: 0) + 1
                acquired.add(chunk)
            }
            origin.ensureDoors()
            destination.ensureDoors()
        }
        override fun release(origin: ElevatorFloor, destination: ElevatorFloor, cab: ElevatorTransitCab) {
            for (chunk in leases.remove(cab).orEmpty()) {
                val remaining = (chunkUsers[chunk] ?: 1) - 1
                if (remaining == 0) {
                    chunkUsers.remove(chunk)
                    chunk.removePluginChunkTicket(plugin)
                } else chunkUsers[chunk] = remaining
            }
        }
        override fun closeSelections(floor: ElevatorFloor, interrupt: Boolean) {
            val selected = guis.filter { it.floor === floor }
            val viewers = selected.flatMap { it.inventory.viewers.toList() }.filterIsInstance<Player>().distinctBy { it.uniqueId }
            selected.forEach { it.invalidate() }
            for (viewer in viewers) {
                viewer.closeInventory()
                if (interrupt && floor.cab.contains(viewer.location)) {
                    viewer.damage(1.0)
                    viewer.playSound(viewer, "minecraft:block.amethyst_block.chime", SoundCategory.MASTER, 1f, 1.5f)
                }
            }
        }
        override fun openDoors(floor: ElevatorFloor) = floor.open()
        override fun closeDoors(floor: ElevatorFloor, closed: () -> Unit) = floor.close(closed)
        override fun board(origin: ElevatorFloor, cab: ElevatorTransitCab): Int =
            transfer(origin.cab, cab.cab, true)
        override fun afterTravel(arrive: () -> Unit): ElevatorTask {
            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable { arrive() }, SONG_DURATION + 20)
            return ElevatorTask { task.cancel() }
        }
        override fun arrive(cab: ElevatorTransitCab, destination: ElevatorFloor) {
            transfer(cab.cab, destination.cab, false)
        }
        override fun failed(origin: ElevatorFloor, failure: Exception) {
            logger.log(Level.SEVERE, "Elevator travel from ${origin.id} failed", failure)
            origin.cab.players().forEach { it.sendMessage("Elevator travel failed. Use /spawn if you are stuck.") }
        }
    })

    init {
        if (configFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(configFile)
            require(config.getInt("version") == 2) { "Unsupported elevators.yml version" }
            for (name in config.getConfigurationSection("elevators")?.getKeys(false).orEmpty()) {
                requireName(name)
                val section = requireNotNull(config.getConfigurationSection("elevators.$name"))
                val elevator = Elevator(name, requireStyle(requireNotNull(section.getString("style"))))
                elevators[name] = elevator
                for (label in section.getConfigurationSection("floors")?.getKeys(false).orEmpty()) {
                    requireLabel(label)
                    val cab = ElevatorCab.at(requireNotNull(section.getLocation("floors.$label")))
                    requireAvailable(cab)
                    elevator.addFloor(ElevatorFloor("$name-$label", cab, elevator))
                }
            }
            for (name in config.getConfigurationSection("transit-cabs")?.getKeys(false).orEmpty()) {
                requireName(name)
                val section = requireNotNull(config.getConfigurationSection("transit-cabs.$name"))
                val cab = ElevatorCab.at(requireNotNull(section.getLocation("location")))
                requireAvailable(cab)
                transitCabs[name] = ElevatorTransitCab(name, requireStyle(requireNotNull(section.getString("style"))), cab)
            }
        }
        getElevators().flatMap { it.getFloors() }.forEach { it.initialize() }
        logger.info("Loaded ${elevators.size} elevators and ${transitCabs.size} transit cabs")
        if (File(plugin.dataFolder, "elevator.yml").exists()) {
            logger.info("Legacy elevator.yml is ignored. Set up elevators using /elevator create and /elevatorfloor create.")
        }
    }

    private fun transfer(from: ElevatorCab, to: ElevatorCab, music: Boolean): Int {
        var moved = 0
        for (player in from.players()) {
            // Recheck after each teleport: another mechanic may summon or move other occupants in its event handlers.
            if (!from.contains(player.location)) continue
            if (player.teleport(from.destination(player.location, to))) {
                moved++
                if (music) player.playSound(player, "lom:elevator", SoundCategory.MASTER, 1f, 1f)
                else player.stopSound("lom:elevator", SoundCategory.MASTER)
            } else {
                logger.warning("Elevator teleport rejected for ${player.uniqueId}")
                player.sendMessage("Elevator transfer failed. Use /spawn if you are stuck.")
            }
        }
        return moved
    }

    private fun chunks(cab: ElevatorCab): List<Chunk> {
        val min = cab.location.clone().add(-3.0, 0.0, -3.0)
        val max = cab.location.clone().add(3.0, 0.0, 3.0)
        return ((min.blockX shr 4)..(max.blockX shr 4)).flatMap { x ->
            ((min.blockZ shr 4)..(max.blockZ shr 4)).map { z -> cab.location.world.getChunkAt(x, z) }
        }
    }

    fun getElevators(): Collection<Elevator> = elevators.values
    fun getElevator(name: String): Elevator? = elevators[name]
    fun getFloor(id: String): ElevatorFloor? = elevators.values.firstNotNullOfOrNull { it.getFloor(id) }
    fun getTransitCabs(): Collection<ElevatorTransitCab> = transitCabs.values

    fun createElevator(name: String, style: String) {
        requireName(name)
        require(name !in elevators) { "Elevator already exists" }
        elevators[name] = Elevator(name, requireStyle(style))
        saveConfig()
    }

    fun setStyle(elevator: Elevator, style: String) {
        require(elevator.getFloors().none { travel.involves(it) }) { "Elevator has active travel" }
        elevator.style = requireStyle(style)
        saveConfig()
    }

    fun deleteElevator(elevator: Elevator) {
        require(elevator.getFloors().none { travel.involves(it) }) { "Elevator has active travel" }
        elevator.getFloors().toList().forEach { deleteFloor(it) }
        elevators.remove(elevator.name)
        saveConfig()
    }

    fun createFloor(id: String, position: Location): ElevatorFloor {
        require(id.count { it == '-' } == 1 && !id.startsWith('-') && !id.endsWith('-')) {
            "Format: <name>-<floor>"
        }
        val elevator = requireNotNull(elevators[id.substringBeforeLast('-')]) { "Create the elevator first: /elevator create <name> <style>" }
        requireLabel(id.substringAfterLast('-'))
        require(getFloor(id) == null) { "Elevator floor already exists" }
        val cab = ElevatorCab.at(position)
        requireAvailable(cab)
        return ElevatorFloor(id, cab, elevator).also {
            elevator.addFloor(it)
            it.initialize()
            saveConfig()
        }
    }

    fun deleteFloor(floor: ElevatorFloor) {
        require(!travel.involves(floor)) { "Elevator floor has active travel" }
        guis.filter { it.floor === floor }.toList().forEach { it.invalidate(); it.inventory.close() }
        floor.cleanup()
        floor.elevator.removeFloor(floor)
        saveConfig()
    }

    fun createTransitCab(name: String, style: String, position: Location) {
        requireName(name)
        require(name !in transitCabs) { "Transit cab already exists" }
        val cab = ElevatorCab.at(position)
        requireAvailable(cab)
        transitCabs[name] = ElevatorTransitCab(name, requireStyle(style), cab)
        saveConfig()
    }

    fun deleteTransitCab(name: String) {
        val cab = requireNotNull(transitCabs[name]) { "Transit cab not found" }
        require(!travel.uses(cab)) { "Transit cab has active travel" }
        transitCabs.remove(name)
        saveConfig()
    }

    fun setTransitStyle(name: String, style: String) {
        val cab = requireNotNull(transitCabs[name]) { "Transit cab not found" }
        require(!travel.uses(cab)) { "Transit cab has active travel" }
        cab.style = requireStyle(style)
        saveConfig()
    }

    private fun requireAvailable(cab: ElevatorCab) {
        require(cab.location.y >= cab.location.world.minHeight && cab.location.y + 4 <= cab.location.world.maxHeight) { "Cab is outside world height limits" }
        val existing = elevators.values.flatMap { it.getFloors().map { floor -> floor.cab } } + transitCabs.values.map { it.cab }
        require(existing.none { it.overlaps(cab) }) { "Cab interior overlaps an existing elevator floor or transit cab" }
    }

    fun call(floor: ElevatorFloor) { if (!stopped) travel.call(floor) }
    fun close(floor: ElevatorFloor) { travel.cancelDeparture(floor); floor.close() }

    internal fun select(player: Player, floor: ElevatorFloor, label: String): String? {
        if (stopped || !floor.cab.contains(player.location)) return "Stand inside this elevator to select a floor."
        val destination = floor.elevator.getFloor("${floor.elevator.name}-$label") ?: return "That floor does not exist."
        return when (travel.start(floor, destination)) {
            ElevatorDepartureResult.STARTED, ElevatorDepartureResult.SAME_FLOOR -> null
            ElevatorDepartureResult.BUSY -> "This cab is already departing."
            ElevatorDepartureResult.NO_COMPATIBLE_CAB -> "This elevator has no transit cabs configured for its style."
            ElevatorDepartureResult.AT_CAPACITY -> "This elevator is at capacity."
            ElevatorDepartureResult.FAILED -> "Elevator travel could not start."
        }
    }

    fun link(frame: ItemFrame, floor: ElevatorFloor): String {
        val mode = if (floor.cab.contains(frame.location)) "select" else "call"
        frame.persistentDataContainer.set(CONTROL_FLOOR, PersistentDataType.STRING, floor.id)
        frame.persistentDataContainer.set(CONTROL_MODE, PersistentDataType.STRING, mode)
        frame.isVisible = false
        frame.setItem(if (mode == "select") ElevatorGUI.getNumber(5994) else null)
        return if (mode == "select") "keypad" else "call control"
    }

    fun saveConfig() {
        val config = YamlConfiguration()
        config.set("version", 2)
        for (elevator in elevators.values) {
            config.set("elevators.${elevator.name}.style", elevator.style)
            for (floor in elevator.getFloors()) config.set("elevators.${elevator.name}.floors.${floor.id.substringAfterLast('-')}", floor.location)
        }
        for (cab in transitCabs.values) {
            config.set("transit-cabs.${cab.name}.style", cab.style)
            config.set("transit-cabs.${cab.name}.location", cab.cab.location)
        }
        config.save(configFile)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFrame(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val frame = event.rightClicked as? ItemFrame ?: return
        val id = frame.persistentDataContainer.get(CONTROL_FLOOR, PersistentDataType.STRING)
        if (id != null) {
            event.isCancelled = true
            val floor = getFloor(id) ?: return
            if (frame.persistentDataContainer.get(CONTROL_MODE, PersistentDataType.STRING) == "select") openSelector(event.player, floor)
            else call(floor)
            return
        }
        if (!event.player.hasPermission("sneakymisc.elevatorfloor")) return
        val name = event.player.inventory.itemInMainHand.itemMeta?.customName()?.let { PlainTextComponentSerializer.plainText().serialize(it) } ?: return
        val floor = getFloor(name) ?: return
        val control = link(frame, floor)
        event.isCancelled = true
        event.player.sendMessage("Linked $control to ${floor.id}")
    }

    @EventHandler(ignoreCancelled = true)
    fun onButton(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (!Tag.BUTTONS.isTagged(block.type)) return
        val frame = block.world.getNearbyEntities(block.location, 1.0, 1.0, 1.0).filterIsInstance<ItemFrame>()
            .firstOrNull { it.persistentDataContainer.get(CONTROL_MODE, PersistentDataType.STRING) == "call" } ?: return
        val floor = getFloor(frame.persistentDataContainer.get(CONTROL_FLOOR, PersistentDataType.STRING) ?: return) ?: return
        if (event.player.isSneaking) openSelector(event.player, floor) else call(floor)
    }

    private fun openSelector(player: Player, floor: ElevatorFloor) {
        if (!floor.cab.contains(player.location)) {
            player.sendMessage("Stand inside this elevator to select a floor.")
        } else if (!travel.isDeparting(floor) && !stopped) player.openInventory(ElevatorGUI(this, floor).inventory)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (transitCabs.values.any { it.cab.contains(event.from) && !it.cab.contains(event.to) }) {
            event.player.stopSound("lom:elevator", SoundCategory.MASTER)
        }
    }

    @EventHandler
    fun onDisable(event: PluginDisableEvent) {
        if (event.plugin !== plugin) return
        stopped = true
        travel.shutdown()
        guis.toList().forEach { it.invalidate(); it.inventory.close() }
        getElevators().flatMap { it.getFloors() }.forEach { it.stop() }
    }

    private fun requireName(value: String) {
        require(value.matches(Regex("[A-Za-z0-9_]+"))) { "Names may contain letters, digits, and underscores" }
    }
    private fun requireStyle(value: String): String {
        require(value.matches(Regex("[a-z0-9_-]+"))) { "Styles may contain lowercase letters, digits, underscores, and hyphens" }
        return value
    }
    private fun requireLabel(value: String) {
        require(value in listOf("B", "G") || value.toIntOrNull()?.let { it in 0..99 && it.toString() == value } == true) { "Floor must be B, G, or 0 through 99 (without leading zeros)" }
    }
}
