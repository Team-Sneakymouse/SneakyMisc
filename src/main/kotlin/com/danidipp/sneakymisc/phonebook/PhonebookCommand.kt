package com.danidipp.sneakymisc.phonebook

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player

class PhonebookCommand(
    private val module: PhonebookModule,
) {
    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("phonebook")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes(::openPhonebook)
            .build()

    private fun openPhonebook(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.sender as? Player ?: run {
            context.source.sender.sendMessage("Only players can use /phonebook")
            return 0
        }

        module.cancelAddMode(player, "Phonebook targeting canceled.")
        module.openPhonebook(player, 0)
        return Command.SINGLE_SUCCESS
    }

    private companion object {
        const val PERMISSION = "sneakymisc.phonebook"
    }
}
