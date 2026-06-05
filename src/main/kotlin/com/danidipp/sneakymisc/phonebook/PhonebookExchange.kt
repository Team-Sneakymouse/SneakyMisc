package com.danidipp.sneakymisc.phonebook

import java.util.UUID

@JvmInline
value class PhonebookExchangeSeekingToken(val value: Long)

@JvmInline
value class PhonebookExchangeId(val value: Long)

data class PhonebookExchangeDecisionModel(
    val exchangeId: PhonebookExchangeId,
    val initiator: PhonebookCharacter,
    val target: PhonebookCharacter,
)

private data class PendingPhonebookExchange(
    val exchangeId: PhonebookExchangeId,
    val initiator: PhonebookCharacter,
    val target: PhonebookCharacter,
)

sealed class PhonebookExchangePersistenceResult {
    data class Created(val data: PhonebookData) : PhonebookExchangePersistenceResult()
    data class AlreadyExists(val data: PhonebookData) : PhonebookExchangePersistenceResult()
}

interface PhonebookExchangeStore : PhonebookDataStore {
    fun acceptExchange(initiator: PhonebookCharacter, target: PhonebookCharacter): PhonebookExchangePersistenceResult
}

sealed class PhonebookExchangeEffect {
    data class SendMessage(val accountId: UUID, val message: PhonebookMessage) : PhonebookExchangeEffect()
    data class ScheduleSeekingTimeout(
        val accountId: UUID,
        val token: PhonebookExchangeSeekingToken,
        val delayTicks: Long,
    ) : PhonebookExchangeEffect()
    data class CancelSeekingTimeout(val accountId: UUID, val token: PhonebookExchangeSeekingToken) : PhonebookExchangeEffect()
    data class OpenExchangeDecision(val targetAccountId: UUID, val model: PhonebookExchangeDecisionModel) : PhonebookExchangeEffect()
    data class ScheduleExchangeClose(val targetAccountId: UUID, val exchangeId: PhonebookExchangeId) : PhonebookExchangeEffect()
}

enum class PhonebookExchangeResult {
    StartedSeeking,
    ResetSeeking,
    SeekingTimedOut,
    NoSeeking,
    SoftInvalidHit,
    HardInvalidHit,
    ExchangeOpened,
    ExchangeAccepted,
    ExchangeAlreadyExists,
    ExchangeDeclined,
    NoExchange,
}

data class PhonebookExchangeOutcome(
    val result: PhonebookExchangeResult,
    val effects: List<PhonebookExchangeEffect> = emptyList(),
)

class PhonebookExchangeActions(
    private val phonebooks: PhonebookExchangeStore,
    private val activeCharacters: PhonebookActiveCharacters,
    private val directory: PhonebookDirectory,
) {
    private val seekingByAccount = mutableMapOf<UUID, PhonebookExchangeSeekingToken>()
    private val pendingById = mutableMapOf<PhonebookExchangeId, PendingPhonebookExchange>()
    private var nextSeekingToken = 1L
    private var nextExchangeId = 1L

    fun startSeeking(initiatorAccountId: UUID): PhonebookExchangeOutcome {
        val previousToken = seekingByAccount[initiatorAccountId]
        val token = PhonebookExchangeSeekingToken(nextSeekingToken++)
        seekingByAccount[initiatorAccountId] = token

        val effects = buildList {
            if (previousToken != null) {
                add(PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccountId, previousToken))
                add(PhonebookExchangeEffect.SendMessage(initiatorAccountId, PhonebookMessageCatalog.exchangeSeekingReset()))
            } else {
                add(PhonebookExchangeEffect.SendMessage(initiatorAccountId, PhonebookMessageCatalog.exchangeSeekingStarted()))
            }
            add(PhonebookExchangeEffect.ScheduleSeekingTimeout(initiatorAccountId, token, SEEKING_TIMEOUT_TICKS))
        }

        return PhonebookExchangeOutcome(
            result = if (previousToken == null) PhonebookExchangeResult.StartedSeeking else PhonebookExchangeResult.ResetSeeking,
            effects = effects,
        )
    }

    fun seekingTimedOut(initiatorAccountId: UUID, token: PhonebookExchangeSeekingToken): PhonebookExchangeOutcome {
        if (seekingByAccount[initiatorAccountId] != token) return PhonebookExchangeOutcome(PhonebookExchangeResult.NoSeeking)

        seekingByAccount.remove(initiatorAccountId)
        return PhonebookExchangeOutcome(
            PhonebookExchangeResult.SeekingTimedOut,
            listOf(PhonebookExchangeEffect.SendMessage(initiatorAccountId, PhonebookMessageCatalog.exchangeSeekingTimedOut())),
        )
    }

    fun damageSeekingTarget(
        initiatorAccountId: UUID,
        targetAccountId: UUID?,
        targetBusy: Boolean = false,
    ): PhonebookExchangeOutcome {
        val seekingToken = seekingByAccount[initiatorAccountId] ?: return PhonebookExchangeOutcome(PhonebookExchangeResult.NoSeeking)
        if (targetAccountId == null || targetAccountId == initiatorAccountId) {
            return PhonebookExchangeOutcome(PhonebookExchangeResult.SoftInvalidHit)
        }

        val initiatorCharacterId = activeCharacters.activeCharacter(initiatorAccountId)
        if (initiatorCharacterId == null) {
            return cancelSeeking(
                initiatorAccountId = initiatorAccountId,
                token = seekingToken,
                message = PhonebookMessageCatalog.exchangeInitiatorNoActiveCharacter(),
            )
        }

        val targetCharacterId = activeCharacters.activeCharacter(targetAccountId)
            ?: return cancelSeeking(
                initiatorAccountId = initiatorAccountId,
                token = seekingToken,
                message = PhonebookMessageCatalog.exchangeTargetNoActiveCharacter(),
            )
        if (targetBusy) {
            return cancelSeeking(
                initiatorAccountId = initiatorAccountId,
                token = seekingToken,
                message = PhonebookMessageCatalog.exchangeTargetBusy(),
            )
        }
        val initiator = directory.character(initiatorAccountId, initiatorCharacterId)
            ?: PhonebookCharacter(initiatorAccountId, initiatorCharacterId, initiatorCharacterId.toString())
        val target = directory.character(targetAccountId, targetCharacterId)
            ?: PhonebookCharacter(targetAccountId, targetCharacterId, targetCharacterId.toString())
        if (phonebooks.load().contactBetween(initiatorCharacterId, targetCharacterId) != null) {
            return cancelSeeking(
                initiatorAccountId = initiatorAccountId,
                token = seekingToken,
                message = PhonebookMessageCatalog.exchangeDuplicateContact(target.displayName),
            )
        }
        val exchangeId = PhonebookExchangeId(nextExchangeId++)
        pendingById[exchangeId] = PendingPhonebookExchange(exchangeId, initiator, target)
        seekingByAccount.remove(initiatorAccountId)
        val targetSeekingToken = seekingByAccount.remove(targetAccountId)

        return PhonebookExchangeOutcome(
            PhonebookExchangeResult.ExchangeOpened,
            buildList {
                add(PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccountId, seekingToken))
                if (targetSeekingToken != null) {
                    add(PhonebookExchangeEffect.CancelSeekingTimeout(targetAccountId, targetSeekingToken))
                }
                add(
                    PhonebookExchangeEffect.SendMessage(
                        initiatorAccountId,
                        PhonebookMessageCatalog.exchangeRequestSent(target.displayName),
                    )
                )
                add(
                    PhonebookExchangeEffect.OpenExchangeDecision(
                        targetAccountId,
                        PhonebookExchangeDecisionModel(exchangeId, initiator, target),
                    )
                )
            },
        )
    }

    fun accept(exchangeId: PhonebookExchangeId, targetAccountId: UUID): PhonebookExchangeOutcome {
        val pending = pendingById[exchangeId] ?: return PhonebookExchangeOutcome(PhonebookExchangeResult.NoExchange)
        if (pending.target.accountId != targetAccountId) return PhonebookExchangeOutcome(PhonebookExchangeResult.NoExchange)

        pendingById.remove(exchangeId)
        val persistence = phonebooks.acceptExchange(pending.initiator, pending.target)
        if (persistence is PhonebookExchangePersistenceResult.AlreadyExists) {
            return PhonebookExchangeOutcome(
                PhonebookExchangeResult.ExchangeAlreadyExists,
                listOf(
                    PhonebookExchangeEffect.SendMessage(
                        pending.target.accountId,
                        PhonebookMessageCatalog.exchangeDuplicateContact(pending.initiator.displayName),
                    ),
                    PhonebookExchangeEffect.ScheduleExchangeClose(pending.target.accountId, exchangeId),
                ),
            )
        }
        return PhonebookExchangeOutcome(
            PhonebookExchangeResult.ExchangeAccepted,
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    pending.initiator.accountId,
                    PhonebookMessageCatalog.exchangeAcceptedInitiator(pending.target.displayName),
                ),
                PhonebookExchangeEffect.SendMessage(
                    pending.target.accountId,
                    PhonebookMessageCatalog.exchangeAcceptedTarget(pending.initiator.displayName),
                ),
                PhonebookExchangeEffect.ScheduleExchangeClose(pending.target.accountId, exchangeId),
            ),
        )
    }

    fun decline(exchangeId: PhonebookExchangeId, targetAccountId: UUID): PhonebookExchangeOutcome =
        decline(exchangeId, targetAccountId, scheduleClose = true)

    fun close(exchangeId: PhonebookExchangeId, targetAccountId: UUID): PhonebookExchangeOutcome =
        decline(exchangeId, targetAccountId, scheduleClose = false)

    fun accountQuit(accountId: UUID): PhonebookExchangeOutcome {
        val effects = mutableListOf<PhonebookExchangeEffect>()
        seekingByAccount.remove(accountId)?.let { token ->
            effects += PhonebookExchangeEffect.CancelSeekingTimeout(accountId, token)
        }

        val pendingForTarget = pendingById.values
            .filter { it.target.accountId == accountId }
            .sortedBy { it.exchangeId.value }

        for (pending in pendingForTarget) {
            pendingById.remove(pending.exchangeId)
            effects += PhonebookExchangeEffect.SendMessage(
                pending.initiator.accountId,
                PhonebookMessageCatalog.exchangeTargetLeft(pending.target.displayName),
            )
        }

        val result = if (pendingForTarget.isEmpty()) {
            PhonebookExchangeResult.NoExchange
        } else {
            PhonebookExchangeResult.ExchangeDeclined
        }
        return PhonebookExchangeOutcome(result, effects)
    }

    fun targetQuit(targetAccountId: UUID): PhonebookExchangeOutcome =
        accountQuit(targetAccountId)

    private fun decline(
        exchangeId: PhonebookExchangeId,
        targetAccountId: UUID,
        scheduleClose: Boolean,
    ): PhonebookExchangeOutcome {
        val pending = pendingById[exchangeId] ?: return PhonebookExchangeOutcome(PhonebookExchangeResult.NoExchange)
        if (pending.target.accountId != targetAccountId) return PhonebookExchangeOutcome(PhonebookExchangeResult.NoExchange)

        pendingById.remove(exchangeId)
        val effects = buildList {
            add(
                PhonebookExchangeEffect.SendMessage(
                    pending.initiator.accountId,
                    PhonebookMessageCatalog.exchangeDeclinedInitiator(pending.target.displayName),
                )
            )
            add(
                PhonebookExchangeEffect.SendMessage(
                    pending.target.accountId,
                    PhonebookMessageCatalog.exchangeDeclinedTarget(pending.initiator.displayName),
                )
            )
            if (scheduleClose) {
                add(PhonebookExchangeEffect.ScheduleExchangeClose(pending.target.accountId, exchangeId))
            }
        }
        return PhonebookExchangeOutcome(PhonebookExchangeResult.ExchangeDeclined, effects)
    }

    private fun cancelSeeking(
        initiatorAccountId: UUID,
        token: PhonebookExchangeSeekingToken,
        message: PhonebookMessage,
    ): PhonebookExchangeOutcome {
        seekingByAccount.remove(initiatorAccountId)
        return PhonebookExchangeOutcome(
            PhonebookExchangeResult.HardInvalidHit,
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccountId, token),
                PhonebookExchangeEffect.SendMessage(initiatorAccountId, message),
            ),
        )
    }

    companion object {
        const val SEEKING_TIMEOUT_TICKS = 20L * 30L
    }
}
