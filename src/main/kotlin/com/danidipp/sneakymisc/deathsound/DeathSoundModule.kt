package com.danidipp.sneakymisc.deathsound

import com.danidipp.sneakymisc.SneakyModule
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

/**
 * Plays `lom:deathsound` where a player dies, so everyone nearby hears it.
 */
class DeathSoundModule : SneakyModule() {
    private val sound = Sound.sound(Key.key("lom", "deathsound"), Sound.Source.PLAYER, 1f, 1f)

    override val listeners: List<Listener> = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onPlayerDeath(event: PlayerDeathEvent) {
            val loc = event.player.location
            loc.world.playSound(sound, loc.x, loc.y, loc.z)
        }
    })
}
