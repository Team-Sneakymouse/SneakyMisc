package com.danidipp.sneakymisc.chat

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent

class PreventSpamKick : Listener {
    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        val reason = PlainTextComponentSerializer.plainText().serialize(event.reason())
        if (event.player.hasPermission("dipp.spambypass") && reason == "Kicked for spamming") {
            event.isCancelled = true
        }
    }
}

