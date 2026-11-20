package com.danidipp.sneakymisc.worldpaste

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.plugin.Plugin

internal class WorldPasteModule(plugin: Plugin) : SneakyModule() {
    override val commands = listOf(
        SneakyMiscCommand(
            WorldPasteCommand(plugin).build(),
            "Paste a preset of schematics over an area, replacing its blocks and entities",
        )
    )

    companion object {
        // FAWE specifically: the paste runs off the main thread, which plain WorldEdit doesn't allow.
        val deps = listOf("FastAsyncWorldEdit")
    }
}
