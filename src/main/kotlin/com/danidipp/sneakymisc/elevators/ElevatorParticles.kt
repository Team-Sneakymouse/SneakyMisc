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
                val position = cab.location.clone().add(x, Random.nextDouble(.4, 3.6), z)
                // Count zero sends a single particle with a velocity rather than a random spread.
                position.world.spawnParticle(Particle.DUST, position, 0, 0.0, movementY, 0.0, .2, PURPLE)
            }
        }, 0L, 3L)
    }

    fun stop() {
        running = false
        task?.cancel()
        task = null
    }

    companion object {
        private val PURPLE = Particle.DustOptions(Color.fromRGB(180, 75, 255), .7f)

        fun arrival(cab: ElevatorCab) {
            repeat(18) {
                val position = cab.location.clone().add(
                    Random.nextDouble(-2.1, 2.1), Random.nextDouble(.4, 3.6), Random.nextDouble(-2.1, 2.1),
                )
                position.world.spawnParticle(Particle.DUST, position, 0, 0.0, 0.0, 0.0, 0.0, PURPLE)
            }
        }
    }
}
