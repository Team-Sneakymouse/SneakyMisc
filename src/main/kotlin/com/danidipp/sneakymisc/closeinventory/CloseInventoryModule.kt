package com.danidipp.sneakymisc.closeinventory

import com.danidipp.sneakymisc.SneakyModule
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.Listener

class CloseInventoryModule : SneakyModule {
    override val commands: List<Command> = listOf(CloseInventoryCommand())
    override val listeners: List<Listener> = listOf()
}

class CloseInventoryCommand(): Command("closeinventory") {
    init {
        description = "Closes a player's inventory"
        usageMessage = "/closeinventory <player>"
        permission = "sneakymisc.closeinventory"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<String>): Boolean {
        if (args.size != 1) return false
        val player = Bukkit.getPlayer(args[0]) ?: return false
        player.closeInventory()
        return true
    }
}