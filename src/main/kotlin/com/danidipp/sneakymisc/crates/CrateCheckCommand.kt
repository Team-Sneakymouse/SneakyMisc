package com.danidipp.sneakymisc.crates

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import com.nisovin.magicspells.MagicSpells
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class CrateCheckCommand {
    private val playerSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        suggest(Bukkit.getOnlinePlayers().map { it.name }.sorted(), builder)
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("cratecheck")
            .requires { it.sender.hasPermission(PERMISSION) }
            .executes { fail(it, USAGE) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests(playerSuggestions)
                    .executes(::checkCrate),
            )
            .build()

    private fun checkCrate(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val playerName = StringArgumentType.getString(context, "player")
        val player = Bukkit.getPlayer(playerName)
            ?: return fail(context, "Player not found")

        val original = player.inventory.itemInMainHand
        val prepared = CrateMigration.prepare(original)
        val definition = prepared.resolution.definitionOrNull(player)
        val shulkerBoxItem = prepared.item
        if (!Tag.SHULKER_BOXES.isTagged(shulkerBoxItem.type)) {
            sender.sendMessage(
                player.name().append(
                    Component.text(" is not holding a shulker box in their main hand.", NamedTextColor.RED),
                ),
            )
            return Command.SINGLE_SUCCESS
        }

        if (definition == null) {
            sender.sendMessage(player.name().append(Component.text(" is not holding a valid crate.", NamedTextColor.RED)))
            return Command.SINGLE_SUCCESS
        }

        val itemMeta = original.itemMeta
        val data = itemMeta.persistentDataContainer
        val id = if (data.has(CrateMigration.backpackKey, PersistentDataType.INTEGER)) {
            data.get(CrateMigration.backpackKey, PersistentDataType.INTEGER)
        } else null
        val crate = id?.let { Crate.get(UUID(0L, it.toLong())) }
        if (crate != null && crate.inventory.viewers.isNotEmpty()) {
            cast(player.name, "item-crate-cratepack-fail")
            return Command.SINGLE_SUCCESS
        }
        if (shulkerBoxItem !== original) {
            try {
                crate?.replaceBackpackItem(shulkerBoxItem)
            } catch (exception: ReflectiveOperationException) {
                return fail(context, "Could not update the backpack for crate migration: ${exception.message}")
            }
            player.inventory.setItemInMainHand(shulkerBoxItem)
        }

        val boxMeta = (shulkerBoxItem.itemMeta as? BlockStateMeta)?.blockState as? org.bukkit.block.ShulkerBox
        if (boxMeta == null) {
            return fail(context, "Could not retrieve shulker box state.")
        }

        if (shulkerBoxItem.amount != 1 || !definition.isFull(boxMeta.inventory.contents)) {
            cast(player.name, "item-crate-cratepack-fail")
            return Command.SINGLE_SUCCESS
        }

        if (MagicSpells.getSpellByInternalName(definition.packingSpell) == null) {
            return fail(context, "Packing spell not found: ${definition.packingSpell}")
        }
        player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        cast(player.name, definition.packingSpell)
        return Command.SINGLE_SUCCESS
    }

    private fun cast(playerName: String, spell: String) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ms cast as $playerName $spell")
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(Component.text(message, NamedTextColor.RED))
        return 0
    }

    private fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.lowercase().startsWith(remainingLowerCase)) suggest(value)
        }
    }.buildFuture()

    private companion object {
        const val PERMISSION = "dipp.commands.cratecheck"
        const val USAGE = "Usage: /cratecheck <player>"
    }
}
