package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookExchangeActionsTest {
    @Test
    fun `starting Phonebook Seeking schedules a timeout and resetting replaces it with neutral feedback`() {
        val initiatorAccount = account("1")
        val actions = exchangeActions()

        val first = actions.startSeeking(initiatorAccount)
        val second = actions.startSeeking(initiatorAccount)

        assertEquals(
            PhonebookExchangeResult.StartedSeeking,
            first.result,
        )
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_SEEKING_STARTED)),
                PhonebookExchangeEffect.ScheduleSeekingTimeout(initiatorAccount, PhonebookExchangeSeekingToken(1), delayTicks = 20 * 30),
            ),
            first.effects,
        )
        assertEquals(
            PhonebookExchangeResult.ResetSeeking,
            second.result,
        )
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, PhonebookExchangeSeekingToken(1)),
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_SEEKING_RESET)),
                PhonebookExchangeEffect.ScheduleSeekingTimeout(initiatorAccount, PhonebookExchangeSeekingToken(2), delayTicks = 20 * 30),
            ),
            second.effects,
        )
    }

    @Test
    fun `Phonebook Seeking timeout clears state and sends keyed feedback only for the current token`() {
        val initiatorAccount = account("1")
        val actions = exchangeActions()
        val first = actions.startSeeking(initiatorAccount)
        val second = actions.startSeeking(initiatorAccount)

        val staleTimeout = actions.seekingTimedOut(initiatorAccount, (first.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token)
        val currentTimeout = actions.seekingTimedOut(initiatorAccount, (second.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token)
        val repeatedTimeout = actions.seekingTimedOut(initiatorAccount, (second.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token)

        assertEquals(PhonebookExchangeResult.NoSeeking, staleTimeout.result)
        assertEquals(emptyList(), staleTimeout.effects)
        assertEquals(PhonebookExchangeResult.SeekingTimedOut, currentTimeout.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_SEEKING_TIMED_OUT))
            ),
            currentTimeout.effects,
        )
        assertEquals(PhonebookExchangeResult.NoSeeking, repeatedTimeout.result)
        assertEquals(emptyList(), repeatedTimeout.effects)
    }

    @Test
    fun `soft invalid damage hits do not consume Phonebook Seeking`() {
        val initiatorAccount = account("1")
        val actions = exchangeActions()
        val seeking = actions.startSeeking(initiatorAccount)

        val nonPlayerHit = actions.damageSeekingTarget(initiatorAccount, targetAccountId = null)
        val selfHit = actions.damageSeekingTarget(initiatorAccount, targetAccountId = initiatorAccount)
        val timeout = actions.seekingTimedOut(initiatorAccount, (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token)

        assertEquals(PhonebookExchangeResult.SoftInvalidHit, nonPlayerHit.result)
        assertEquals(emptyList(), nonPlayerHit.effects)
        assertEquals(PhonebookExchangeResult.SoftInvalidHit, selfHit.result)
        assertEquals(emptyList(), selfHit.effects)
        assertEquals(PhonebookExchangeResult.SeekingTimedOut, timeout.result)
    }

    @Test
    fun `hard invalid damage hit cancels Phonebook Seeking with feedback when the initiator has no active Character`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(targetAccount to character("2"))),
        )
        val seeking = actions.startSeeking(initiatorAccount)
        val token = (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val result = actions.damageSeekingTarget(initiatorAccount, targetAccount)
        val timeout = actions.seekingTimedOut(initiatorAccount, token)

        assertEquals(PhonebookExchangeResult.HardInvalidHit, result.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, token),
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_INITIATOR_NO_ACTIVE_CHARACTER)),
            ),
            result.effects,
        )
        assertEquals(PhonebookExchangeResult.NoSeeking, timeout.result)
    }

    @Test
    fun `valid damage hit consumes seeking and opens an exchange for snapshotted Characters`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val initiatorSnapshot = PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character")
        val targetSnapshot = PhonebookCharacter(targetAccount, targetCharacter, "Target Character")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(characters = listOf(initiatorSnapshot, targetSnapshot)),
        )
        val seeking = actions.startSeeking(initiatorAccount)
        val token = (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val result = actions.damageSeekingTarget(initiatorAccount, targetAccount)
        val timeout = actions.seekingTimedOut(initiatorAccount, token)

        assertEquals(PhonebookExchangeResult.ExchangeOpened, result.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, token),
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_REQUEST_SENT,
                        mapOf("character" to "Target Character"),
                    ),
                ),
                PhonebookExchangeEffect.OpenExchangeDecision(
                    targetAccount,
                    PhonebookExchangeDecisionModel(PhonebookExchangeId(1), initiatorSnapshot, targetSnapshot),
                ),
            ),
            result.effects,
        )
        assertEquals(PhonebookExchangeResult.NoSeeking, timeout.result)
    }

    @Test
    fun `valid damage hit cancels target Phonebook Seeking before opening their exchange GUI`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val initiatorSnapshot = PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character")
        val targetSnapshot = PhonebookCharacter(targetAccount, targetCharacter, "Target Character")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(characters = listOf(initiatorSnapshot, targetSnapshot)),
        )
        val targetSeeking = actions.startSeeking(targetAccount)
        val targetToken = (targetSeeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token
        val initiatorSeeking = actions.startSeeking(initiatorAccount)
        val initiatorToken = (initiatorSeeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val opened = actions.damageSeekingTarget(initiatorAccount, targetAccount)
        val targetTimeout = actions.seekingTimedOut(targetAccount, targetToken)

        assertEquals(PhonebookExchangeResult.ExchangeOpened, opened.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, initiatorToken),
                PhonebookExchangeEffect.CancelSeekingTimeout(targetAccount, targetToken),
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_REQUEST_SENT,
                        mapOf("character" to "Target Character"),
                    ),
                ),
                PhonebookExchangeEffect.OpenExchangeDecision(
                    targetAccount,
                    PhonebookExchangeDecisionModel(PhonebookExchangeId(1), initiatorSnapshot, targetSnapshot),
                ),
            ),
            opened.effects,
        )
        assertEquals(PhonebookExchangeResult.NoSeeking, targetTimeout.result)
        assertEquals(emptyList(), targetTimeout.effects)
    }

    @Test
    fun `hard invalid damage hit cancels seeking when target has no active Character`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(activeCharacters = mapOf(initiatorAccount to initiatorCharacter)),
        )
        val seeking = actions.startSeeking(initiatorAccount)
        val token = (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val result = actions.damageSeekingTarget(initiatorAccount, targetAccount)

        assertEquals(PhonebookExchangeResult.HardInvalidHit, result.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, token),
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_TARGET_NO_ACTIVE_CHARACTER)),
            ),
            result.effects,
        )
    }

    @Test
    fun `hard invalid damage hit rejects duplicate contacts with snapshotted target feedback`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val initialData = PhonebookData(
            contacts = mapOf(
                PhonebookContactKeys.forCharacters(initiatorCharacter, targetCharacter) to
                    PhonebookContact.between(initiatorCharacter, initiatorAccount, targetCharacter, targetAccount)
            )
        )
        val actions = exchangeActions(
            phonebooks = RecordingPhonebookStore(initialData),
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
                ),
            ),
        )
        val seeking = actions.startSeeking(initiatorAccount)
        val token = (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val result = actions.damageSeekingTarget(initiatorAccount, targetAccount)

        assertEquals(PhonebookExchangeResult.HardInvalidHit, result.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, token),
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DUPLICATE_CONTACT,
                        mapOf("character" to "Target Character"),
                    ),
                ),
            ),
            result.effects,
        )
    }

    @Test
    fun `hard invalid damage hit rejects a busy target without replacing their UI`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
        )
        val seeking = actions.startSeeking(initiatorAccount)
        val token = (seeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val result = actions.damageSeekingTarget(initiatorAccount, targetAccount, targetBusy = true)

        assertEquals(PhonebookExchangeResult.HardInvalidHit, result.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, token),
                PhonebookExchangeEffect.SendMessage(initiatorAccount, PhonebookMessage(PhonebookMessageKeys.EXCHANGE_TARGET_BUSY)),
            ),
            result.effects,
        )
    }

    @Test
    fun `accepting an exchange resolves once persists the snapshotted contact lists both Characters and schedules close`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val initiatorSnapshot = PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character")
        val targetSnapshot = PhonebookCharacter(targetAccount, targetCharacter, "Target Character")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(characters = listOf(initiatorSnapshot, targetSnapshot)),
        )
        val open = actions.damageSeekingTargetAfterStart(initiatorAccount, targetAccount)
        val exchangeId = (open.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId

        val accepted = actions.accept(exchangeId, targetAccount)
        val repeated = actions.accept(exchangeId, targetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeAccepted, accepted.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_ACCEPTED_INITIATOR,
                        mapOf("character" to "Target Character"),
                    ),
                ),
                PhonebookExchangeEffect.SendMessage(
                    targetAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_ACCEPTED_TARGET,
                        mapOf("character" to "Initiator Character"),
                    ),
                ),
                PhonebookExchangeEffect.ScheduleExchangeClose(targetAccount, exchangeId),
            ),
            accepted.effects,
        )
        assertEquals(PhonebookExchangeResult.NoExchange, repeated.result)
        assertEquals(emptyList(), repeated.effects)
        assertEquals(setOf(initiatorCharacter, targetCharacter), phonebooks.load().listings)
        assertEquals(
            PhonebookContact.between(initiatorCharacter, initiatorAccount, targetCharacter, targetAccount),
            phonebooks.load().contactBetween(initiatorCharacter, targetCharacter),
        )
        assertEquals(1, phonebooks.savedData.size)
    }

    @Test
    fun `accepting an exchange reports duplicate persistence outcome without listing Characters`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
                ),
            ),
        )
        val open = actions.damageSeekingTargetAfterStart(initiatorAccount, targetAccount)
        val exchangeId = (open.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId
        phonebooks.save(
            PhonebookData(
                contacts = mapOf(
                    PhonebookContactKeys.forCharacters(initiatorCharacter, targetCharacter) to
                        PhonebookContact.between(initiatorCharacter, initiatorAccount, targetCharacter, targetAccount)
                )
            )
        )

        val accepted = actions.accept(exchangeId, targetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeAlreadyExists, accepted.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    targetAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DUPLICATE_CONTACT,
                        mapOf("character" to "Initiator Character"),
                    ),
                ),
                PhonebookExchangeEffect.ScheduleExchangeClose(targetAccount, exchangeId),
            ),
            accepted.effects,
        )
        assertEquals(emptySet(), phonebooks.load().listings)
        assertEquals(1, phonebooks.savedData.size)
    }

    @Test
    fun `decline creates no contact schedules close and later close event does not decline again`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
                ),
            ),
        )
        val open = actions.damageSeekingTargetAfterStart(initiatorAccount, targetAccount)
        val exchangeId = (open.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId

        val declined = actions.decline(exchangeId, targetAccount)
        val closeAfterDecline = actions.close(exchangeId, targetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeDeclined, declined.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_INITIATOR,
                        mapOf("character" to "Target Character"),
                    ),
                ),
                PhonebookExchangeEffect.SendMessage(
                    targetAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_TARGET,
                        mapOf("character" to "Initiator Character"),
                    ),
                ),
                PhonebookExchangeEffect.ScheduleExchangeClose(targetAccount, exchangeId),
            ),
            declined.effects,
        )
        assertEquals(PhonebookExchangeResult.NoExchange, closeAfterDecline.result)
        assertEquals(PhonebookData(), phonebooks.load())
        assertEquals(emptyList(), phonebooks.savedData)
    }

    @Test
    fun `multiple outgoing requests from one initiator resolve independently`() {
        val initiatorAccount = account("1")
        val firstTargetAccount = account("2")
        val secondTargetAccount = account("3")
        val initiatorCharacter = character("1")
        val firstTargetCharacter = character("2")
        val secondTargetCharacter = character("3")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    firstTargetAccount to firstTargetCharacter,
                    secondTargetAccount to secondTargetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(firstTargetAccount, firstTargetCharacter, "First Target"),
                    PhonebookCharacter(secondTargetAccount, secondTargetCharacter, "Second Target"),
                ),
            ),
        )

        val firstOpen = actions.damageSeekingTargetAfterStart(initiatorAccount, firstTargetAccount)
        val secondOpenWithoutSeeking = actions.damageSeekingTarget(initiatorAccount, secondTargetAccount)
        val secondOpen = actions.damageSeekingTargetAfterStart(initiatorAccount, secondTargetAccount)
        val firstExchangeId = (firstOpen.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId
        val secondExchangeId = (secondOpen.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId

        val firstDeclined = actions.decline(firstExchangeId, firstTargetAccount)
        val secondAccepted = actions.accept(secondExchangeId, secondTargetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeOpened, firstOpen.result)
        assertEquals(PhonebookExchangeResult.NoSeeking, secondOpenWithoutSeeking.result)
        assertEquals(emptyList(), secondOpenWithoutSeeking.effects)
        assertEquals(PhonebookExchangeResult.ExchangeOpened, secondOpen.result)
        assertEquals(PhonebookExchangeResult.ExchangeDeclined, firstDeclined.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_INITIATOR,
                        mapOf("character" to "First Target"),
                    ),
                ),
                PhonebookExchangeEffect.SendMessage(
                    firstTargetAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_TARGET,
                        mapOf("character" to "Initiator Character"),
                    ),
                ),
                PhonebookExchangeEffect.ScheduleExchangeClose(firstTargetAccount, firstExchangeId),
            ),
            firstDeclined.effects,
        )
        assertEquals(PhonebookExchangeResult.ExchangeAccepted, secondAccepted.result)
        assertEquals(
            PhonebookContact.between(initiatorCharacter, initiatorAccount, secondTargetCharacter, secondTargetAccount),
            phonebooks.load().contactBetween(initiatorCharacter, secondTargetCharacter),
        )
        assertEquals(null, phonebooks.load().contactBetween(initiatorCharacter, firstTargetCharacter))
        assertEquals(setOf(initiatorCharacter, secondTargetCharacter), phonebooks.load().listings)
    }

    @Test
    fun `closing an unresolved exchange counts as decline without scheduling another close`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val actions = exchangeActions(
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
                ),
            ),
        )
        val open = actions.damageSeekingTargetAfterStart(initiatorAccount, targetAccount)
        val exchangeId = (open.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId

        val closed = actions.close(exchangeId, targetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeDeclined, closed.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_INITIATOR,
                        mapOf("character" to "Target Character"),
                    ),
                ),
                PhonebookExchangeEffect.SendMessage(
                    targetAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_DECLINED_TARGET,
                        mapOf("character" to "Initiator Character"),
                    ),
                ),
            ),
            closed.effects,
        )
    }

    @Test
    fun `target quit cancels their unresolved exchange without persistence`() {
        val initiatorAccount = account("1")
        val targetAccount = account("2")
        val initiatorCharacter = character("1")
        val targetCharacter = character("2")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    targetAccount to targetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(targetAccount, targetCharacter, "Target Character"),
                ),
            ),
        )
        val open = actions.damageSeekingTargetAfterStart(initiatorAccount, targetAccount)
        val exchangeId = (open.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId

        val quit = actions.targetQuit(targetAccount)
        val acceptAfterQuit = actions.accept(exchangeId, targetAccount)

        assertEquals(PhonebookExchangeResult.ExchangeDeclined, quit.result)
        assertEquals(
            listOf(
                PhonebookExchangeEffect.SendMessage(
                    initiatorAccount,
                    PhonebookMessage(
                        PhonebookMessageKeys.EXCHANGE_TARGET_LEFT,
                        mapOf("character" to "Target Character"),
                    ),
                )
            ),
            quit.effects,
        )
        assertEquals(PhonebookExchangeResult.NoExchange, acceptAfterQuit.result)
        assertEquals(PhonebookData(), phonebooks.load())
        assertEquals(emptyList(), phonebooks.savedData)
    }

    @Test
    fun `initiator quit cancels only active seeking and leaves already opened exchanges resolvable`() {
        val initiatorAccount = account("1")
        val firstTargetAccount = account("2")
        val secondTargetAccount = account("3")
        val initiatorCharacter = character("1")
        val firstTargetCharacter = character("2")
        val secondTargetCharacter = character("3")
        val phonebooks = RecordingPhonebookStore(PhonebookData())
        val actions = exchangeActions(
            phonebooks = phonebooks,
            activeCharacters = FakePhonebookActiveCharacters(
                activeCharacters = mapOf(
                    initiatorAccount to initiatorCharacter,
                    firstTargetAccount to firstTargetCharacter,
                    secondTargetAccount to secondTargetCharacter,
                ),
            ),
            directory = FakePhonebookDirectory(
                characters = listOf(
                    PhonebookCharacter(initiatorAccount, initiatorCharacter, "Initiator Character"),
                    PhonebookCharacter(firstTargetAccount, firstTargetCharacter, "First Target"),
                    PhonebookCharacter(secondTargetAccount, secondTargetCharacter, "Second Target"),
                ),
            ),
        )
        val firstOpen = actions.damageSeekingTargetAfterStart(initiatorAccount, firstTargetAccount)
        val firstExchangeId = (firstOpen.effects.last() as PhonebookExchangeEffect.OpenExchangeDecision).model.exchangeId
        val activeSeeking = actions.startSeeking(initiatorAccount)
        val activeSeekingToken = (activeSeeking.effects.last() as PhonebookExchangeEffect.ScheduleSeekingTimeout).token

        val quit = actions.accountQuit(initiatorAccount)
        val timedOutAfterQuit = actions.seekingTimedOut(initiatorAccount, activeSeekingToken)
        val acceptedAfterQuit = actions.accept(firstExchangeId, firstTargetAccount)

        assertEquals(PhonebookExchangeResult.NoExchange, quit.result)
        assertEquals(
            listOf(PhonebookExchangeEffect.CancelSeekingTimeout(initiatorAccount, activeSeekingToken)),
            quit.effects,
        )
        assertEquals(PhonebookExchangeResult.NoSeeking, timedOutAfterQuit.result)
        assertEquals(emptyList(), timedOutAfterQuit.effects)
        assertEquals(PhonebookExchangeResult.ExchangeAccepted, acceptedAfterQuit.result)
        assertEquals(
            PhonebookContact.between(initiatorCharacter, initiatorAccount, firstTargetCharacter, firstTargetAccount),
            phonebooks.load().contactBetween(initiatorCharacter, firstTargetCharacter),
        )
        assertEquals(null, phonebooks.load().contactBetween(initiatorCharacter, secondTargetCharacter))
    }

    private fun exchangeActions(
        phonebooks: PhonebookExchangeStore = RecordingPhonebookStore(PhonebookData()),
        activeCharacters: PhonebookActiveCharacters = FakePhonebookActiveCharacters(),
        directory: PhonebookDirectory = FakePhonebookDirectory(),
    ): PhonebookExchangeActions =
        PhonebookExchangeActions(
            phonebooks = phonebooks,
            activeCharacters = activeCharacters,
            directory = directory,
        )

    private class FakePhonebookActiveCharacters(
        private val activeCharacters: Map<UUID, UUID> = emptyMap(),
    ) : PhonebookActiveCharacters {
        override fun activeCharacter(accountId: UUID): UUID? = activeCharacters[accountId]
    }

    private class FakePhonebookDirectory(
        characters: List<PhonebookCharacter> = emptyList(),
        private val onlineAccounts: Set<UUID> = emptySet(),
    ) : PhonebookDirectory {
        private val charactersByAccountAndId = characters.associateBy { it.accountId to it.characterId }

        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? =
            charactersByAccountAndId[accountId to characterId]

        override fun isOnline(accountId: UUID): Boolean = accountId in onlineAccounts
    }

    private fun account(suffix: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-${suffix.padStart(12, '0')}")

    private fun character(suffix: String): UUID =
        UUID.fromString("${suffix.padStart(8, '0')}-0000-0000-0000-000000000000")

    private fun PhonebookExchangeActions.damageSeekingTargetAfterStart(
        initiatorAccount: UUID,
        targetAccount: UUID,
    ): PhonebookExchangeOutcome {
        startSeeking(initiatorAccount)
        return damageSeekingTarget(initiatorAccount, targetAccount)
    }
}
