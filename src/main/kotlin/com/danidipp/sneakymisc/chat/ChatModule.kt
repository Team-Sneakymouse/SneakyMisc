package com.danidipp.sneakymisc.chat

import com.danidipp.sneakymisc.SneakyModule
import com.sk89q.worldguard.protection.flags.StateFlag
import org.bukkit.plugin.Plugin

class ChatModule(plugin: Plugin, chatFlag: StateFlag) : SneakyModule() {
    override val listeners = listOf(ChatListener(plugin, chatFlag), PreventSpamKick())

    companion object {
        val deps = listOf("WorldGuard", "PlaceholderAPI")
    }
}
