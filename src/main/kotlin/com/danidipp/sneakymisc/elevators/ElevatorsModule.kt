package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.elevators.commands.ElevatorCommandUtils
import com.danidipp.sneakymisc.elevators.commands.ElevatorCommands
import com.danidipp.sneakymisc.elevators.commands.ElevatorFloorCommands
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.event.Listener
import java.io.File
import java.util.logging.Logger

class ElevatorsModule(logger: Logger) : SneakyModule() {
    companion object {
        lateinit var instance: ElevatorsModule
        var deps = listOf<String>()
    }
    val commandUtils = ElevatorCommandUtils(this)
    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(ElevatorCommands(this, commandUtils).build(),"Manage elevators"),
        SneakyMiscCommand(ElevatorFloorCommands(this, commandUtils).build(), "Manage elevator floors"),
    )
    override val listeners: List<Listener> get() = listOf(
        ElevatorFloor.listener(this),
        ElevatorGUI.listener
    )

    private val configFile = File(SneakyMisc.getInstance().dataFolder, "elevator.yml")
    private val config = YamlConfiguration()
    private val elevators = mutableMapOf<String, Elevator>()

    init {
        instance = this
        ConfigurationSerialization.registerClass(Elevator::class.java)
        ConfigurationSerialization.registerClass(ElevatorFloor::class.java)

        elevators.clear()
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()
        }
        config.load(configFile)
        val elevatorSections = config.getConfigurationSection("elevators") ?: config.createSection("elevators")
        for (elevatorName in elevatorSections.getKeys(false)) {
            val elevatorSection = elevatorSections.getConfigurationSection(elevatorName) ?: throw Exception("'$elevatorName' is a key but not a section")
            val elevator = Elevator.deserialize(elevatorSection.getValues(false))
            elevators[elevatorName] = elevator
        }
        logger.info("Loaded ${elevators.size} elevators")
    }

    fun saveConfig() {
        val elevatorSections = config.createSection("elevators")
        for ((elevatorName, elevator) in elevators) {
            elevatorSections.set(elevatorName, elevator.serialize())
        }
        config.save(configFile)
    }

    fun createElevator(name: String): Elevator {
        val elevator = Elevator(name)
        elevators[name] = elevator
        return elevator
    }

    fun deleteElevator(name: String): Elevator? {
        val elevator = elevators.remove(name) ?: return null
        for(floor in elevator.getFloors()) {
            elevator.deleteFloor(floor)
        }
        return elevator
    }


    fun getElevators(): Collection<Elevator> {
        return elevators.values
    }
    fun getElevator(elevatorName: String): Elevator? {
        return elevators[elevatorName]
    }
    fun getFloor(floorId: String): ElevatorFloor? {
        val elevatorName = floorId.split("-").firstOrNull() ?: return null
        return getElevator(elevatorName)?.getFloor(floorId)
    }

//    fun update(elevator: ElevatorFloor, location: org.bukkit.Location): ElevatorFloor {
//        elevator.location = location
//        saveConfig()
//        return elevator
//    }
//    fun update(name: String, location: org.bukkit.Location): ElevatorFloor? {
//        val elevator = elevators.find { it.floor == name } ?: return null
//        update(elevator, location)
//        return elevator
//    }

}


