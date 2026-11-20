package com.danidipp.sneakymisc.offhand

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * `/offhand`: swaps the item in your main hand with your off hand.
 *
 * A fae's off hand belongs to their wings (SneakyWings, or SneakyRacials before it, draws them there while it's
 * empty), so a fae can't swap. If a fae has something other than wings in it, the command takes that item out
 * (into the inventory, or dropped at their feet when it's full), which puts the wings back.
 */
class OffhandModule : SneakyModule() {
    companion object {
        val deps = emptyList<String>()
        private val WINGS_KEYS = listOf(NamespacedKey("sneakywings", "wings"), NamespacedKey("sneakyracials", "fae_wings"))
        private val MAGIC_ITEM_KEY = NamespacedKey("magicspells", "magicitem")
    }

    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(
            Commands.literal("offhand")
                .requires { it.sender is Player }
                .executes(::run)
                .build(),
            "Swaps the item in your hand to your off hand"
        )
    )

    private fun run(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.sender as? Player ?: return 0
        val inv = player.inventory
        val off = inv.itemInOffHand

        if (isFae(player)) {
            if (off.type.isAir || isWings(off)) {
                player.sendMessage(Component.text("Your off hand is for your wings.", NamedTextColor.RED))
                return 0
            }
            inv.setItemInOffHand(null)
            for (left in inv.addItem(off).values) player.world.dropItemNaturally(player.location, left)
            player.sendMessage(Component.text("Took the item out of your off hand; your wings are back.", NamedTextColor.GREEN))
            return Command.SINGLE_SUCCESS
        }

        val main = inv.itemInMainHand
        if (main.type.isAir && off.type.isAir) {
            player.sendMessage(Component.text("Both your hands are empty.", NamedTextColor.RED))
            return 0
        }
        inv.setItemInMainHand(off)
        inv.setItemInOffHand(main)
        return Command.SINGLE_SUCCESS
    }

    /** Whether the player's character is a fae, asked of SneakyRacials (by reflection, so it's no build dependency). */
    private fun isFae(player: Player): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("SneakyRacials")) return false
        return try {
            val api = Class.forName("com.sneakymouse.sneakyracials.SneakyRacialsApi")
            api.getMethod("race", Player::class.java).invoke(null, player) == "fae"
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    /** Whether the item is a pair of fae wings (SneakyWings' marked view item, or SneakyRacials' wings item). */
    private fun isWings(item: ItemStack): Boolean {
        val pdc = item.persistentDataContainer
        if (WINGS_KEYS.any { pdc.has(it) }) return true
        return pdc.get(MAGIC_ITEM_KEY, PersistentDataType.STRING) == "item-fae-wings"
    }
}
