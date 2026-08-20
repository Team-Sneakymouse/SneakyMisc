package com.danidipp.sneakymisc.magicspells

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.plugin.java.JavaPlugin

class MagicSpellsModule(plugin: JavaPlugin) : SneakyModule() {
    companion object {
        val deps = listOf("MagicSpells", "WorldGuard")
    }

    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(
            DumpMagicSpellsItemIdsCommand(plugin.dataPath.resolve("ms_item_ids.txt")).build(),
            "Dump loaded MagicSpells item ids",
        ),
        SneakyMiscCommand(
            AssignTargetsCommand().build(),
            "Assign MagicSpells espTarget values within a WorldGuard region",
        )
    )
}
