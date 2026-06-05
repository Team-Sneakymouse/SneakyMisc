package com.danidipp.sneakymisc.phonebook

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class PhonebookCommand(
    private val openHandler: PhonebookOpenHandler,
    private val inventoryFactory: PhonebookInventoryFactory,
) {
    companion object {
        const val PERMISSION = "sneakymisc.phonebook"
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("phonebook")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes(::openPhonebook)
            .build()

    private fun openPhonebook(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.sender as? Player
            ?: return failConsole(context)

        return when (openHandler.openPhonebook(BukkitPhonebookViewer(player, inventoryFactory))) {
            PhonebookOpenResult.Opened -> Command.SINGLE_SUCCESS
            PhonebookOpenResult.NoActiveCharacter -> 0
            PhonebookOpenResult.NoPermission -> 0
        }
    }

    private fun failConsole(context: CommandContext<CommandSourceStack>): Int {
        context.source.sender.sendMessage(Component.translatable(PhonebookMessageKeys.PLAYER_ONLY))
        return 0
    }
}
