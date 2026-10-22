package com.danidipp.sneakymisc.elevators

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random
import kotlin.math.roundToInt

/** One emitter per elevator travel; existing client particles fade naturally after it stops. */
internal class ElevatorParticles(
    private val plugin: Plugin,
    private val cab: ElevatorCab,
    private val movementY: Double,
    private val travelTicks: Long,
) {
    private var task: BukkitTask? = null
    private var running = false
    private var elapsedTicks = 0L

    fun start() {
        if (running) return
        running = true
        elapsedTicks = 0L
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!running) return@Runnable
            val progress = (elapsedTicks.toDouble() / travelTicks).coerceIn(0.0, 1.0)
            // Accelerate increasingly hard until arrival; no easing out before the sudden stop.
            val speedMultiplier = 1.0 + (END_SPEED_MULTIPLIER - 1) * progress * progress
            val trailDuration = (START_DURATION / speedMultiplier).roundToInt()
            val count = if (movementY == 0.0) START_COUNT else (START_COUNT * speedMultiplier).roundToInt()
            elapsedTicks += 3
            if (cab.players().isEmpty()) return@Runnable
            repeat(count) {
                val alongWall = Random.nextDouble(-2.1, 2.1)
                val (x, z) = when (Random.nextInt(4)) {
                    0 -> -2.1 to alongWall
                    1 -> 2.1 to alongWall
                    2 -> alongWall to -2.1
                    else -> alongWall to 2.1
                }
                if (movementY == 0.0) {
                    val position = cab.location.clone().add(x, Random.nextDouble(MIN_HEIGHT, MAX_HEIGHT), z)
                    position.world.spawnParticle(Particle.DUST, position, 0, 0.0, 0.0, 0.0, 0.0, PURPLE)
                } else {
                    val startY = if (movementY > 0.0) MIN_HEIGHT else MAX_HEIGHT
                    val targetY = if (movementY > 0.0) MAX_HEIGHT else MIN_HEIGHT
                    val position = cab.location.clone().add(x, startY, z)
                    val target = cab.location.clone().add(x, targetY, z)
                    val trail = Particle.Trail(target, PURPLE.color, trailDuration)
                    position.world.spawnParticle(Particle.TRAIL, position, 1, 0.0, 0.0, 0.0, 0.0, trail)
                }
            }
        }, 0L, 3L)
    }

    fun stop() {
        running = false
        task?.cancel()
        task = null
    }

    fun arrival(destination: ElevatorCab) {
        val direction = if (movementY == 0.0) -1.0 else movementY
        val minimumTargetY = if (direction > 0.0) MIN_HEIGHT + ARRIVAL_DISTANCE else MIN_HEIGHT
        val maximumTargetY = if (direction > 0.0) MAX_HEIGHT else MAX_HEIGHT - ARRIVAL_DISTANCE
        val restingPoints = List(START_COUNT * END_SPEED_MULTIPLIER) {
            destination.location.clone().add(
                Random.nextDouble(-2.1, 2.1), Random.nextDouble(minimumTargetY, maximumTargetY), Random.nextDouble(-2.1, 2.1),
            )
        }
        for (target in restingPoints) {
            // Use the final transit speed over a short path before the lingering puffs.
            val position = target.clone().add(0.0, -direction * ARRIVAL_DISTANCE, 0.0)
            val trail = Particle.Trail(target, PURPLE.color, ARRIVAL_DURATION)
            position.world.spawnParticle(Particle.TRAIL, position, 1, 0.0, 0.0, 0.0, 0.0, trail)
        }
        // Trail particles expire at their target. Dust gives the resting points a brief lingering shimmer.
        // This one-shot arrival effect intentionally outlives the transit emitter's stop().
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            for (position in restingPoints) {
                position.world.spawnParticle(Particle.DUST, position, 0, 0.0, 0.0, 0.0, 0.0, PURPLE)
            }
        }, ARRIVAL_DURATION.toLong())
    }

    companion object {
        private const val MIN_HEIGHT = .4
        private const val MAX_HEIGHT = 4.6
        private const val START_DURATION = 32.0
        private const val END_SPEED_MULTIPLIER = 12
        private const val START_COUNT = 6
        private const val ARRIVAL_DURATION = 2
        // Match the actual tick-rounded transit speed, rather than the unrounded target speed.
        private val END_DURATION = (START_DURATION / END_SPEED_MULTIPLIER).roundToInt()
        private val ARRIVAL_DISTANCE = (MAX_HEIGHT - MIN_HEIGHT) / END_DURATION * ARRIVAL_DURATION
        private val PURPLE = Particle.DustOptions(Color.fromRGB(180, 75, 255), .7f)
    }
}
