package com.danidipp.sneakymisc.nophysics

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent


class NoPhysicsModule: SneakyModule(), Listener {
    companion object {
        val deps = listOf<String>()
    }
    override val listeners: List<Listener> = listOf(this)

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        val type = event.getBlock().getType()

        if (type == Material.SAND || type == Material.GRAVEL) {
            event.setCancelled(true)
        }
    }
}