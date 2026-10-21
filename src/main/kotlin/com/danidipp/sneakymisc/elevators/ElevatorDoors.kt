package com.danidipp.sneakymisc.elevators

import com.danidipp.sneakymisc.SneakyMisc
import com.sneakyutils.util.Direction
import com.sneakyutils.util.PoseFacing
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.MultipleFacing
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f

internal class ElevatorDoors(
    val id: String,
    private val cab: ElevatorCab,
    private val schedule: (Long, Runnable) -> BukkitTask = { delay, action ->
        Bukkit.getScheduler().runTaskLater(SneakyMisc.getInstance(), action, delay)
    },
) {
    val location: Location get() = cab.location
    private var leftDoor: BlockDisplay? = null
    private var rightDoor: BlockDisplay? = null
    private val tasks = mutableSetOf<BukkitTask>()
    private var revision = 0L
    private var state = DoorState.CLOSED
    val isOpen: Boolean get() = state == DoorState.OPEN || state == DoorState.OPENING

    private enum class DoorState { CLOSED, OPENING, OPEN, CLOSING }

    private fun cancelTasks() {
        revision++
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    private fun later(delay: Long, action: () -> Unit) {
        val expected = revision
        lateinit var task: BukkitTask
        task = schedule(delay, Runnable {
            tasks.remove(task)
            if (revision == expected) action()
        })
        tasks.add(task)
    }

    internal fun open() {
        if (state == DoorState.OPENING) return
        val alreadyOpen = state == DoorState.OPEN
        cancelTasks()
        if (alreadyOpen) {
            scheduleClose()
            return
        }
        ensureDoors()
        state = DoorState.OPENING
        val doorLocation = location.clone().add(location.direction.multiply(3.0))
        doorLocation.world.playSound(doorLocation, "lom:elevatorping", SoundCategory.MASTER, 1f, 1f)
        later(20) {
            animate(Openness.OPEN)
            setOpenness(Openness.HALF)
            later(DOOR_DURATION / 3L - 2) { setOpenness(Openness.BARELY) }
            later(2 * DOOR_DURATION / 3L - 2) { setOpenness(Openness.OPEN) }
            later(DOOR_DURATION.toLong()) {
                state = DoorState.OPEN
            }
            scheduleClose()
        }
    }

    private fun scheduleClose() { later(200) { close() } }

    internal fun close(closed: () -> Unit = {}): ElevatorTask {
        cancelTasks()
        ensureDoors()
        val expected = revision
        if (state == DoorState.CLOSED) {
            later(1, closed)
        } else {
            state = DoorState.CLOSING
            animate(Openness.CLOSED)
            later(DOOR_DURATION / 3L + 2) { setOpenness(Openness.BARELY) }
            later(2 * DOOR_DURATION / 3L + 2) { setOpenness(Openness.HALF) }
            later(DOOR_DURATION.toLong()) {
                setOpenness(Openness.CLOSED)
                state = DoorState.CLOSED
                closed()
            }
        }
        return ElevatorTask { if (revision == expected) cancelTasks() }
    }

    private fun animate(openness: Openness) {
        listOf(leftDoor to DoorBlockPosition.LEFT, rightDoor to DoorBlockPosition.RIGHT).forEach { (door, side) ->
            door?.let {
                it.interpolationDelay = -1
                it.transformation = getDoorTransformation(it, side, openness)
            }
        }
    }

    internal fun ensureDoors() {
        val doorLocation = location.clone().add(location.direction.multiply(3.0))
        val entities = doorLocation.chunk.entities.filterIsInstance<BlockDisplay>()
        fun findOrSpawn(side: DoorBlockPosition): BlockDisplay {
            val key = "$id:${side.name.lowercase()}"
            val matching = entities.filter { it.persistentDataContainer.get(ELEVATOR_KEY, PersistentDataType.STRING) == key }
            matching.drop(1).forEach { it.remove() }
            val door = matching.firstOrNull() ?: doorLocation.world.spawn(doorLocation, BlockDisplay::class.java) {
                it.block = Material.POLISHED_BASALT.createBlockData()
                it.brightness = Display.Brightness(15, 15)
                it.interpolationDuration = DOOR_DURATION
                it.transformation = Transformation(Vector3f(), AxisAngle4f(), Vector3f(1.5f, DOOR_HEIGHT, 0.5f), AxisAngle4f())
                it.persistentDataContainer.set(ELEVATOR_KEY, PersistentDataType.STRING, key)
            }
            door.transformation = getDoorTransformation(door, side, if (isOpen) Openness.OPEN else Openness.CLOSED)
            return door
        }
        if (leftDoor?.isValid != true) leftDoor = findOrSpawn(DoorBlockPosition.LEFT)
        if (rightDoor?.isValid != true) rightDoor = findOrSpawn(DoorBlockPosition.RIGHT)
    }

    internal fun initialize() {
        state = DoorState.CLOSED
        ensureDoors()
        setOpenness(Openness.CLOSED)
    }

    internal fun stop() {
        cancelTasks()
        state = DoorState.CLOSED
        animate(Openness.CLOSED)
        setOpenness(Openness.CLOSED)
    }

    internal fun cleanup() {
        cancelTasks()
        ensureDoors()
        setOpenness(Openness.OPEN)
        leftDoor?.remove()
        rightDoor?.remove()
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


    private enum class Openness { OPEN, BARELY, HALF, CLOSED }

    companion object {
        private const val DOOR_HEIGHT = 4f
        const val DOOR_DURATION = 40
        val ELEVATOR_KEY = NamespacedKey("sneakymisc", "elevator_door")
    }
}
