package com.danidipp.sneakymisc.deathsound

import com.danidipp.sneakymisc.SneakyModule
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.lang.reflect.Method

/**
 * Plays a death sound where a player dies, so everyone nearby hears it. The sound follows the
 * character's gender (SneakyCharacterManager `/chargender`) and whether they're an elf (SneakyRacials):
 *
 * | | feminine | nonbinary | masculine / unset |
 * |---|---|---|---|
 * | elf | `death.avfdie` | `death.sharfemale_die` | `death.avmdie` |
 * | everyone else | `death.kobfdie` | `death.frostalfmale_die` | `deathsound` |
 *
 * An elf with no gender set gets the plain `deathsound`.
 */
class DeathSoundModule : SneakyModule() {
    override val listeners: List<Listener> = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onPlayerDeath(event: PlayerDeathEvent) {
            val player = event.player
            val loc = player.location
            val sound = Sound.sound(Key.key("lom", soundFor(player)), Sound.Source.PLAYER, 1f, 1f)
            loc.world.playSound(sound, loc.x, loc.y, loc.z)
        }
    })

    private fun soundFor(player: Player): String {
        val gender = gender(player)
        val elf = race(player).equals("elf", ignoreCase = true)
        return when {
            elf && gender == "feminine" -> "death.avfdie"
            elf && gender == "nonbinary" -> "death.sharfemale_die"
            elf && gender == "masculine" -> "death.avmdie"
            gender == "feminine" -> "death.kobfdie"
            gender == "nonbinary" -> "death.frostalfmale_die"
            else -> "deathsound"
        }
    }

    private fun gender(player: Player): String =
        papi(player, "%sneakycharacters_character_gender%").lowercase()

    /** Race from SneakyRacials' API (which also knows MagicSpells races), else the character's race tag. */
    private fun race(player: Player): String {
        val fromApi = runCatching { raceMethod()?.invoke(null, player) as String? }.getOrNull()
        return fromApi ?: papi(player, "%sneakycharacters_character_tag_race%")
    }

    private fun papi(player: Player, placeholder: String): String {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return ""
        val out = PlaceholderAPI.setPlaceholders(player, placeholder)
        return if (out == placeholder) "" else out.trim()
    }

    private var raceMethodResolved = false
    private var raceMethodCache: Method? = null

    private fun raceMethod(): Method? {
        if (raceMethodResolved) return raceMethodCache
        val plugin = Bukkit.getPluginManager().getPlugin("SneakyRacials")?.takeIf { it.isEnabled } ?: return null
        raceMethodResolved = true
        raceMethodCache = runCatching {
            plugin.javaClass.classLoader.loadClass("com.sneakymouse.sneakyracials.SneakyRacialsApi")
                .getMethod("race", Player::class.java)
        }.getOrNull()
        return raceMethodCache
    }
}
