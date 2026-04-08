package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyMisc
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class PhonebookSessionManager {
    private val plugin = SneakyMisc.getInstance()
    private val addModes = mutableMapOf<UUID, Pair<PendingAddMode, BukkitTask>>()
    private val requestsByTarget = mutableMapOf<UUID, Pair<PendingExchangeRequest, BukkitTask>>()
    private val targetByRequester = mutableMapOf<UUID, UUID>()

    fun startAddMode(player: Player, onExpire: (Player) -> Unit) {
        cancelAddMode(player.uniqueId)
        val expiresAt = System.currentTimeMillis() + ADD_MODE_DURATION_MS
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            addModes.remove(player.uniqueId) ?: return@Runnable
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
            onExpire(onlinePlayer)
        }, ADD_MODE_DURATION_TICKS)
        addModes[player.uniqueId] = PendingAddMode(player.uniqueId, expiresAt) to task
    }

    fun cancelAddMode(playerUuid: UUID): Boolean {
        val removed = addModes.remove(playerUuid) ?: return false
        removed.second.cancel()
        return true
    }

    fun consumeAddMode(playerUuid: UUID): Boolean = cancelAddMode(playerUuid)

    fun isInAddMode(playerUuid: UUID): Boolean = addModes.containsKey(playerUuid)

    fun createExchangeRequest(
        requester: Player,
        requesterCharacterUuid: String,
        requesterCharacterName: String,
        target: Player,
        targetCharacterUuid: String,
        targetCharacterName: String,
        onExpire: (PendingExchangeRequest) -> Unit,
    ): CreateExchangeRequestResult {
        if (targetByRequester.containsKey(requester.uniqueId)) return CreateExchangeRequestResult.RequesterBusy
        if (requestsByTarget.containsKey(target.uniqueId) || targetByRequester.containsKey(target.uniqueId)) {
            return CreateExchangeRequestResult.TargetBusy
        }

        val request = PendingExchangeRequest(
            requestId = UUID.randomUUID().toString(),
            requesterPlayerUuid = requester.uniqueId,
            requesterCharacterUuid = requesterCharacterUuid,
            requesterCharacterName = requesterCharacterName,
            targetPlayerUuid = target.uniqueId,
            targetCharacterUuid = targetCharacterUuid,
            targetCharacterName = targetCharacterName,
            expiresAtMillis = System.currentTimeMillis() + EXCHANGE_DURATION_MS,
        )
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val removed = removeExchangeRequestForTarget(target.uniqueId) ?: return@Runnable
            onExpire(removed)
        }, EXCHANGE_DURATION_TICKS)

        requestsByTarget[target.uniqueId] = request to task
        targetByRequester[requester.uniqueId] = target.uniqueId
        return CreateExchangeRequestResult.Created(request)
    }

    fun getExchangeRequest(targetPlayerUuid: UUID): PendingExchangeRequest? =
        requestsByTarget[targetPlayerUuid]?.first

    fun removeExchangeRequestForTarget(targetPlayerUuid: UUID): PendingExchangeRequest? {
        val removed = requestsByTarget.remove(targetPlayerUuid) ?: return null
        removed.second.cancel()
        targetByRequester.remove(removed.first.requesterPlayerUuid)
        return removed.first
    }

    fun removeExchangeRequestForRequester(requesterPlayerUuid: UUID): PendingExchangeRequest? {
        val targetUuid = targetByRequester[requesterPlayerUuid] ?: return null
        return removeExchangeRequestForTarget(targetUuid)
    }

    companion object {
        private const val ADD_MODE_DURATION_MS = 30_000L
        private const val EXCHANGE_DURATION_MS = 30_000L
        private const val ADD_MODE_DURATION_TICKS = 20L * 30L
        private const val EXCHANGE_DURATION_TICKS = 20L * 30L
    }
}
