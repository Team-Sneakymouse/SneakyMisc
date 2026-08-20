package com.danidipp.sneakymisc.magicspells

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.variables.variabletypes.PlayerStringVariable
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player

class AssignTargetsCommand {
    private val regionSuggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
        val player = context.source.sender as? Player
        if (player == null) {
            builder.buildFuture()
        } else {
            suggest(regionManager(player.world)?.regions?.keys.orEmpty().sorted(), builder)
        }
    }

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("assigntargets")
            .requires { it.sender.hasPermission(PERMISSION) }
            .then(
                Commands.argument("region", StringArgumentType.word())
                    .suggests(regionSuggestions)
                    .executes(::assignTargets)
            )
            .build()

    private fun assignTargets(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val player = sender as? Player
            ?: return fail(context, "Only players can run /assigntargets because the region is resolved in your current world.")

        val variable = MagicSpells.getVariableManager().getVariable(VARIABLE_NAME)
        if (variable !is PlayerStringVariable) {
            return fail(context, "MagicSpells variable $VARIABLE_NAME is missing or is not a PlayerStringVariable.")
        }

        val regionId = StringArgumentType.getString(context, "region")
        val regionManager = regionManager(player.world)
            ?: return fail(context, "WorldGuard regions are unavailable for world ${player.world.name}.")
        val selectedRegion = regionManager.getRegion(regionId)
            ?: return fail(context, "WorldGuard region $regionId does not exist in world ${player.world.name}.")

        val onlinePlayers = Bukkit.getOnlinePlayers().toList()
        val eligiblePlayers = onlinePlayers.filter {
            it.world == player.world &&
                it.gameMode == GameMode.SURVIVAL &&
                isInRegion(regionManager, it, selectedRegion.id)
        }

        val targetValues = buildTargetValues(onlinePlayers, eligiblePlayers)
        for ((targetPlayer, targetValue) in targetValues) {
            MagicSpells.getVariableManager().set(VARIABLE_NAME, targetPlayer, targetValue)
        }

        val assignedCount = if (eligiblePlayers.size >= 2) eligiblePlayers.size else 0
        val clearedCount = targetValues.count { it.value == NO_TARGET }
        context.source.sender.sendMessage(
            "Assigned $assignedCount target states in region ${selectedRegion.id}; cleared $clearedCount online accounts."
        )
        return Command.SINGLE_SUCCESS
    }

    private fun buildTargetValues(
        onlinePlayers: List<Player>,
        eligiblePlayers: List<Player>,
    ): Map<Player, String> {
        if (eligiblePlayers.size < 2) {
            return onlinePlayers.associateWith { NO_TARGET }
        }

        val shuffledPlayers = eligiblePlayers.shuffled()
        val assignments = shuffledPlayers.mapIndexed { index, targetPlayer ->
            targetPlayer to shuffledPlayers[(index + 1) % shuffledPlayers.size].name
        }.toMap()

        return onlinePlayers.associateWith { assignments[it] ?: NO_TARGET }
    }

    private fun isInRegion(regionManager: RegionManager, player: Player, regionId: String): Boolean {
        val applicableRegions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.location))
        return applicableRegions.any { it.id == regionId }
    }

    private fun regionManager(world: World): RegionManager? {
        val container = WorldGuard.getInstance().platform.regionContainer
        return container.get(BukkitAdapter.adapt(world))
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

    private companion object {
        const val PERMISSION = "sneakymisc.command.assigntargets"
        const val VARIABLE_NAME = "espTarget"
        const val NO_TARGET = "0"
    }
}
