package com.danidipp.sneakymisc.magicspells

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.nisovin.magicspells.util.magicitems.MagicItems
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class DumpMagicSpellsItemIdsCommand(private val outputPath: Path) {
    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("dumpmsitemids")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes(::dumpItemIds)
            .build()

    private fun dumpItemIds(context: CommandContext<CommandSourceStack>): Int {
        val itemIds = MagicItems.getMagicItemKeys().toList()

        runCatching {
            outputPath.parent?.let { Files.createDirectories(it) }
            Files.writeString(outputPath, itemIds.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()))
        }.onFailure {
            context.source.sender.sendMessage("Failed to write ${outputPath.name}: ${it.message}")
            return 0
        }

        context.source.sender.sendMessage("Wrote ${itemIds.size} MagicSpells item ids to ${outputPath.name}")
        return Command.SINGLE_SUCCESS
    }

    private companion object {
        const val PERMISSION = "sneakymisc.command.dumpmsitemids"
    }
}
