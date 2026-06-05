package com.danidipp.sneakymisc.phonebook

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class PhonebookCommand(
    private val accountActions: PhonebookAccountActions,
    private val inventoryFactory: PhonebookInventoryFactory,
) {
    companion object {
        const val PERMISSION = "sneakymisc.phonebook"
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("phonebook")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes(::openPhonebook)
            .then(
                Commands.literal("toggle")
                    .executes { togglePhonebook(it, PhonebookListingMode.Toggle) }
                    .then(Commands.literal("listed").executes { togglePhonebook(it, PhonebookListingMode.Listed) })
                    .then(Commands.literal("unlisted").executes { togglePhonebook(it, PhonebookListingMode.Unlisted) })
            )
            .build()

    private fun openPhonebook(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.sender as? Player
            ?: return failConsole(context)

        return when (accountActions.openPhonebook(BukkitPhonebookViewer(player, inventoryFactory))) {
            PhonebookOpenResult.Opened -> Command.SINGLE_SUCCESS
            PhonebookOpenResult.NoActiveCharacter -> 0
            PhonebookOpenResult.NoPermission -> 0
        }
    }

    private fun togglePhonebook(context: CommandContext<CommandSourceStack>, mode: PhonebookListingMode): Int {
        val player = context.source.sender as? Player
            ?: return failConsole(context)

        return when (accountActions.changeListing(BukkitPhonebookViewer(player, inventoryFactory), mode)) {
            PhonebookListingResult.Listed -> Command.SINGLE_SUCCESS
            PhonebookListingResult.Unlisted -> Command.SINGLE_SUCCESS
            PhonebookListingResult.NoActiveCharacter -> 0
            PhonebookListingResult.NoPermission -> 0
        }
    }

    private fun failConsole(context: CommandContext<CommandSourceStack>): Int {
        context.source.sender.sendMessage(Component.translatable(PhonebookMessageKeys.PLAYER_ONLY))
        return 0
    }
}
