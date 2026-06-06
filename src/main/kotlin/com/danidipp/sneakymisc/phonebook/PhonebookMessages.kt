package com.danidipp.sneakymisc.phonebook

data class PhonebookMessage(
    val key: String,
    val arguments: Map<String, String> = emptyMap(),
)

data class PhonebookMessageCatalogEntry(
    val key: String,
    val argumentNames: List<String> = emptyList(),
    val defaultMiniMessage: String,
) {
    fun message(arguments: Map<String, String> = emptyMap()): PhonebookMessage =
        PhonebookMessage(key, arguments)

    fun message(vararg arguments: Pair<String, String>): PhonebookMessage =
        message(arguments.toMap())
}

object PhonebookMessageCatalog {
    val noActiveCharacter = message(
        key = "sneakymisc.phonebook.no_active_character",
        defaultMiniMessage = "<red>You have no active Character.",
    )
    val targetOffline = message(
        key = "sneakymisc.phonebook.target_offline",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<red><gold>{0}</gold> is no longer reachable.",
    )
    val staleOwner = message(
        key = "sneakymisc.phonebook.stale_owner",
        defaultMiniMessage = "<red>This Phonebook is no longer active.",
    )
    val playerOnly = message(
        key = "sneakymisc.phonebook.player_only",
        defaultMiniMessage = "<red>Only players can open a Phonebook.",
    )
    val listed = message(
        key = "sneakymisc.phonebook.listed",
        defaultMiniMessage = "<green>Your Character is now listed in Phonebooks.",
    )
    val unlisted = message(
        key = "sneakymisc.phonebook.unlisted",
        defaultMiniMessage = "<yellow>Your Character is now hidden from Phonebooks.",
    )
    val contactRemoved = message(
        key = "sneakymisc.phonebook.contact_removed",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<green>Removed <gold>{0}</gold> from your Phonebook.",
    )
    val contactAlreadyRemoved = message(
        key = "sneakymisc.phonebook.contact_already_removed",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<yellow><gold>{0}</gold>'s Phonebook was already updated.",
    )
    val exchangeSeekingStarted = message(
        key = "sneakymisc.phonebook.exchange.seeking_started",
        defaultMiniMessage = "<green>Hit another player within 30 seconds to exchange Phonebook details.",
    )
    val exchangeSeekingReset = message(
        key = "sneakymisc.phonebook.exchange.seeking_reset",
        defaultMiniMessage = "<yellow>Still waiting for a Phonebook Exchange target.",
    )
    val exchangeSeekingTimedOut = message(
        key = "sneakymisc.phonebook.exchange.seeking_timed_out",
        defaultMiniMessage = "<yellow>Phonebook Exchange timed out.",
    )
    val exchangeInitiatorNoActiveCharacter = message(
        key = "sneakymisc.phonebook.exchange.initiator_no_active_character",
        defaultMiniMessage = "<red>You no longer have an active Character.",
    )
    val exchangeTargetNoActiveCharacter = message(
        key = "sneakymisc.phonebook.exchange.target_no_active_character",
        defaultMiniMessage = "<red>You need an active Character to exchange Phonebook details.",
    )
    val exchangeDuplicateContact = message(
        key = "sneakymisc.phonebook.exchange.duplicate_contact",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<yellow><gold>{0}</gold> is already in your Phonebook.",
    )
    val exchangeTargetBusy = message(
        key = "sneakymisc.phonebook.exchange.target_busy",
        defaultMiniMessage = "<yellow>That player is busy. Try again when their inventory is clear.",
    )
    val exchangeRequestSent = message(
        key = "sneakymisc.phonebook.exchange.request_sent",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<green>Sent a Phonebook Exchange request to <gold>{0}</gold>.",
    )
    val exchangeAcceptedInitiator = message(
        key = "sneakymisc.phonebook.exchange.accepted_initiator",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<green><gold>{0}</gold> accepted your Phonebook Exchange.",
    )
    val exchangeAcceptedTarget = message(
        key = "sneakymisc.phonebook.exchange.accepted_target",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<green>You exchanged Phonebook details with <gold>{0}</gold>.",
    )
    val exchangeDeclinedInitiator = message(
        key = "sneakymisc.phonebook.exchange.declined_initiator",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<yellow><gold>{0}</gold> declined your Phonebook Exchange.",
    )
    val exchangeDeclinedTarget = message(
        key = "sneakymisc.phonebook.exchange.declined_target",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<yellow>You declined <gold>{0}</gold>'s Phonebook Exchange.",
    )
    val exchangeTargetLeft = message(
        key = "sneakymisc.phonebook.exchange.target_left",
        argumentNames = listOf("character"),
        defaultMiniMessage = "<yellow><gold>{0}</gold> left before finishing the Phonebook Exchange.",
    )
    val exchangeAddContact = message(
        key = "sneakymisc.phonebook.exchange.add_contact",
        defaultMiniMessage = "<green>Add Contact",
    )
    val previousPage = message(
        key = "sneakymisc.phonebook.previous_page",
        defaultMiniMessage = "<yellow>Previous Page",
    )
    val nextPage = message(
        key = "sneakymisc.phonebook.next_page",
        defaultMiniMessage = "<yellow>Next Page",
    )
    val exchangeTitle = message(
        key = "sneakymisc.phonebook.exchange.title",
        defaultMiniMessage = "<gold>Phonebook Exchange",
    )
    val exchangeAccept = message(
        key = "sneakymisc.phonebook.exchange.accept",
        defaultMiniMessage = "<green>Accept",
    )
    val exchangeDecline = message(
        key = "sneakymisc.phonebook.exchange.decline",
        defaultMiniMessage = "<red>Decline",
    )
    val title = message(
        key = "sneakymisc.phonebook.title",
        defaultMiniMessage = "<gold>Phonebook",
    )

    val entries: List<PhonebookMessageCatalogEntry> = listOf(
        noActiveCharacter,
        targetOffline,
        staleOwner,
        playerOnly,
        listed,
        unlisted,
        contactRemoved,
        contactAlreadyRemoved,
        exchangeSeekingStarted,
        exchangeSeekingReset,
        exchangeSeekingTimedOut,
        exchangeInitiatorNoActiveCharacter,
        exchangeTargetNoActiveCharacter,
        exchangeDuplicateContact,
        exchangeTargetBusy,
        exchangeRequestSent,
        exchangeAcceptedInitiator,
        exchangeAcceptedTarget,
        exchangeDeclinedInitiator,
        exchangeDeclinedTarget,
        exchangeTargetLeft,
        exchangeAddContact,
        previousPage,
        nextPage,
        exchangeTitle,
        exchangeAccept,
        exchangeDecline,
        title,
    )

    private val entriesByKey = entries.associateBy { it.key }

    fun entry(key: String): PhonebookMessageCatalogEntry =
        requireNotNull(entriesByKey[key]) { "Unknown Phonebook message key '$key'" }

    fun argumentNames(key: String): List<String> =
        entriesByKey[key]?.argumentNames.orEmpty()

    fun noActiveCharacter(): PhonebookMessage = noActiveCharacter.message()
    fun targetOffline(character: String): PhonebookMessage = targetOffline.message("character" to character)
    fun staleOwner(): PhonebookMessage = staleOwner.message()
    fun listed(): PhonebookMessage = listed.message()
    fun unlisted(): PhonebookMessage = unlisted.message()
    fun contactRemoved(character: String): PhonebookMessage = contactRemoved.message("character" to character)
    fun contactAlreadyRemoved(character: String): PhonebookMessage = contactAlreadyRemoved.message("character" to character)

    fun exchangeSeekingStarted(): PhonebookMessage = exchangeSeekingStarted.message()
    fun exchangeSeekingReset(): PhonebookMessage = exchangeSeekingReset.message()
    fun exchangeSeekingTimedOut(): PhonebookMessage = exchangeSeekingTimedOut.message()
    fun exchangeInitiatorNoActiveCharacter(): PhonebookMessage = exchangeInitiatorNoActiveCharacter.message()
    fun exchangeTargetNoActiveCharacter(): PhonebookMessage = exchangeTargetNoActiveCharacter.message()
    fun exchangeDuplicateContact(character: String): PhonebookMessage = exchangeDuplicateContact.message("character" to character)
    fun exchangeTargetBusy(): PhonebookMessage = exchangeTargetBusy.message()
    fun exchangeRequestSent(character: String): PhonebookMessage = exchangeRequestSent.message("character" to character)
    fun exchangeAcceptedInitiator(character: String): PhonebookMessage = exchangeAcceptedInitiator.message("character" to character)
    fun exchangeAcceptedTarget(character: String): PhonebookMessage = exchangeAcceptedTarget.message("character" to character)
    fun exchangeDeclinedInitiator(character: String): PhonebookMessage = exchangeDeclinedInitiator.message("character" to character)
    fun exchangeDeclinedTarget(character: String): PhonebookMessage = exchangeDeclinedTarget.message("character" to character)
    fun exchangeTargetLeft(character: String): PhonebookMessage = exchangeTargetLeft.message("character" to character)

    private fun message(
        key: String,
        argumentNames: List<String> = emptyList(),
        defaultMiniMessage: String,
    ): PhonebookMessageCatalogEntry =
        PhonebookMessageCatalogEntry(key, argumentNames, defaultMiniMessage)
}

object PhonebookMessageKeys {
    val NO_ACTIVE_CHARACTER: String = PhonebookMessageCatalog.noActiveCharacter.key
    val TARGET_OFFLINE: String = PhonebookMessageCatalog.targetOffline.key
    val STALE_OWNER: String = PhonebookMessageCatalog.staleOwner.key
    val PLAYER_ONLY: String = PhonebookMessageCatalog.playerOnly.key
    val LISTED: String = PhonebookMessageCatalog.listed.key
    val UNLISTED: String = PhonebookMessageCatalog.unlisted.key
    val CONTACT_REMOVED: String = PhonebookMessageCatalog.contactRemoved.key
    val CONTACT_ALREADY_REMOVED: String = PhonebookMessageCatalog.contactAlreadyRemoved.key
    val EXCHANGE_SEEKING_STARTED: String = PhonebookMessageCatalog.exchangeSeekingStarted.key
    val EXCHANGE_SEEKING_RESET: String = PhonebookMessageCatalog.exchangeSeekingReset.key
    val EXCHANGE_SEEKING_TIMED_OUT: String = PhonebookMessageCatalog.exchangeSeekingTimedOut.key
    val EXCHANGE_INITIATOR_NO_ACTIVE_CHARACTER: String = PhonebookMessageCatalog.exchangeInitiatorNoActiveCharacter.key
    val EXCHANGE_TARGET_NO_ACTIVE_CHARACTER: String = PhonebookMessageCatalog.exchangeTargetNoActiveCharacter.key
    val EXCHANGE_DUPLICATE_CONTACT: String = PhonebookMessageCatalog.exchangeDuplicateContact.key
    val EXCHANGE_TARGET_BUSY: String = PhonebookMessageCatalog.exchangeTargetBusy.key
    val EXCHANGE_REQUEST_SENT: String = PhonebookMessageCatalog.exchangeRequestSent.key
    val EXCHANGE_ACCEPTED_INITIATOR: String = PhonebookMessageCatalog.exchangeAcceptedInitiator.key
    val EXCHANGE_ACCEPTED_TARGET: String = PhonebookMessageCatalog.exchangeAcceptedTarget.key
    val EXCHANGE_DECLINED_INITIATOR: String = PhonebookMessageCatalog.exchangeDeclinedInitiator.key
    val EXCHANGE_DECLINED_TARGET: String = PhonebookMessageCatalog.exchangeDeclinedTarget.key
    val EXCHANGE_TARGET_LEFT: String = PhonebookMessageCatalog.exchangeTargetLeft.key
    val EXCHANGE_ADD_CONTACT: String = PhonebookMessageCatalog.exchangeAddContact.key
    val PREVIOUS_PAGE: String = PhonebookMessageCatalog.previousPage.key
    val NEXT_PAGE: String = PhonebookMessageCatalog.nextPage.key
    val EXCHANGE_TITLE: String = PhonebookMessageCatalog.exchangeTitle.key
    val EXCHANGE_ACCEPT: String = PhonebookMessageCatalog.exchangeAccept.key
    val EXCHANGE_DECLINE: String = PhonebookMessageCatalog.exchangeDecline.key
    val TITLE: String = PhonebookMessageCatalog.title.key

    fun argumentNames(key: String): List<String> = PhonebookMessageCatalog.argumentNames(key)
}
