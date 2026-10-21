package com.danidipp.sneakymisc.elevators

import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

/** One emitter per elevator travel; existing client particles fade naturally after it stops. */
internal class ElevatorParticles(
    private val plugin: Plugin,
    private val cab: ElevatorCab,
    private val movementY: Double,
) {
    private var task: BukkitTask? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!running || cab.players().isEmpty()) return@Runnable
            repeat(6) {
                val alongWall = Random.nextDouble(-2.1, 2.1)
                val (x, z) = when (Random.nextInt(4)) {
                    0 -> -2.1 to alongWall
                    1 -> 2.1 to alongWall
                    2 -> alongWall to -2.1
                    else -> alongWall to 2.1
                }
                if (movementY == 0.0) {
                    val position = cab.location.clone().add(x, Random.nextDouble(.4, 3.6), z)
                    position.world.spawnParticle(Particle.DUST, position, 0, 0.0, 0.0, 0.0, 0.0, PURPLE)
                } else {
                    val startY = if (movementY > 0.0) .4 else 3.6
                    val targetY = if (movementY > 0.0) 3.6 else .4
                    val position = cab.location.clone().add(x, startY, z)
                    val target = cab.location.clone().add(x, targetY, z)
                    val trail = Particle.Trail(target, PURPLE.color, Random.nextInt(16, 25))
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
        val restingPoints = List(18) {
            destination.location.clone().add(
                Random.nextDouble(-2.1, 2.1), Random.nextDouble(1.0, 3.0), Random.nextDouble(-2.1, 2.1),
            )
        }
        for (target in restingPoints) {
            // Continue the transit flow, but travel only a short distance as the elevator settles.
            val position = target.clone().add(0.0, if (movementY == 0.0) .6 else -movementY * .6, 0.0)
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
        private const val ARRIVAL_DURATION = 12
        private val PURPLE = Particle.DustOptions(Color.fromRGB(180, 75, 255), .7f)
    }
}
