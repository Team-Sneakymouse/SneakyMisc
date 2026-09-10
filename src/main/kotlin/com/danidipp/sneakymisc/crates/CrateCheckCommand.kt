package com.danidipp.sneakymisc.crates

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
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

        val shulkerBoxItem = player.inventory.itemInMainHand
        if (!Tag.SHULKER_BOXES.isTagged(shulkerBoxItem.type)) {
            sender.sendMessage(
                player.name().append(
                    Component.text(" is not holding a shulker box in their main hand.", NamedTextColor.RED),
                ),
            )
            return Command.SINGLE_SUCCESS
        }

        val shulkerModelData = shulkerBoxItem.itemMeta.customModelData
        val shulkerLabel = CrateUtils.getLabel(shulkerBoxItem.type, shulkerModelData)
        if (shulkerLabel == null) {
            sender.sendMessage(player.name().append(Component.text(" is not holding a valid crate.", NamedTextColor.RED)))
            return Command.SINGLE_SUCCESS
        }

        val boxMeta = (shulkerBoxItem.itemMeta as? BlockStateMeta)?.blockState as? org.bukkit.block.ShulkerBox
        if (boxMeta == null) {
            return fail(context, "Could not retrieve shulker box state.")
        }

        if (!validateStacks(boxMeta.inventory.contents, shulkerLabel)) {
            cast(player.name, "item-crate-cratepack-fail")
            return Command.SINGLE_SUCCESS
        }

        val itemMeta = shulkerBoxItem.itemMeta
        val persistentKey = itemMeta.persistentDataContainer.keys.firstOrNull { it.toString() == CMI_BACKPACK_KEY }
        val id = persistentKey?.let { itemMeta.persistentDataContainer.get(it, PersistentDataType.INTEGER) }
        val crate = id?.let { Crate.get(UUID(0L, it.toLong())) }
        if (crate != null && crate.inventory.viewers.isNotEmpty()) {
            cast(player.name, "item-crate-cratepack-fail")
            return Command.SINGLE_SUCCESS
        }

        player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        val crateName = shulkerLabel.itemMeta.displayName()
            ?.let(PlainTextComponentSerializer.plainText()::serialize)
            ?: "cratepack"
        cast(player.name, "item-crate-$crateName-success")
        return Command.SINGLE_SUCCESS
    }

    private fun cast(playerName: String, spell: String) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ms cast as $playerName $spell")
    }

    private fun validateStacks(contents: Array<ItemStack?>, label: ItemStack): Boolean {
        var validStacks = 0
        for (stack in contents) {
            if (stack == null || stack.type == Material.AIR) continue

            val materialCorrect = stack.type == label.type
            val modelDataCorrect = if (
                stack.hasItemMeta() &&
                label.hasItemMeta() &&
                stack.itemMeta.hasCustomModelData() &&
                label.itemMeta.hasCustomModelData()
            ) {
                stack.itemMeta.customModelData == label.itemMeta.customModelData
            } else {
                true
            }

            if (!materialCorrect || !modelDataCorrect || stack.amount != REQUIRED_STACK_SIZE) return false
            validStacks++
        }
        return validStacks == REQUIRED_STACKS
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
        const val CMI_BACKPACK_KEY = "cmilib:cmibackpack"
        const val REQUIRED_STACKS = 9
        const val REQUIRED_STACK_SIZE = 99
    }
}
