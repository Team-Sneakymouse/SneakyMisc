package com.danidipp.sneakymisc.chat

import com.danidipp.sneakymisc.SneakyModule
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import org.bukkit.plugin.Plugin

class ChatModule(plugin: Plugin, chatFlag: StateFlag) : SneakyModule() {
    override val listeners = listOf(ChatListener(plugin, chatFlag), PreventSpamKick())

    companion object {
        val deps = listOf("WorldGuard", "PlaceholderAPI")

        fun registerChatFlag(): StateFlag {
            val registry = WorldGuard.getInstance().flagRegistry
            val flag = StateFlag("chat", true)
            try {
                registry.register(flag)
                return flag
            } catch (exception: FlagConflictException) {
                return registry.get("chat") as? StateFlag
                    ?: throw IllegalStateException("WorldGuard flag 'chat' is not a state flag", exception)
            }
        }
    }
}
