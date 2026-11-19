package com.danidipp.sneakymisc.ptimereset

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Resets a player's personal time when they log in, so a ptime set in an earlier session
 * (e.g. a stuck night or frozen time) never carries over. Uses CMI's `-s` flag so the
 * player sees no message.
 */
class PTimeResetModule : SneakyModule() {
    companion object {
        val deps = listOf("CMI")
    }

    override val listeners: List<Listener> = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onPlayerJoin(event: PlayerJoinEvent) {
            val player = event.player
            // One tick later, so CMI's own join handling has run first.
            player.scheduler.runDelayed(SneakyMisc.getInstance(), {
                if (player.isOnline) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi ptime reset ${player.name} -s")
                }
            }, null, 1L)
        }
    })
}
