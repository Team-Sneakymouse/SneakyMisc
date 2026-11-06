package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.SneakyMisc
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

/**
 * PlaceholderAPI: what a DClock shows. `%dclock_<clock>%` is its first display's digits (as on the wall, zero-padded
 * to its width), `%dclock_<clock>_<display>%` a given display's, `%dclock_<clock>_value%` the bare number. Empty
 * while the clock has no value yet. Used by SneakyHud's dragon bar.
 */
class DClockPlaceholders(private val module: DClockModule) : PlaceholderExpansion() {
    override fun getIdentifier() = "dclock"
    override fun getAuthor() = SneakyMisc.AUTHORS
    override fun getVersion() = SneakyMisc.VERSION
    override fun persist() = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        val names = module.getClockNames()
        // the longest clock name the params start with, so clock names may hold underscores
        val clock = names.filter { params == it || params.startsWith(it + "_") }.maxByOrNull { it.length } ?: return null
        val rest = params.removePrefix(clock).removePrefix("_")
        return module.placeholder(clock, rest.ifEmpty { null }) ?: ""
    }
}
