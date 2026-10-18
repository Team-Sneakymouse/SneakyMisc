package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import com.sneakyutils.util.Direction
import com.sneakyutils.util.PoseFacing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.MultipleFacing
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import kotlin.math.floor

data class ElevatorFloor(
    val id: String,
    var location: Location,
    var isOpen: Boolean,
    val elevator: Elevator
): ConfigurationSerializable {
    private var leftDoor: BlockDisplay? = null
    private var rightDoor: BlockDisplay? = null
    private var closingTask: BukkitTask? = null

    fun getPlayers(): List<Player> {
        return location.world.getNearbyEntities(location, 3.0, 3.0, 3.0)
            .filterIsInstance<Player>()
    }

    private fun scheduleClose() {
        closingTask?.cancel()
        closingTask = Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
            close()
        }, 20 * 10)
    }

    fun open() {
        if (isOpen) {
            scheduleClose()
            return
        }
        val doorLocation = location.clone().add(location.direction.multiply(3.0))
        doorLocation.world.playSound(doorLocation, "lom:elevatorping", SoundCategory.MASTER, 1f, 1f)
        val openDelay = 20L

        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
            isOpen = true
            ElevatorsModule.instance.saveConfig()
            val leftDoor = leftDoor
            val rightDoor = rightDoor

            if (leftDoor != null) {
                leftDoor.interpolationDelay = -1
                leftDoor.transformation = getDoorTransformation(leftDoor, DoorBlockPosition.LEFT, Openness.OPEN)
            }
            if (rightDoor != null) {
                rightDoor.interpolationDelay = -1
                rightDoor.transformation = getDoorTransformation(rightDoor, DoorBlockPosition.RIGHT, Openness.OPEN)
            }

            setOpenness(Openness.HALF)
            Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
                setOpenness(Openness.BARELY)
            }, 1 * DOOR_DURATION.toLong()/3L - 2)
            Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
                setOpenness(Openness.OPEN)
            }, 2 * DOOR_DURATION.toLong()/3L - 2)

            scheduleClose()
        }, openDelay)
    }

    fun close() {
        if (closingTask?.isCancelled == false) {
            closingTask?.cancel()
            closingTask = null
        }
        isOpen = false
        ElevatorsModule.instance.saveConfig()
        val leftDoor = leftDoor
        val rightDoor = rightDoor

        if (leftDoor != null) {
            leftDoor.interpolationDelay = -1
            leftDoor.transformation = getDoorTransformation(leftDoor, DoorBlockPosition.LEFT, Openness.CLOSED)
        }
        if (rightDoor != null) {
            rightDoor.interpolationDelay = -1
            rightDoor.transformation = getDoorTransformation(rightDoor, DoorBlockPosition.RIGHT, Openness.CLOSED)
        }

        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
            setOpenness(Openness.BARELY)
        }, 1 * DOOR_DURATION.toLong()/3L + 2)
        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
            setOpenness(Openness.HALF)
        }, 2 * DOOR_DURATION.toLong()/3L + 2)
        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), Runnable {
            setOpenness(Openness.CLOSED)
        }, DOOR_DURATION.toLong())
    }

    private enum class DoorBlockPosition {
        LEFT, CENTER, RIGHT
    }
    private fun getDoorBlock(height: Int, position: DoorBlockPosition): Block {
        val blockLocation = location.clone().add(location.direction.multiply(3.0))
        val offset = when (position) {
            DoorBlockPosition.LEFT -> when (PoseFacing.getDirection(location)) {
                Direction.NORTH -> BlockFace.WEST.direction
                Direction.EAST -> BlockFace.NORTH.direction
                Direction.SOUTH -> BlockFace.EAST.direction
                Direction.WEST -> BlockFace.SOUTH.direction
                else -> Vector(0, 0, 0)
            }
            DoorBlockPosition.CENTER -> Vector(0, 0, 0)
            DoorBlockPosition.RIGHT -> when (PoseFacing.getDirection(location)) {
                Direction.NORTH -> BlockFace.EAST.direction
                Direction.EAST -> BlockFace.SOUTH.direction
                Direction.SOUTH -> BlockFace.WEST.direction
                Direction.WEST -> BlockFace.NORTH.direction
                else -> Vector(0, 0, 0)
            }
        }
        return blockLocation.add(offset).add(0.0, height.toDouble(), 0.0).block
    }

    // Blocks elevator door with iron bars
    private fun setOpenness(state: Openness) {
        for (i in 0 ..< 1 * DOOR_HEIGHT.toInt()) {
            when (state) {
                Openness.OPEN -> {
                    getDoorBlock(i, DoorBlockPosition.LEFT).setType(Material.AIR, false)
                    getDoorBlock(i, DoorBlockPosition.RIGHT).setType(Material.AIR, false)
                    getDoorBlock(i, DoorBlockPosition.CENTER).setType(Material.AIR, false)
                }
                Openness.BARELY -> {
                    val leftBlock = getDoorBlock(i, DoorBlockPosition.LEFT)
                    val rightBlock = getDoorBlock(i, DoorBlockPosition.RIGHT)

                    leftBlock.setType(Material.IRON_BARS, false)
                    rightBlock.setType(Material.IRON_BARS, false)

                    val leftData = leftBlock.blockData as MultipleFacing
                    val rightData = rightBlock.blockData as MultipleFacing
                    val direction = PoseFacing.getDirection(location)
                    when (direction) {
                        Direction.NORTH, Direction.SOUTH -> {
                            leftData.setFace(BlockFace.WEST, direction == Direction.NORTH)
                            leftData.setFace(BlockFace.EAST, direction == Direction.SOUTH)
                            rightData.setFace(BlockFace.WEST, direction == Direction.SOUTH)
                            rightData.setFace(BlockFace.EAST, direction == Direction.NORTH)
                        }
                        Direction.EAST, Direction.WEST -> {
                            leftData.setFace(BlockFace.NORTH, direction == Direction.EAST)
                            leftData.setFace(BlockFace.SOUTH, direction == Direction.WEST)
                            rightData.setFace(BlockFace.NORTH, direction == Direction.WEST)
                            rightData.setFace(BlockFace.SOUTH, direction == Direction.EAST)
                        }
                        else -> {}
                    }
                    leftBlock.setBlockData(leftData, false)
                    rightBlock.setBlockData(rightData, false)

                    getDoorBlock(i, DoorBlockPosition.CENTER).setType(Material.AIR, false)
                }
                Openness.HALF -> {
                    val leftBlock = getDoorBlock(i, DoorBlockPosition.LEFT)
                    val rightBlock = getDoorBlock(i, DoorBlockPosition.RIGHT)

                    leftBlock.setType(Material.IRON_BARS, false)
                    rightBlock.setType(Material.IRON_BARS, false)

                    val leftData = leftBlock.blockData as MultipleFacing
                    val rightData = rightBlock.blockData as MultipleFacing
                    val direction = PoseFacing.getDirection(location)
                    when (direction) {
                        Direction.NORTH, Direction.SOUTH -> {
                            leftData.setFace(BlockFace.WEST, true)
                            leftData.setFace(BlockFace.EAST, true)
                            rightData.setFace(BlockFace.WEST, true)
                            rightData.setFace(BlockFace.EAST, true)
                        }
                        Direction.EAST, Direction.WEST -> {
                            leftData.setFace(BlockFace.NORTH, true)
                            leftData.setFace(BlockFace.SOUTH, true)
                            rightData.setFace(BlockFace.NORTH, true)
                            rightData.setFace(BlockFace.SOUTH, true)
                        }
                        else -> {}
                    }
                    leftBlock.setBlockData(leftData, false)
                    rightBlock.setBlockData(rightData, false)

                    getDoorBlock(i, DoorBlockPosition.CENTER).setType(Material.AIR, false)
                }
                Openness.CLOSED -> {
                    val leftBlock = getDoorBlock(i, DoorBlockPosition.LEFT)
                    val rightBlock = getDoorBlock(i, DoorBlockPosition.RIGHT)
                    val centerBlock = getDoorBlock(i, DoorBlockPosition.CENTER)

                    leftBlock.setType(Material.IRON_BARS, false)
                    rightBlock.setType(Material.IRON_BARS, false)
                    centerBlock.setType(Material.IRON_BARS, false)

                    val leftData = leftBlock.blockData as MultipleFacing
                    val rightData = rightBlock.blockData as MultipleFacing
                    val centerData = centerBlock.blockData as MultipleFacing

                    when (PoseFacing.getDirection(location)) {
                        Direction.NORTH, Direction.SOUTH -> {
                            leftData.setFace(BlockFace.WEST, true)
                            leftData.setFace(BlockFace.EAST, true)
                            rightData.setFace(BlockFace.WEST, true)
                            rightData.setFace(BlockFace.EAST, true)
                            centerData.setFace(BlockFace.WEST, true)
                            centerData.setFace(BlockFace.EAST, true)
                        }
                        Direction.EAST, Direction.WEST -> {
                            leftData.setFace(BlockFace.NORTH, true)
                            leftData.setFace(BlockFace.SOUTH, true)
                            rightData.setFace(BlockFace.NORTH, true)
                            rightData.setFace(BlockFace.SOUTH, true)
                            centerData.setFace(BlockFace.NORTH, true)
                            centerData.setFace(BlockFace.SOUTH, true)
                        }
                        else -> {}
                    }
                    leftBlock.setBlockData(leftData, false)
                    rightBlock.setBlockData(rightData, false)
                    centerBlock.setBlockData(centerData, false)
                }
            }
        }
    }

    private fun getDoorTransformation(entity: BlockDisplay, door: DoorBlockPosition, openness: Openness): Transformation {
        if (door == DoorBlockPosition.LEFT && openness == Openness.OPEN) return Transformation(
            /* translation    */ Vector3f(-2.45f, 0.0f, -0.25f),
            /* left rotation  */ entity.transformation.leftRotation,
            /* scale          */ Vector3f(1.0f, DOOR_HEIGHT, 0.5f),
            /* right rotation */ entity.transformation.rightRotation
        )

        if (door == DoorBlockPosition.RIGHT && openness == Openness.OPEN) return Transformation(
            /* translation    */ Vector3f(1.45f, 0.0f, -0.25f),
            /* left rotation  */ entity.transformation.leftRotation,
            /* scale          */ Vector3f(1.0f, DOOR_HEIGHT, 0.5f),
            /* right rotation */ entity.transformation.rightRotation
        )

        if (door == DoorBlockPosition.LEFT && openness == Openness.CLOSED) return Transformation(
            /* translation    */ Vector3f(-1.5f, 0.0f, -0.25f),
            /* left rotation  */ entity.transformation.leftRotation,
            /* scale          */ Vector3f(1.5f, DOOR_HEIGHT, 0.5f),
            /* right rotation */ entity.transformation.rightRotation
        )

        if (door == DoorBlockPosition.RIGHT && openness == Openness.CLOSED) return Transformation(
            /* translation    */ Vector3f(0.0f, 0.0f, -0.25f),
            /* left rotation  */ entity.transformation.leftRotation,
            /* scale          */ Vector3f(1.5f, DOOR_HEIGHT, 0.5f),
            /* right rotation */ entity.transformation.rightRotation
        )

        throw IllegalArgumentException("Invalid door and openness combination: $door, $openness")
    }


    companion object {
        private const val DOOR_HEIGHT = 4.0f
        const val DOOR_DURATION = 40
        val ELEVATOR_KEY = NamespacedKey(SneakyMisc.getInstance(), "elevator_door")

        fun listener(elevators: ElevatorsModule) = object : Listener {
            @EventHandler
            fun onEntityLoad(event: ChunkLoadEvent) {
                val entities = event.chunk.entities
                for (entity in entities) {
                    if (entity is BlockDisplay && entity.persistentDataContainer.has(ELEVATOR_KEY, PersistentDataType.STRING)) {
                        val elevatorData = entity.persistentDataContainer.get(ELEVATOR_KEY, PersistentDataType.STRING)!!

                        if (elevatorData.split(":").size != 2) {
                            SneakyMisc.getInstance().logger.severe("Failed to parse elevator data: $elevatorData")
                            entity.remove()
                            return
                        }
                        val (floorId, doorType) = elevatorData.split(":")

                        val elevatorFloor = elevators.getFloor(floorId)
                        if (elevatorFloor == null) {
                            SneakyMisc.getInstance().logger.severe("Failed to find elevator floor: $floorId")
                            entity.remove()
                            continue
                        }

                        when (doorType) {
                            "left" -> {
                                elevatorFloor.leftDoor = entity
                                entity.transformation = elevatorFloor.getDoorTransformation(entity, DoorBlockPosition.LEFT, if (elevatorFloor.isOpen) Openness.OPEN else Openness.CLOSED)
                            }
                            "right" -> {
                                elevatorFloor.rightDoor = entity
                                entity.transformation = elevatorFloor.getDoorTransformation(entity, DoorBlockPosition.RIGHT, if (elevatorFloor.isOpen) Openness.OPEN else Openness.CLOSED)
                            }
                            else -> {
                                SneakyMisc.getInstance().logger.severe("Failed to parse door type: $doorType")
                                entity.remove()
                            }
                        }
                    }
                }
            }
            @EventHandler
            fun onEntityUnload(event: ChunkUnloadEvent) {
                val entities = event.chunk.entities
                for (entity in entities) {
                    if (entity is BlockDisplay && entity.persistentDataContainer.has(ELEVATOR_KEY, PersistentDataType.STRING)) {
                        val elevatorData = entity.persistentDataContainer.get(ELEVATOR_KEY, PersistentDataType.STRING)!!

                        if (elevatorData.split(":").size != 2) {
                            SneakyMisc.getInstance().logger.severe("Failed to parse elevator data: $elevatorData")
                            entity.remove()
                            return
                        }
                        val (floorId, doorType) = elevatorData.split(":")

                        val elevatorFloor = elevators.getFloor(floorId)
                        if (elevatorFloor == null) {
                            entity.remove()
                            continue
                        }

                        when (doorType) {
                            "left" -> elevatorFloor.leftDoor = null
                            "right" -> elevatorFloor.rightDoor = null
                            else -> {
                                SneakyMisc.getInstance().logger.severe("Failed to parse door type: $doorType")
                                entity.remove()
                            }
                        }
                    }
                }
            }

            @EventHandler
            fun onEntityInteract(event: PlayerInteractEntityEvent) {
                if (event.rightClicked !is ItemFrame) return
                val itemFrame = event.rightClicked as ItemFrame
                if (itemFrame.persistentDataContainer.has(ELEVATOR_KEY, PersistentDataType.STRING)){
                    event.isCancelled = true
                    val data = itemFrame.persistentDataContainer.get(ELEVATOR_KEY, PersistentDataType.STRING) ?: return
                    val elevator = elevators.getElevator(data) ?: return
                    event.player.openInventory(ElevatorGUI(elevator).inventory)
                    return
                }
                if (event.hand != EquipmentSlot.HAND) return
                if (!event.player.hasPermission("sneakymisc.elevatorfloor")) return
                val item = event.player.inventory.itemInMainHand
                val customName = item.itemMeta?.customName() ?: return
                val itemName = PlainTextComponentSerializer.plainText().serialize(customName)
                if (itemName.contains('-')) {
                    val elevatorFloor = elevators.getFloor(itemName) ?: return
                    itemFrame.persistentDataContainer.set(ELEVATOR_KEY, PersistentDataType.STRING, elevatorFloor.id)
                    event.player.sendMessage(Component.text("ElevatorFloor ").append(elevatorFloor.toComponent()).append(Component.text(" linked to item frame")))
                    // place item?
                    itemFrame.isVisible = false
                } else {
                    val elevator = elevators.getElevator(itemName) ?: return
                    itemFrame.persistentDataContainer.set(ELEVATOR_KEY, PersistentDataType.STRING, elevator.name)
                    event.player.sendMessage(Component.text("Elevator ").append(Component.text(elevator.name)).append(Component.text(" linked to item frame")))
                    itemFrame.setItem(ElevatorGUI.getNumber(5994))
                    itemFrame.isVisible = false
                }

                event.isCancelled = true

            }

            @EventHandler
            fun onButtonClick(event: PlayerInteractEvent) {
                if (event.hand != EquipmentSlot.HAND) return
                val clickedBlock = event.clickedBlock ?: return
                if (!Tag.BUTTONS.isTagged(clickedBlock.type)) return
                val itemFrame = clickedBlock.world.getNearbyEntities(clickedBlock.location, 1.0, 1.0, 1.0)
                    .filterIsInstance<ItemFrame>()
                    .firstOrNull { it.persistentDataContainer.has(ELEVATOR_KEY, PersistentDataType.STRING) } ?: return
                val elevatorFloorId = itemFrame.persistentDataContainer.get(ELEVATOR_KEY, PersistentDataType.STRING) ?: return
                val elevator = elevators.getElevator(elevatorFloorId.split("-").first()) ?: return
                if (elevator.inTransit) {
                    clickedBlock.world.playSound(clickedBlock.location, "lom:computer.error", SoundCategory.MASTER, 1f, 1f)
                    return
                }

                if(event.player.isSneaking) {
                    event.player.openInventory(ElevatorGUI(elevator).inventory)
                    return
                }

                val floor = elevators.getFloor(elevatorFloorId) ?: return
                elevator.callTo(floor)
            }
        }

        fun create(floorId: String, location: Location, elevator: Elevator): ElevatorFloor {
            location.apply {
                x = floor(x) + 0.5
                y = floor(y)
                z = floor(z) + 0.5
                yaw = when (PoseFacing.getDirection(yaw)) {
                    Direction.NORTH -> 180.0f
                    Direction.EAST -> -90.0f
                    Direction.SOUTH -> 0.0f
                    Direction.WEST -> 90.0f
                    else -> 45.0f
                }
                pitch = 0.0f
            }
            val elevatorFloor = ElevatorFloor(floorId, location, false, elevator)

            // Spawn doors
            val doorLocation = location.clone().add(location.direction.multiply(3.0))

            val rotation = AxisAngle4f(0.0f, 0.0f, 0.0f, 1.0f)
            val scale = Vector3f(1.5f, DOOR_HEIGHT, 0.5f)
            val leftDoorTranslation = Vector3f(-1.5f, 0.0f, -0.25f)
            val rightDoorTranslation = Vector3f(0.0f, 0.0f, -0.25f)


            elevatorFloor.leftDoor = location.world.spawn(doorLocation, BlockDisplay::class.java) {
                it.block = Material.POLISHED_BASALT.createBlockData()
                it.brightness = Display.Brightness(15, 15)
                it.interpolationDuration = DOOR_DURATION
                it.transformation = Transformation(
                    /* translation    */ leftDoorTranslation.clone() as Vector3f,
                    /* left rotation  */ rotation.clone() as AxisAngle4f,
                    /* scale          */ scale.clone() as Vector3f,
                    /* right rotation */ rotation.clone() as AxisAngle4f
                )
                it.persistentDataContainer.set(ELEVATOR_KEY, PersistentDataType.STRING, "$floorId:left")
            }
            elevatorFloor.rightDoor = location.world.spawn(doorLocation, BlockDisplay::class.java) {
                it.block = Material.POLISHED_BASALT.createBlockData()
                it.brightness = Display.Brightness(15, 15)
                it.interpolationDuration = DOOR_DURATION
                it.transformation = Transformation(
                    /* translation */ rightDoorTranslation.clone() as Vector3f,
                    /* left rotation  */ rotation.clone() as AxisAngle4f,
                    /* scale          */ scale.clone() as Vector3f,
                    /* right rotation */ rotation.clone() as AxisAngle4f
                )
                it.persistentDataContainer.set(ELEVATOR_KEY, PersistentDataType.STRING, "$floorId:right")
            }

            elevatorFloor.setOpenness(Openness.CLOSED)
            return elevatorFloor
        }

        fun deserialize(data: Map<String, Any>, elevator: Elevator): ElevatorFloor {
            val id = data["id"] as String? ?: throw IllegalArgumentException("Failed to parse ElevatorFloor config: Missing id")
            val location = data["location"] as Location? ?: throw IllegalArgumentException("Failed to parse ElevatorFloor config: Missing location")
            val isOpen = data["isOpen"] as Boolean? ?: false
            val elevatorFloor = ElevatorFloor(id, location, isOpen, elevator)
            elevatorFloor.setOpenness(if (isOpen) Openness.OPEN else Openness.CLOSED)
            return elevatorFloor
        }
    }

    override fun serialize(): MutableMap<String, Any> {
        return mutableMapOf(
            "id" to id,
            "location" to location,
            "isOpen" to isOpen
        )
    }

    fun toComponent(): TextComponent {
        return Component.text(id, NamedTextColor.AQUA).hoverEvent(
            Component.join(JoinConfiguration.newlines(),
                Component.text(id).append(Component.text(if (isOpen) " open" else " closed", if (isOpen) NamedTextColor.GREEN else NamedTextColor.RED)),
                Component.text("World: ", NamedTextColor.GRAY).append(Component.text(location.world.name, NamedTextColor.YELLOW)),
                Component.join(JoinConfiguration.commas(true),
                    Component.text("X: ", NamedTextColor.GRAY).append(Component.text(location.x, NamedTextColor.YELLOW)),
                    Component.text("Y: ", NamedTextColor.GRAY).append(Component.text(location.y, NamedTextColor.YELLOW)),
                    Component.text("Z: ", NamedTextColor.GRAY).append(Component.text(location.z, NamedTextColor.YELLOW))
                ),
                Component.text("Facing: ", NamedTextColor.GRAY).append(Component.text(PoseFacing.getDirection(location).name, NamedTextColor.YELLOW)),
                Component.text("Left door: ", NamedTextColor.GRAY).append(Component.text(leftDoor != null, if (leftDoor != null) NamedTextColor.YELLOW else NamedTextColor.RED)),
                Component.text("Right door: ", NamedTextColor.GRAY).append(Component.text(rightDoor != null, if (rightDoor != null) NamedTextColor.YELLOW else NamedTextColor.RED)),
                Component.text("Click to teleport", NamedTextColor.AQUA)
            ).asHoverEvent()
        ).clickEvent(ClickEvent.runCommand("/minecraft:tp @s ${location.x} ${location.y} ${location.z} ${location.yaw} ${location.pitch}"))
    }

    fun cleanup() {
        setOpenness(Openness.OPEN)
        leftDoor?.remove()
        rightDoor?.remove()
    }

    enum class Openness {
        OPEN,
        BARELY,
        HALF,
        CLOSED
    }

}