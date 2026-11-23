package com.danidipp.sneakymisc.elevators

import com.sneakybackingtracks.api.PlotMusic
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.util.BoundingBox

/**
 * Plays the ride music through SneakyBackingTracks as plot music: on the Plots voice chat slider, in place of the
 * district music rather than over it, and muted by `/music off`. The voice fills the cab interior, so everyone inside
 * hears the same moment of it and nobody outside does.
 *
 * Only this file names SneakyBackingTracks classes. It must not be touched unless that plugin is enabled.
 */
internal object ElevatorBackingTracksMusic {
    /** `lom:elevator` in SneakySendSound's audio folder: namespace dropped, dots become folders, `.ogg`. */
    const val FILE = "elevator.ogg"

    /** SneakySendSound end reasons that mean the music never played. */
    private val NOT_PLAYED = setOf("LOAD_FAILED", "ZONE_NOT_FOUND", "NO_VOICE_SERVER")

    /** As [ElevatorMusic.start], for [plugin]: SneakyBackingTracks stops the music if that plugin disables. */
    fun start(plugin: Plugin, cab: ElevatorCab, failed: () -> Unit): ElevatorTask? {
        val music = Bukkit.getServicesManager().load(PlotMusic::class.java) ?: return null
        val at = cab.location
        val interior = BoundingBox(at.x - 2.5, at.y, at.z - 2.5, at.x + 2.5, at.y + ElevatorCab.HEIGHT, at.z + 2.5)
        // Volume 0 leaves the loudness to the Plots slider.
        val voice = music.play(plugin, at.world, interior, FILE, 0.0, false) ?: return null
        if (!voice.isActive) return null
        // The file loads in the background, so a missing one is only reported here.
        voice.onEnd { reason -> if (reason in NOT_PLAYED) failed() }
        return ElevatorTask { voice.stop() }
    }
}
