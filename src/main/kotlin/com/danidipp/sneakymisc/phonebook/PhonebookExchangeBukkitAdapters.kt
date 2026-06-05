package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

class BukkitPhonebookExchangeController(
    private val plugin: Plugin,
    private val actions: PhonebookExchangeActions,
    private val inventoryFactory: PhonebookExchangeInventoryFactory,
) : PhonebookExchangeGateway {
    private val seekingTimeouts = mutableMapOf<Pair<UUID, PhonebookExchangeSeekingToken>, BukkitTask>()

    override fun startSeeking(initiatorAccountId: UUID) {
        apply(actions.startSeeking(initiatorAccountId))
    }

    fun damageSeekingTarget(initiatorAccountId: UUID, targetAccountId: UUID?, targetBusy: Boolean) {
        apply(actions.damageSeekingTarget(initiatorAccountId, targetAccountId, targetBusy))
    }

    fun accept(exchangeId: PhonebookExchangeId, targetAccountId: UUID) {
        apply(actions.accept(exchangeId, targetAccountId))
    }

    fun decline(exchangeId: PhonebookExchangeId, targetAccountId: UUID) {
        apply(actions.decline(exchangeId, targetAccountId))
    }

    fun close(exchangeId: PhonebookExchangeId, targetAccountId: UUID) {
        apply(actions.close(exchangeId, targetAccountId))
    }

    fun targetQuit(targetAccountId: UUID) {
        apply(actions.targetQuit(targetAccountId))
    }

    private fun apply(outcome: PhonebookExchangeOutcome) {
        for (effect in outcome.effects) {
            when (effect) {
                is PhonebookExchangeEffect.SendMessage ->
                    Bukkit.getPlayer(effect.accountId)?.sendMessage(effect.message.asComponent())
                is PhonebookExchangeEffect.ScheduleSeekingTimeout -> {
                    val key = effect.accountId to effect.token
                    seekingTimeouts[key] = Bukkit.getScheduler().runTaskLater(
                        plugin,
                        Runnable {
                            seekingTimeouts.remove(key)
                            apply(actions.seekingTimedOut(effect.accountId, effect.token))
                        },
                        effect.delayTicks,
                    )
                }
                is PhonebookExchangeEffect.CancelSeekingTimeout ->
                    seekingTimeouts.remove(effect.accountId to effect.token)?.cancel()
                is PhonebookExchangeEffect.OpenExchangeDecision ->
                    Bukkit.getPlayer(effect.targetAccountId)?.openInventory(inventoryFactory.create(effect.model))
                is PhonebookExchangeEffect.ScheduleExchangeClose ->
                    Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                            val player = Bukkit.getPlayer(effect.targetAccountId) ?: return@Runnable
                            val holder = player.openInventory.topInventory.holder as? PhonebookExchangeHolder
                            if (holder?.model?.exchangeId == effect.exchangeId) {
                                player.closeInventory()
                            }
                        },
                    )
            }
        }
    }
}

class PhonebookExchangeListener(
    private val controller: BukkitPhonebookExchangeController,
    private val inventoryFactory: PhonebookExchangeInventoryFactory,
) : Listener {
    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damagerPlayer() ?: return
        val target = event.entity as? Player
        controller.damageSeekingTarget(
            initiatorAccountId = damager.uniqueId,
            targetAccountId = target?.uniqueId,
            targetBusy = target?.isBusyForPhonebookExchange() == true,
        )
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? PhonebookExchangeHolder ?: return
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        when (inventoryFactory.selectedDecision(event.currentItem)) {
            PhonebookExchangeDecision.Accept -> controller.accept(holder.model.exchangeId, player.uniqueId)
            PhonebookExchangeDecision.Decline -> controller.decline(holder.model.exchangeId, player.uniqueId)
            null -> Unit
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is PhonebookExchangeHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? PhonebookExchangeHolder ?: return
        val player = event.player as? Player ?: return
        controller.close(holder.model.exchangeId, player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        controller.targetQuit(event.player.uniqueId)
    }

    private fun EntityDamageByEntityEvent.damagerPlayer(): Player? =
        when (val damager = damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        }

    private fun Player.isBusyForPhonebookExchange(): Boolean {
        val topType = openInventory.topInventory.type
        return topType != InventoryType.CRAFTING || !itemOnCursor.isEmptyOrAir()
    }

    private fun ItemStack?.isEmptyOrAir(): Boolean =
        this == null || type == Material.AIR || amount <= 0
}
