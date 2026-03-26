package com.danidipp.sneakymisc.metaoverlayhelper.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.sneakycharactermanager.paper.handlers.character.Character
import org.bukkit.Bukkit
import java.util.UUID

class MetaOverlayHelperCommand {
    private companion object {
        const val PERMISSION = "sneakymisc.metaoverlayhelper"
        const val RESPONSE_PREFIX = "[MetaOverlayHelper] "
        const val USAGE = "Usage: /metaoverlayhelper charid <uuid>"
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("metaoverlayhelper")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { fail(it, USAGE) }
            .then(
                Commands.literal("charid")
                    .executes { fail(it, USAGE) }
                    .then(
                        Commands.argument("uuid", StringArgumentType.word())
                            .executes(::showCharacterId)
                    )
            ).build()

    private fun showCharacterId(context: CommandContext<CommandSourceStack>): Int {
        if (!Bukkit.getPluginManager().isPluginEnabled("SneakyCharacterManager")) {
            return fail(context, "${RESPONSE_PREFIX}SneakyCharacterManager plugin is not enabled")
        }

        val playerUuid = runCatching { UUID.fromString(stringArg(context, "uuid")) }.getOrNull()
            ?: return fail(context, "${RESPONSE_PREFIX}Invalid UUID")
        val player = Bukkit.getPlayer(playerUuid)
            ?: return fail(context, "${RESPONSE_PREFIX}Player not found")
        val character = Character.get(player)
            ?: return fail(context, "${RESPONSE_PREFIX}Character not found")

        return success(context, "$RESPONSE_PREFIX${character.characterUUID}")
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    private fun success(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun stringArg(
        context: CommandContext<CommandSourceStack>,
        name: String,
    ): String = StringArgumentType.getString(context, name)
}
