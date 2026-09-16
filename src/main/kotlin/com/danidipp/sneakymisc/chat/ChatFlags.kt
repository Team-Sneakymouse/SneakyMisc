package com.danidipp.sneakymisc.chat

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException

object ChatFlags {
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
