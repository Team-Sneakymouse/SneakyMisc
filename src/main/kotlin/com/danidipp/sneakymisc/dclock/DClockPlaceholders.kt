package com.danidipp.sneakymisc.dclock

import com.danidipp.sneakymisc.SneakyMisc
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

/**
 * PlaceholderAPI: `%dclock_value%` is the Doom Clock's seconds left as a bare number (used by
 * SneakyHud's dragon bar); `%dclock_<display>%` is a display's digits as on the wall, zero-padded
 * to its width. Empty while the value is not known yet.
 */
class DClockPlaceholders(private val module: DClockModule) : PlaceholderExpansion() {
    override fun getIdentifier() = "dclock"
    override fun getAuthor() = SneakyMisc.AUTHORS
    override fun getVersion() = SneakyMisc.VERSION
    override fun persist() = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (params != "value" && params !in module.getDisplayNames()) return null
        return module.placeholder(params) ?: ""
    }
}
