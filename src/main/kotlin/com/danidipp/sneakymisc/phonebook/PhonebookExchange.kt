package com.danidipp.sneakymisc.phonebook

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class PhonebookExchange(
    private val service: PhonebookService,
    private val sessions: PhonebookSessionManager,
) {
    fun startTargeting(player: Player, onExpire: (Player) -> Unit) {
        sessions.startAddMode(player, onExpire)
    }

    fun cancelTargeting(playerUuid: UUID): Boolean =
        sessions.cancelAddMode(playerUuid)

    fun targetContact(
        requester: Player,
        target: Player,
        onExpire: (PendingExchangeRequest) -> Unit,
    ): TargetContactExchangeResult {
        if (!sessions.isInAddMode(requester.uniqueId)) return TargetContactExchangeResult.NotTargeting
        sessions.consumeAddMode(requester.uniqueId)

        if (requester.uniqueId == target.uniqueId) return TargetContactExchangeResult.SelfTarget

        val requesterCharacter = service.getCurrentCharacter(requester)
            ?: return TargetContactExchangeResult.RequesterMissingCharacter
        val targetCharacter = service.getCurrentCharacter(target)
            ?: return TargetContactExchangeResult.TargetMissingCharacter

        if (service.containsContact(requester.uniqueId, targetCharacter.characterUUID)) {
            return TargetContactExchangeResult.AlreadyContact(targetCharacter.name)
        }

        return when (
            val result = sessions.createExchangeRequest(
                requester = requester,
                requesterCharacterUuid = requesterCharacter.characterUUID,
                requesterCharacterName = requesterCharacter.name,
                target = target,
                targetCharacterUuid = targetCharacter.characterUUID,
                targetCharacterName = targetCharacter.name,
                onExpire = onExpire,
            )
        ) {
            is CreateExchangeRequestResult.Created -> TargetContactExchangeResult.Created(result.request)
            CreateExchangeRequestResult.RequesterBusy -> TargetContactExchangeResult.RequesterBusy
            CreateExchangeRequestResult.TargetBusy -> TargetContactExchangeResult.TargetBusy
        }
    }

    fun targetHasRequest(targetPlayerUuid: UUID, requestId: String): Boolean =
        sessions.getExchangeRequest(targetPlayerUuid)?.requestId == requestId

    fun respond(target: Player, accepted: Boolean): ContactExchangeResponseResult {
        val request = sessions.removeExchangeRequestForTarget(target.uniqueId)
            ?: return ContactExchangeResponseResult.Missing
        val requester = Bukkit.getPlayer(request.requesterPlayerUuid)
        if (requester == null || !requester.isOnline) {
            return ContactExchangeResponseResult.RequesterOffline
        }

        val requesterCharacter = service.getCurrentCharacter(requester)
        val targetCharacter = service.getCurrentCharacter(target)
        if (
            requesterCharacter == null ||
            targetCharacter == null ||
            requesterCharacter.characterUUID != request.requesterCharacterUuid ||
            targetCharacter.characterUUID != request.targetCharacterUuid
        ) {
            return ContactExchangeResponseResult.CharacterChanged(request)
        }

        if (!accepted) {
            return ContactExchangeResponseResult.Declined(request, targetCharacter.name)
        }

        val result = service.exchangeContacts(requester, target)
        if (result.isFailure) {
            return ContactExchangeResponseResult.Failed(
                request = request,
                result.exceptionOrNull()?.message ?: "The contact exchange failed.",
            )
        }

        return ContactExchangeResponseResult.Accepted(
            request = request,
            requesterCharacterName = requesterCharacter.name,
            targetCharacterName = targetCharacter.name,
        )
    }

    fun cancelForQuit(playerUuid: UUID): ContactExchangeCancellation? {
        sessions.cancelAddMode(playerUuid)

        val requesterRemoved = sessions.removeExchangeRequestForRequester(playerUuid)
        if (requesterRemoved != null) {
            return ContactExchangeCancellation(requesterRemoved, playerWasRequester = true)
        }

        val targetRemoved = sessions.removeExchangeRequestForTarget(playerUuid)
        if (targetRemoved != null) {
            return ContactExchangeCancellation(targetRemoved, playerWasRequester = false)
        }

        return null
    }
}
