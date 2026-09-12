package com.danidipp.sneakymisc.clientupdatereminder

import com.danidipp.sneakymisc.SneakyModule
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class ClientUpdateReminderModule : SneakyModule() {
    companion object {
        val deps = listOf("ViaVersion")
        private val reminder = Component.text(
            "Support for Minecraft 1.21.4 will end soon. Please update via ",
            NamedTextColor.YELLOW,
        ).append(
            Component.text("modpack.rawb.tv", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl("https://modpack.rawb.tv"))
        )
    }

    override val listeners: List<Listener> = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onPlayerJoin(event: PlayerJoinEvent) {
            val version = Via.getAPI().getPlayerProtocolVersion(event.player.uniqueId)
            if (version.isKnown && version.olderThan(ProtocolVersion.v26_2)) {
                event.player.sendMessage(reminder)
            }
        }
    })
}
