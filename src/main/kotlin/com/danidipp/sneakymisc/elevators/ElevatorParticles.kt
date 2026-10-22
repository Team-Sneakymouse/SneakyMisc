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
            val acceleration = progress * progress
            val speed = 1.0 / START_DURATION + (1.0 / END_DURATION - 1.0 / START_DURATION) * acceleration
            val fullFlightDuration = 1.0 / speed
            val emission = if (movementY == 0.0) START_COUNT.toDouble() else START_COUNT + (END_COUNT - START_COUNT) * acceleration
            // Small variations and one-tick emission avoid synchronized sheets around the walls.
            val count = (emission * Random.nextDouble(.75, 1.25)).roundToInt().coerceAtLeast(1)
            elapsedTicks++
            if (cab.players().isEmpty()) return@Runnable
            repeat(count) {
                val alongWall = Random.nextDouble(-2.2, 2.2)
                val wall = Random.nextDouble(1.8, 2.2)
                val (x, z) = when (Random.nextInt(4)) {
                    0 -> -wall to alongWall
                    1 -> wall to alongWall
                    2 -> alongWall to -wall
                    else -> alongWall to wall
                }
                if (movementY == 0.0) {
                    val position = cab.location.clone().add(x, Random.nextDouble(MIN_HEIGHT, MAX_HEIGHT), z)
                    position.world.spawnParticle(Particle.DUST, position, 0, 0.0, 0.0, 0.0, 0.0, PURPLE)
                } else {
                    val fraction = Random.nextDouble(.35, 1.0)
                    val distance = (MAX_HEIGHT - MIN_HEIGHT) * fraction
                    val targetY = if (movementY > 0.0) MAX_HEIGHT else MIN_HEIGHT
                    val startY = targetY - movementY * distance
                    val position = cab.location.clone().add(x, startY, z)
                    val target = cab.location.clone().add(x, targetY, z)
                    val duration = (fullFlightDuration * fraction).roundToInt().coerceAtLeast(1)
                    val trail = Particle.Trail(target, PURPLE.color, duration)
                    position.world.spawnParticle(Particle.TRAIL, position, 1, 0.0, 0.0, 0.0, 0.0, trail)
                }
            }
        }, 0L, 1L)
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
        val restingPoints = List(ARRIVAL_COUNT) {
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
        private const val START_DURATION = 128.0
        private const val END_DURATION = 3
        private const val START_COUNT = 2
        private const val END_COUNT = 48
        private const val ARRIVAL_COUNT = END_COUNT * 3
        private const val ARRIVAL_DURATION = 2
        private val ARRIVAL_DISTANCE = (MAX_HEIGHT - MIN_HEIGHT) / END_DURATION * ARRIVAL_DURATION
        private val PURPLE = Particle.DustOptions(Color.fromRGB(180, 75, 255), .7f)
    }
}
