package com.danidipp.sneakymisc

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.Listener

abstract class SneakyModule {
    open val commands: List<SneakyMiscCommand> = listOf()
    open val listeners: List<Listener> = listOf()

    fun registerBrigadierCommands(commands: Commands) {}
}
