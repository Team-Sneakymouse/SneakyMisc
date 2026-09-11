package com.danidipp.sneakymisc.kobold

import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class KoboldCommand(private val plugin: Plugin) : Listener {
    private val koboldPlayers = ConcurrentHashMap<Player, ScheduledTask>()
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val task = koboldPlayers.remove(player) ?: return
        task.cancel()

        val plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message())
        val koboldMessage = plainMessage
            .replace(Regex("[\\w']+"), "kobold")
            .replace(". k", ". K")
            .replaceFirst("k", "K")

        val originalRenderer = event.renderer()
        event.renderer(ChatRenderer { source, displayName, message, viewer ->
            var renderedMessage = message
            val viewerPlayer = viewer as? Player
            if (viewerPlayer != null) {
                val tags = PlaceholderAPI.setPlaceholders(viewerPlayer, "%sneakycharacters_character_tags%")
                    .split(",")
                    .map { it.trim() }
                val isSpy = viewerPlayer.hasPermission("dipp.koboldspy")
                val isKobold = tags.any { it.equals("kobold", ignoreCase = true) }
                if (!isSpy && !isKobold) {
                    renderedMessage = Component.text(koboldMessage)
                }
            }

            originalRenderer.render(source, displayName, renderedMessage, viewer)
        })
    }
    fun build(name: String) = Commands.literal(name)
        .then(Commands.argument("message", StringArgumentType.greedyString())
            .executes { context ->
                val player = context.source.sender as? Player
                if (player == null) {
                    context.source.sender.sendMessage("This command can only be used by a player.")
                    return@executes 0
                }
                koboldPlayers.remove(player)?.cancel()
                val task = Bukkit.getAsyncScheduler().runDelayed(plugin, { expired ->
                    koboldPlayers.remove(player, expired)
                }, 1, TimeUnit.SECONDS)
                koboldPlayers[player] = task
                player.chat(StringArgumentType.getString(context, "message"))
                1
            })
        .build()
}
