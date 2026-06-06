package com.danidipp.sneakymisc.phonebook

import com.mojang.brigadier.arguments.StringArgumentType
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
    private val debug: PhonebookDebugCommandAdapter,
) {
    companion object {
        const val PERMISSION = "sneakymisc.phonebook"
        const val DEBUG_PERMISSION = "sneakymisc.phonebook.debug"
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("phonebook")
            .executes(::openPhonebook)
            .then(
                Commands.literal("toggle")
                    .requires { it.sender.hasPermission(PERMISSION) }
                    .executes { togglePhonebook(it, PhonebookListingMode.Toggle) }
                    .then(Commands.literal("listed").executes { togglePhonebook(it, PhonebookListingMode.Listed) })
                    .then(Commands.literal("unlisted").executes { togglePhonebook(it, PhonebookListingMode.Unlisted) })
            )
            .then(
                Commands.literal("debug")
                    .requires { it.sender.hasPermission(DEBUG_PERMISSION) }
                    .executes(::debugSelf)
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .executes { debugTarget(it, StringArgumentType.getString(it, "player")) }
                    )
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

    private fun debugSelf(context: CommandContext<CommandSourceStack>): Int =
        sendDebugResult(context, debug.debugSelf(context.debugActor()))

    private fun debugTarget(context: CommandContext<CommandSourceStack>, targetName: String): Int =
        sendDebugResult(context, debug.debugTarget(context.debugActor(), targetName))

    private fun CommandContext<CommandSourceStack>.debugActor(): PhonebookDebugCommandActor =
        PhonebookDebugCommandActor(
            accountId = (source.sender as? Player)?.uniqueId,
            permitted = source.sender.hasPermission(DEBUG_PERMISSION),
        )

    private fun sendDebugResult(context: CommandContext<CommandSourceStack>, result: PhonebookDebugCommandResult): Int =
        when (result) {
            is PhonebookDebugCommandResult.Sent -> {
                result.components.forEach(context.source.sender::sendMessage)
                Command.SINGLE_SUCCESS
            }
            PhonebookDebugCommandResult.NoPermission -> 0
            PhonebookDebugCommandResult.ConsoleRequiresTarget -> {
                context.source.sender.sendMessage(Component.text("Usage: /phonebook debug <player>"))
                0
            }
            is PhonebookDebugCommandResult.TargetNotOnline -> {
                context.source.sender.sendMessage(Component.text("No online Account found for ${result.targetName}."))
                0
            }
        }
}
