package com.danidipp.sneakymisc.forceloadregion

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.World

internal class ForceLoadRegionCommand {
    private val regionSuggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
        suggest(regionManager(context.source.location.world)?.regions?.keys.orEmpty().sorted(), builder)
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("forceloadregion")
            .requires { it.sender.hasPermission(PERMISSION) }
            .then(
                Commands.argument("region", StringArgumentType.word())
                    .suggests(regionSuggestions)
                    .executes(::forceLoadRegion)
            )
            .build()

    private fun forceLoadRegion(context: CommandContext<CommandSourceStack>): Int {
        val world = context.source.location.world
        val regionId = StringArgumentType.getString(context, "region")
        val regionManager = regionManager(world)
            ?: return fail(context, "WorldGuard regions are unavailable for world ${world.name}.")
        val region = regionManager.getRegion(regionId)
            ?: return fail(context, "WorldGuard region $regionId does not exist in world ${world.name}.")
        if (!region.isPhysicalArea) {
            return fail(context, "WorldGuard region ${region.id} does not have a finite area.")
        }

        val minimum = region.minimumPoint
        val maximum = region.maximumPoint
        val chunks = FullyContainedChunks.find(minimum.x(), maximum.x(), minimum.z(), maximum.z()) { x, z ->
            region.contains(BlockVector2.at(x, z))
        }
        var added = 0
        for (chunk in chunks) {
            if (!world.isChunkForceLoaded(chunk.x, chunk.z)) {
                world.setChunkForceLoaded(chunk.x, chunk.z, true)
                added++
            }
        }

        val alreadyLoaded = chunks.size - added
        context.source.sender.sendMessage(
            "Force-loaded $added chunks fully inside region ${region.id} in ${world.name}; " +
                "$alreadyLoaded were already force-loaded."
        )
        return Command.SINGLE_SUCCESS
    }

    private fun regionManager(world: World): RegionManager? =
        WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))

    private fun fail(context: CommandContext<CommandSourceStack>, message: String): Int {
        context.source.sender.sendMessage(message)
        return 0
    }

    private fun suggest(values: Iterable<String>, builder: SuggestionsBuilder) = builder.apply {
        for (value in values) {
            if (value.lowercase().startsWith(remainingLowerCase)) suggest(value)
        }
    }.buildFuture()

    private companion object {
        const val PERMISSION = "sneakymisc.command.forceloadregion"
    }
}
