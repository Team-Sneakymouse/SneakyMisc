package com.danidipp.sneakymisc

import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.Listener

interface SneakyModule {
    abstract val commands: List<SneakyMiscCommand>
    abstract val listeners: List<Listener>

    fun registerBrigadierCommands(commands: Commands) {}
}
