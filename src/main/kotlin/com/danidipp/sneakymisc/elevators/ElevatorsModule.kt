package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.elevators.commands.*
import org.bukkit.*
import org.bukkit.block.data.Levelled
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
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
        val TRANSIT_KEYPAD = NamespacedKey("sneakymisc", "elevator_transit_keypad")
        val CONTROL_MODE = NamespacedKey("sneakymisc", "elevator_control_mode")
        const val SONG_DURATION = 310L
        private const val TRAVEL_LIGHT_LEVEL = 15
        private const val IDLE_LIGHT_LEVEL = 1
    }

    private val configFile = File(plugin.dataFolder, "elevators.yml")
    private val elevators = linkedMapOf<String, Elevator>()
    private val transitCabs = linkedMapOf<String, ElevatorTransitCab>()
    internal val guis = mutableSetOf<ElevatorGUI>()
    private val chunkUsers = mutableMapOf<Chunk, Int>()
    private val particles = mutableMapOf<ElevatorTransitCab, ElevatorParticles>()
    private val leases = mutableMapOf<ElevatorTransitCab, MutableList<Chunk>>()
    private var stopped = false
    /** Set when elevators.yml could not be read. Saving is blocked so the file is not overwritten. */
    private var configError: String? = null
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
        override fun prepare(origin: ElevatorFloor?, destination: ElevatorFloor, cab: ElevatorTransitCab) {
            val acquired = mutableListOf<Chunk>()
            leases[cab] = acquired
            for (chunk in listOfNotNull(origin?.cab, destination.cab, cab.cab).flatMap { chunks(it) }.distinct()) {
                if (chunk !in chunkUsers) chunk.addPluginChunkTicket(plugin)
                chunkUsers[chunk] = (chunkUsers[chunk] ?: 0) + 1
                acquired.add(chunk)
            }
            origin?.ensureDoors()
            destination.ensureDoors()
            cab.initialize()
            setLights(cab.cab, TRAVEL_LIGHT_LEVEL)
            // Travel from inside a transit cab has no departure height, so it picks a random direction.
            val movementY = if (origin == null) listOf(-1.0, 1.0).random()
            else -destination.location.y.compareTo(origin.location.y).toDouble()
            particles[cab] = ElevatorParticles(plugin, cab.cab, movementY, SONG_DURATION + 20)
        }
        override fun release(origin: ElevatorFloor?, destination: ElevatorFloor, cab: ElevatorTransitCab) {
            particles.remove(cab)?.stop()
            setLights(cab.cab, IDLE_LIGHT_LEVEL)
            for (chunk in leases.remove(cab).orEmpty()) {
                val remaining = (chunkUsers[chunk] ?: 1) - 1
                if (remaining == 0) {
                    chunkUsers.remove(chunk)
                    chunk.removePluginChunkTicket(plugin)
                } else chunkUsers[chunk] = remaining
            }
        }
        override fun closeSelections(floor: ElevatorFloor, interrupt: Boolean) {
            val (confirmed, selected) = guis.filter { it.floor === floor }.partition { !interrupt && it.pending }
            confirmed.forEach { it.closeLater() }
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
        override fun board(origin: ElevatorFloor, cab: ElevatorTransitCab): Int {
            val moved = transfer(origin.cab, cab.cab, true)
            if (moved > 0) particles[cab]?.start()
            return moved
        }
        override fun boardInPlace(cab: ElevatorTransitCab): Int {
            val occupants = cab.cab.players()
            occupants.forEach { it.playSound(it, "lom:elevator", SoundCategory.MASTER, 1f, 1f) }
            if (occupants.isNotEmpty()) particles[cab]?.start()
            return occupants.size
        }
        override fun afterTravel(arrive: () -> Unit): ElevatorTask {
            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable { arrive() }, SONG_DURATION + 20)
            return ElevatorTask { task.cancel() }
        }
        override fun arrive(cab: ElevatorTransitCab, destination: ElevatorFloor) {
            if (transfer(cab.cab, destination.cab, false) > 0) particles[cab]?.arrival(destination.cab)
        }
        override fun failed(origin: ElevatorFloor?, cab: ElevatorTransitCab, failure: Exception) {
            logger.log(Level.SEVERE, "Elevator travel from ${origin?.id ?: "transit cab ${cab.name}"} failed", failure)
            (origin?.cab ?: cab.cab).players().forEach { it.sendMessage("Elevator travel failed. Use /spawn if you are stuck.") }
        }
    })

    init {
        loadConfig()?.let { logger.severe("Elevators are disabled until elevators.yml is fixed and /elevator reload is run") }
        if (File(plugin.dataFolder, "elevator.yml").exists()) {
            logger.info("Legacy elevator.yml is ignored. Set up elevators using /elevator create and /elevatorfloor create.")
        }
    }

    /**
     * Reads elevators.yml and replaces the running setup. If the file is invalid, the running setup is kept
     * (empty at startup), saving is blocked, and the error is returned.
     */
    private fun loadConfig(beforeApply: () -> Unit = {}): String? {
        val (loadedElevators, loadedCabs) = try {
            parseConfig()
        } catch (failure: Exception) {
            val message = failure.message ?: failure.javaClass.simpleName
            configError = message
            logger.log(Level.SEVERE, "Could not load elevators.yml: $message", failure)
            return message
        }
        beforeApply()
        configError = null
        elevators.clear(); elevators.putAll(loadedElevators)
        transitCabs.clear(); transitCabs.putAll(loadedCabs)
        getElevators().flatMap { it.getFloors() }.forEach { it.initialize() }
        transitCabs.values.forEach { it.initialize() }
        logger.info("Loaded ${elevators.size} elevators and ${transitCabs.size} transit cabs")
        return null
    }

    private fun parseConfig(): Pair<Map<String, Elevator>, Map<String, ElevatorTransitCab>> {
        val loadedElevators = linkedMapOf<String, Elevator>()
        val loadedCabs = linkedMapOf<String, ElevatorTransitCab>()
        if (!configFile.exists()) return loadedElevators to loadedCabs
        val config = YamlConfiguration.loadConfiguration(configFile)
        require(config.getInt("version") == 2) { "Unsupported elevators.yml version" }
        val occupied = mutableListOf<ElevatorCab>()
        for (name in config.getConfigurationSection("elevators")?.getKeys(false).orEmpty()) {
            requireName(name)
            val section = requireNotNull(config.getConfigurationSection("elevators.$name"))
            val elevator = Elevator(name, requireStyle(requireNotNull(section.getString("style")) { "Elevator $name has no style" }))
            loadedElevators[name] = elevator
            for (label in section.getConfigurationSection("floors")?.getKeys(false).orEmpty()) {
                require(ElevatorFloorLabels.isCanonical(label)) { "Invalid floor $name-$label. ${ElevatorFloorLabels.RULE}" }
                val cab = ElevatorCab.at(requireNotNull(section.getLocation("floors.$label")) { "Floor $name-$label has no valid location" })
                requireAvailable(cab, occupied)
                occupied.add(cab)
                elevator.addFloor(ElevatorFloor("$name-$label", cab, elevator))
            }
            for (label in section.getStringList("restricted-floors")) {
                require(ElevatorFloorLabels.isCanonical(label)) { "Invalid restricted floor $name-$label. ${ElevatorFloorLabels.RULE}" }
                if (elevator.getFloor("$name-$label") != null) elevator.setRestricted("$name-$label", true)
            }
        }
        for (name in config.getConfigurationSection("transit-cabs")?.getKeys(false).orEmpty()) {
            requireName(name)
            val section = requireNotNull(config.getConfigurationSection("transit-cabs.$name"))
            val cab = ElevatorCab.at(requireNotNull(section.getLocation("location")) { "Transit cab $name has no valid location" })
            requireAvailable(cab, occupied)
            occupied.add(cab)
            loadedCabs[name] = ElevatorTransitCab(name, requireStyle(requireNotNull(section.getString("style")) { "Transit cab $name has no style" }), cab)
        }
        return loadedElevators to loadedCabs
    }

    /** Re-reads elevators.yml. Refuses while any elevator travel is active. */
    fun reload(): String {
        require(!stopped) { "Elevators are currently stopped" }
        require(travel.isIdle()) { "Wait until all elevator travel has finished" }
        // The old setup is only torn down once the new file has parsed, so a bad file leaves running elevators alone.
        val error = loadConfig {
            guis.toList().forEach { it.invalidate(); it.inventory.close() }
            getElevators().flatMap { it.getFloors() }.forEach { it.cleanup() }
            transitCabs.values.forEach { it.cleanup() }
        }
        require(error == null) { "Reload failed: $error. Current elevators keep running, but changes are blocked until a reload succeeds." }
        return "Reloaded ${elevators.size} elevators and ${transitCabs.size} transit cabs"
    }

    private fun requireWritable() {
        configError?.let { throw IllegalArgumentException("elevators.yml failed to load ($it). Fix it and run /elevator reload before making changes.") }
    }

    private fun transfer(from: ElevatorCab, to: ElevatorCab, music: Boolean): Int {
        var moved = 0
        for (player in from.players()) {
            // Recheck after each teleport: another mechanic may summon or move other occupants in its event handlers.
            if (!from.contains(player.location)) continue
            if (player.teleport(from.destination(player.location, to))) {
                moved++
                if (music) player.playSound(player, "lom:elevator", SoundCategory.MASTER, 1f, 1f)
            } else {
                logger.warning("Elevator teleport rejected for ${player.uniqueId}")
                player.sendMessage("Elevator transfer failed. Use /spawn if you are stuck.")
            }
        }
        return moved
    }

    /**
     * Light blocks are found again on every call instead of being remembered between rides. A crash mid-ride leaves
     * them bright until the cab's next ride ends.
     */
    private fun setLights(cab: ElevatorCab, level: Int) {
        for (block in cab.blocks()) {
            if (block.type != Material.LIGHT) continue
            val light = block.blockData as Levelled
            if (light.level == level) continue
            light.level = level
            block.setBlockData(light, false)
        }
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
    /** Accepts label aliases, e.g. `tower-1` finds `tower-G`. */
    fun getFloor(id: String): ElevatorFloor? = ElevatorFloorLabels.canonicalId(id).let { canonical ->
        elevators.values.firstNotNullOfOrNull { it.getFloor(canonical) }
    }
    fun getTransitCabs(): Collection<ElevatorTransitCab> = transitCabs.values

    fun createElevator(name: String, style: String) {
        requireWritable()
        requireName(name)
        require(name !in elevators) { "Elevator already exists" }
        elevators[name] = Elevator(name, requireStyle(style))
        saveConfig()
    }

    fun setStyle(elevator: Elevator, style: String) {
        requireWritable()
        require(elevator.getFloors().none { travel.involves(it) }) { "Elevator has active travel" }
        elevator.style = requireStyle(style)
        saveConfig()
    }

    fun deleteElevator(elevator: Elevator) {
        requireWritable()
        require(elevator.getFloors().none { travel.involves(it) }) { "Elevator has active travel" }
        elevator.getFloors().toList().forEach { deleteFloor(it) }
        elevators.remove(elevator.name)
        saveConfig()
    }

    /** Accepts label aliases and stores the canonical ID, e.g. `tower-1` creates `tower-G`. */
    fun createFloor(input: String, position: Location): ElevatorFloor {
        requireWritable()
        require(input.count { it == '-' } == 1 && !input.startsWith('-') && !input.endsWith('-')) {
            "Format: <name>-<floor>"
        }
        val id = ElevatorFloorLabels.canonicalId(input)
        val elevator = requireNotNull(elevators[id.substringBeforeLast('-')]) { "Create the elevator first: /elevator create <name> <style>" }
        require(ElevatorFloorLabels.isCanonical(id.substringAfterLast('-'))) { ElevatorFloorLabels.RULE }
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
        requireWritable()
        require(!travel.involves(floor)) { "Elevator floor has active travel" }
        guis.filter { it.floor === floor }.toList().forEach { it.invalidate(); it.inventory.close() }
        floor.cleanup()
        floor.elevator.removeFloor(floor)
        saveConfig()
    }

    fun createTransitCab(name: String, style: String, position: Location) {
        requireWritable()
        requireName(name)
        require(name !in transitCabs) { "Transit cab already exists" }
        val cab = ElevatorCab.at(position)
        requireAvailable(cab)
        transitCabs[name] = ElevatorTransitCab(name, requireStyle(style), cab).also { it.initialize() }
        saveConfig()
    }

    fun deleteTransitCab(name: String) {
        requireWritable()
        val cab = requireNotNull(transitCabs[name]) { "Transit cab not found" }
        require(!travel.uses(cab)) { "Transit cab has active travel" }
        cab.cleanup()
        transitCabs.remove(name)
        saveConfig()
    }

    fun setTransitStyle(name: String, style: String) {
        requireWritable()
        val cab = requireNotNull(transitCabs[name]) { "Transit cab not found" }
        require(!travel.uses(cab)) { "Transit cab has active travel" }
        cab.style = requireStyle(style)
        saveConfig()
    }

    private fun requireAvailable(
        cab: ElevatorCab,
        existing: List<ElevatorCab> = elevators.values.flatMap { it.getFloors().map { floor -> floor.cab } } + transitCabs.values.map { it.cab },
    ) {
        require(cab.location.y >= cab.location.world.minHeight && cab.location.y + ElevatorCab.HEIGHT <= cab.location.world.maxHeight) { "Cab is outside world height limits" }
        require(existing.none { it.overlaps(cab) }) { "Cab interior overlaps an existing elevator floor or transit cab" }
    }

    fun call(floor: ElevatorFloor) { if (!stopped) travel.call(floor) }
    fun close(floor: ElevatorFloor) { travel.cancelDeparture(floor); floor.close() }

    internal fun select(player: Player, floor: ElevatorFloor, label: String): String? {
        if (stopped) return "Elevators are currently stopped."
        val destination = floor.elevator.getFloor("${floor.elevator.name}-$label") ?: return "That floor does not exist."
        // Only the selector needs access; everyone in the cab rides along when it departs.
        if (floor.elevator.isRestricted(destination.id) && !player.hasPermission(floorPermission(destination))) {
            return "You do not have access to that floor."
        }
        return when (travel.start(floor, destination)) {
            ElevatorDepartureResult.STARTED, ElevatorDepartureResult.SAME_FLOOR -> null
            ElevatorDepartureResult.BUSY -> "This cab is already departing."
            ElevatorDepartureResult.NO_COMPATIBLE_CAB -> "This elevator has no transit cabs configured for its style."
            ElevatorDepartureResult.AT_CAPACITY -> "This elevator is at capacity."
            ElevatorDepartureResult.FAILED -> "Elevator travel could not start."
        }
    }

    /**
     * Clicking the keypad inside an idle transit cab sends everyone inside to a random elevator floor of the cab's
     * style that the clicker may select. Clicks during travel do nothing; anything else that prevents travel
     * plays the error sound.
     */
    internal fun startRandomTransit(player: Player, cab: ElevatorTransitCab) {
        if (stopped || travel.uses(cab)) return
        val destinations = elevators.values.filter { it.style == cab.style }.flatMap { it.getFloors() }
            .filter { !it.elevator.isRestricted(it.id) || player.hasPermission(floorPermission(it)) }
        val result = if (destinations.isEmpty() || !cab.cab.contains(player.location)) null
        else travel.startFromCab(cab, destinations.random())
        if (result != ElevatorDepartureResult.STARTED) player.playSound(player, "lom:fail_wrong", SoundCategory.MASTER, 1f, 1f)
    }

    fun floorPermission(floor: ElevatorFloor): String = "sneakymisc.elevator.floor.${floor.id}"

    fun setRestricted(floor: ElevatorFloor, restricted: Boolean) {
        requireWritable()
        floor.elevator.setRestricted(floor.id, restricted)
        saveConfig()
    }

    fun link(frame: ItemFrame, floor: ElevatorFloor): String {
        val mode = if (floor.cab.contains(frame.location)) "select" else "call"
        frame.persistentDataContainer.remove(TRANSIT_KEYPAD)
        frame.persistentDataContainer.set(CONTROL_FLOOR, PersistentDataType.STRING, floor.id)
        frame.persistentDataContainer.set(CONTROL_MODE, PersistentDataType.STRING, mode)
        frame.isVisible = false
        frame.setItem(if (mode == "select") ElevatorGUI.model("panel") else null)
        return if (mode == "select") "keypad" else "call control"
    }

    fun linkTransitKeypad(frame: ItemFrame, name: String) {
        val cab = requireNotNull(transitCabs[name]) { "Transit cab not found" }
        require(cab.cab.contains(frame.location)) { "Place the keypad inside this transit cab" }
        frame.persistentDataContainer.remove(CONTROL_FLOOR)
        frame.persistentDataContainer.remove(CONTROL_MODE)
        frame.persistentDataContainer.set(TRANSIT_KEYPAD, PersistentDataType.STRING, name)
        frame.isVisible = false
        frame.setItem(ElevatorGUI.model("panel"))
    }

    fun saveConfig() {
        val config = YamlConfiguration()
        config.set("version", 2)
        for (elevator in elevators.values) {
            config.set("elevators.${elevator.name}.style", elevator.style)
            for (floor in elevator.getFloors()) config.set("elevators.${elevator.name}.floors.${floor.id.substringAfterLast('-')}", floor.location)
            if (elevator.restrictedFloorIds().isNotEmpty()) {
                config.set("elevators.${elevator.name}.restricted-floors", elevator.restrictedFloorIds().map { it.substringAfterLast('-') })
            }
        }
        for (cab in transitCabs.values) {
            config.set("transit-cabs.${cab.name}.style", cab.style)
            config.set("transit-cabs.${cab.name}.location", cab.cab.location)
        }
        config.save(configFile)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFrame(event: PlayerInteractEntityEvent) {
        val frame = event.rightClicked as? ItemFrame ?: return
        if (frame.persistentDataContainer.has(TRANSIT_KEYPAD, PersistentDataType.STRING)) {
            event.isCancelled = true
            // Each click fires once per hand; only the main hand may start travel.
            if (event.hand != EquipmentSlot.HAND) return
            val cab = frame.persistentDataContainer.get(TRANSIT_KEYPAD, PersistentDataType.STRING)?.let { transitCabs[it] } ?: return
            startRandomTransit(event.player, cab)
            return
        }
        if (event.hand != EquipmentSlot.HAND) return
        val id = frame.persistentDataContainer.get(CONTROL_FLOOR, PersistentDataType.STRING)
        if (id != null) {
            event.isCancelled = true
            val floor = getFloor(id) ?: return
            if (frame.persistentDataContainer.get(CONTROL_MODE, PersistentDataType.STRING) == "select") openSelector(event.player, floor)
            else call(floor)
            return
        }
        if (!event.player.hasPermission("sneakymisc.elevatorfloor") && !event.player.hasPermission("sneakymisc.elevator")) return
        val name = event.player.inventory.itemInMainHand.itemMeta?.customName()?.let { PlainTextComponentSerializer.plainText().serialize(it) } ?: return
        if (name in transitCabs && event.player.hasPermission("sneakymisc.elevator")) {
            event.isCancelled = true
            try {
                linkTransitKeypad(frame, name)
                event.player.sendMessage("Linked transit keypad to $name")
            } catch (failure: IllegalArgumentException) {
                event.player.sendMessage(failure.message ?: "Invalid transit keypad")
            }
            return
        }
        if (!event.player.hasPermission("sneakymisc.elevatorfloor")) return
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
}
