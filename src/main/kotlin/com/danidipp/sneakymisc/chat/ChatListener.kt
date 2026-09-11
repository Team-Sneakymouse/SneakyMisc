package com.danidipp.sneakymisc.chat

import org.bukkit.plugin.Plugin
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.ApplicableRegionSet
import com.sk89q.worldguard.protection.flags.StateFlag
import io.papermc.paper.event.player.AsyncChatEvent
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.util.HSVLike
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import kotlin.math.atan2
import kotlin.math.hypot

class ChatListener(private val plugin: Plugin, private val chatFlag: StateFlag) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun chatFlag(event: AsyncChatEvent) {
        val player = event.player
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val chatFrom: ApplicableRegionSet = query.getApplicableRegions(localPlayer.location)
        if (!chatFrom.testState(localPlayer, chatFlag)) {
            player.sendActionBar(Component.text("You cannot chat here.", NamedTextColor.RED))
            event.isCancelled = true
        }
    }

    private enum class MessageRenderType {
        NORMAL,
        SHOUT,
        GLOBAL,
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val player = event.player
        val baseRadius = 12.0 * 12.0 // 12 blocks radius for normal chat
        val shoutRadius = 36.0 * 36.0

        val renderType = when {
            plainText.length > 2 && plainText.startsWith("!!") && player.hasPermission("dipp.chat.shout.global") -> {
                // Global
                (event.message() as? TextComponent)?.let { event.message(componentReplaceFirst(it, "!!", "")) }
                MessageRenderType.GLOBAL
            }
            plainText.length > 1 && plainText.startsWith("!") && player.hasPermission("dipp.chat.shout") -> {
                // Shout
                (event.message() as? TextComponent)?.let { event.message(componentReplaceFirst(it, "!", "")) }

                if (player.health > 10.0 || player.gameMode != GameMode.SURVIVAL) {
                    // Shout successful - Large radius
                    if (player.gameMode == GameMode.SURVIVAL) {
                        Bukkit.getScheduler().runTask(plugin, Runnable { player.damage(10.0) })
                    }
                    event.viewers().removeIf { viewer -> !isInRange(player, viewer, shoutRadius) }
                    sendVoidAlert(player, event.viewers())
                    MessageRenderType.SHOUT
                } else {
                    // Too weak to shout - Normal radius with warning
                    event.viewers().removeIf { viewer -> !isInRange(player, viewer, baseRadius) }
                    player.sendActionBar(Component.text("You are too weak to shout.", NamedTextColor.RED))
                    MessageRenderType.NORMAL
                }
            }
            else -> {
                // Normal
                event.viewers().removeIf { viewer -> !isInRange(player, viewer, baseRadius) }
                sendVoidAlert(player, event.viewers())
                MessageRenderType.NORMAL
            }
        }

        event.viewers().addAll(Bukkit.getOnlinePlayers().filter { it.hasPermission("dipp.chatspy") })

        event.renderer { source, sourceDisplayName, message, viewer ->
            // TODO: OOC format
            val characterName = PlaceholderAPI.setPlaceholders(source, "%sneakycharacters_character_name%")
            var displayName = sourceDisplayName
                .color(NamedTextColor.GRAY)
                .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                    .matchLiteral(source.name).replacement(characterName).build())

            var hoverText = Component.empty()
                .append(Component.text("Account name: ", NamedTextColor.YELLOW))
                .append(Component.text(source.name, NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Voicechat: ", NamedTextColor.YELLOW))
                .append(Component.text(PlaceholderAPI.setPlaceholders(source, "%cond_voicechat-status%"), NamedTextColor.GOLD))

            // Admin view
            var renderedMessage = message
            val viewerPlayer = viewer as? Player
            if (viewerPlayer != null && viewerPlayer.hasPermission("dipp.chatspy")) {
                hoverText = hoverText
                    .append(Component.newline())
                    .append(Component.text("Teleport to player", NamedTextColor.WHITE))
                displayName = displayName.clickEvent(ClickEvent.runCommand("/minecraft:tp ${source.name}"))

                val inRange = when (renderType) {
                    MessageRenderType.GLOBAL -> true
                    MessageRenderType.SHOUT -> isInRange(source, viewer, shoutRadius)
                    MessageRenderType.NORMAL -> isInRange(source, viewer, baseRadius)
                }

                if (!inRange) {
                    val color = coordsToRGB(source.location.blockX, source.location.blockZ)
                    displayName = displayName.color(color)
                    renderedMessage = renderedMessage.color(NamedTextColor.GRAY)
                }
            }

            // Put everything together
            displayName = displayName.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverText))
            val prefix = when (renderType) {
                MessageRenderType.NORMAL -> Component.empty()
                MessageRenderType.SHOUT -> Component.text("! ", NamedTextColor.RED, TextDecoration.BOLD)
                MessageRenderType.GLOBAL -> Component.text("!! ", NamedTextColor.RED, TextDecoration.BOLD)
            }

            // return
            Component.empty()
                .append(prefix)
                .append(displayName)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(renderedMessage)
        }
    }

    private fun componentReplaceFirst(component: TextComponent, search: String, replace: String): TextComponent {
        return when {
            component.content().isNotEmpty() -> component.content(component.content().replaceFirst(search, replace))
            component.children().isNotEmpty() -> {
                val children = component.children().toMutableList()
                val firstChild = children.firstOrNull() as? TextComponent ?: return component
                children[0] = componentReplaceFirst(firstChild, search, replace)
                component.children(children)
            }
            else -> component
        }
    }

    private fun sendVoidAlert(player: Player, viewers: Set<Audience>) {
        val visibleRecipients = viewers.count { viewer ->
            viewer is Player && viewer.trackedBy.contains(player)
        }
        if (visibleRecipients <= 0) {
            player.sendActionBar(Component.text("Nobody can hear you.", NamedTextColor.RED))
        }
    }

    private fun isInRange(source: Player, audience: Audience, radiusSquared: Double): Boolean {
        val viewer = audience as? Player ?: return false
        if (source.world != viewer.world) return false
        return source.location.distanceSquared(viewer.location) <= radiusSquared
    }

    fun coordsToRGB(x: Int, z: Int): TextColor {
        val xMin = 4400
        val xMax = 5600
        val yMin = 4400
        val yMax = 5600

        val scaledX = (2 * (x - xMin) / (xMax - xMin).toDouble()) - 1
        val scaledZ = (2 * (z - yMin) / (yMax - yMin).toDouble()) - 1

        val hue = (Math.toDegrees(atan2(scaledZ, scaledX)) + 360) % 360

        var saturation = hypot(scaledX, scaledZ) % 2.0
        if (saturation > 1.0) saturation = 2.0 - saturation

        val value = 0.75

        val hsv = HSVLike.hsvLike(hue.toFloat() / 360, saturation.toFloat(), value.toFloat())
        return TextColor.color(hsv)
    }
}

