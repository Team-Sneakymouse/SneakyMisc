package com.danidipp.sneakymisc.closeinventory

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.closeinventory.commands.CloseInventoryCommand
import org.bukkit.event.Listener

class CloseInventoryModule : SneakyModule() {
    companion object { val deps = listOf<String>() }
    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(CloseInventoryCommand().build(), "Closes a player's inventory")
    )
}
