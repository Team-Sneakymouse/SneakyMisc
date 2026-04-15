package com.danidipp.sneakymisc.paintings

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.Registry
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

class PaintingsCommand {
    private val paintingSuggestions = SuggestionProvider<CommandSourceStack> { _, builder ->
        suggest(customPaintingKeys().map { it.asString() }.sorted(), builder)
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("paintings")
            .requires { source ->
                source.sender.hasPermission(INFO_PERMISSION) || source.sender.hasPermission(GET_PERMISSION)
            }
            .executes { fail(it, "Usage: /paintings <info|get>") }
            .then(
                Commands.literal("info")
                    .requires { it.sender.hasPermission(INFO_PERMISSION) }
                    .executes(::showInfo)
            )
            .then(
                Commands.literal("get")
                    .requires { it.sender.hasPermission(GET_PERMISSION) }
                    .then(
                        Commands.argument("painting_id", StringArgumentType.word())
                            .suggests(paintingSuggestions)
                            .executes { givePainting(it, 1) }
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes { givePainting(it, IntegerArgumentType.getInteger(it, "amount")) }
                            )
                    )
            )
            .build()

    private fun showInfo(context: CommandContext<CommandSourceStack>): Int {
        val registered = customPaintingKeys().map { it.asString() }.sorted()
        val sender = context.source.sender
        sender.sendMessage("Registered non-vanilla paintings: ${registered.size}")
        if (registered.isEmpty()) {
            sender.sendMessage("No non-vanilla paintings are currently registered")
        } else {
            sender.sendMessage(registered.joinToString(", "))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun givePainting(context: CommandContext<CommandSourceStack>, amount: Int): Int {
        val player = context.source.sender as? Player ?: return fail(context, "Only players can use /paintings get")
        val paintingId = StringArgumentType.getString(context, "painting_id")
        val paintingKey = NamespacedKey.fromString(paintingId)
            ?: return fail(context, "Invalid painting id '$paintingId'")
        if (paintingKey.namespace == NamespacedKey.MINECRAFT) {
            return fail(context, "Painting '$paintingId' is in the minecraft namespace and is not available through this command")
        }

        val painting = paintingRegistry().get(paintingKey)
        val resolvedKey = painting?.let { paintingRegistry().getKey(it) }
        if (resolvedKey == null || resolvedKey.namespace == NamespacedKey.MINECRAFT) {
            return fail(context, "Unknown non-vanilla painting '$paintingId'")
        }

        val item = runCatching {
            Bukkit.getItemFactory().createItemStack(
                """minecraft:painting[entity_data={"id":"minecraft:painting","variant":"${paintingKey.asString()}"}]"""
            )
        }.getOrElse {
            return fail(context, "Failed to create painting item '$paintingId': ${it.message}")
        }

        item.amount = amount
        val overflow = player.inventory.addItem(item)
        val overflowAmount = overflow.values.sumOf { it.amount }
        for (leftover in overflow.values) {
            player.world.dropItemNaturally(player.location, leftover)
        }

        if (overflowAmount > 0) {
            player.sendMessage("Gave ${amount - overflowAmount}x $paintingId and dropped ${overflowAmount}x overflow at your feet")
        } else {
            player.sendMessage("Gave ${amount}x $paintingId")
        }
        return Command.SINGLE_SUCCESS
    }

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    private fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.lowercase().startsWith(remainingLowerCase)) {
                suggest(value)
            }
        }
    }.buildFuture()

    private fun paintingRegistry(): Registry<org.bukkit.Art> =
        RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT)

    private fun customPaintingKeys(): List<NamespacedKey> =
        paintingRegistry().asSequence()
            .mapNotNull { paintingRegistry().getKey(it) }
            .filter { it.namespace != NamespacedKey.MINECRAFT }
            .toList()

    private companion object {
        const val INFO_PERMISSION = "sneakymisc.command.paintings.info"
        const val GET_PERMISSION = "sneakymisc.command.paintings.get"
    }
}
