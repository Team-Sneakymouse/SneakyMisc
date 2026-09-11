package com.danidipp.sneakymisc.kobold

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.plugin.Plugin

class KoboldModule(plugin: Plugin) : SneakyModule() {
    private val kobold = KoboldCommand(plugin)
    override val commands = listOf(
        SneakyMiscCommand(kobold.build("kobold"), "Speak in kobold"),
        SneakyMiscCommand(kobold.build("ko"), "Speak in kobold"),
    )
    override val listeners = listOf(kobold)

    companion object {
        val deps = listOf("PlaceholderAPI")
    }
}
