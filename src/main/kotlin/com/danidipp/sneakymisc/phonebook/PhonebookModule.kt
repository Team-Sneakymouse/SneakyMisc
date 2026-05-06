package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import net.kyori.adventure.text.Component
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.logging.Logger

class PhonebookModule(
    private val logger: Logger,
) : SneakyModule() {
    companion object {
        val deps = listOf("SneakyCharacterManager", "SneakyCellPhones")
        private val CONTACT_SLOTS = (0 until 54).filter { it % 9 != 8 } // 9th column reserved for ui
        private val UI_SLOTS = setOf(8, 17, 26, 35, 44, 53)
    }

    private val plugin = SneakyMisc.getInstance()
    private val repository = PhonebookRepository(logger)
    private val service = PhonebookService(logger, repository)
    private val sessions = PhonebookSessionManager()
    private val exchange = PhonebookExchange(service, sessions)
    private val suppressExchangeClose = mutableSetOf<UUID>()

    private val characterUuidKey = NamespacedKey(plugin, "phonebook_character_uuid")
    private val ownerPlayerUuidKey = NamespacedKey(plugin, "phonebook_owner_player_uuid")

    private val listener = object : Listener {
        @EventHandler
        fun onLoadCharacter(event: LoadCharacterEvent) {
            service.refreshCharacterIndex()
        }

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
            val player = event.whoClicked as? Player ?: return
            val topInventory = event.view.topInventory
            when (val holder = topInventory.holder) {
                is PhonebookGui -> handlePhonebookClick(event, player, holder)
                is PhonebookExchangeGui -> handleExchangeClick(event, player)
            }
        }

        @EventHandler
        fun onInventoryDrag(event: InventoryDragEvent) {
            val holder = event.view.topInventory.holder
            if (holder is PhonebookGui || holder is PhonebookExchangeGui) {
                event.isCancelled = true
            }
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
            val player = event.player as? Player ?: return
            val holder = event.inventory.holder
            if (holder !is PhonebookExchangeGui) return
            if (suppressExchangeClose.remove(player.uniqueId)) return
            if (!exchange.targetHasRequest(player.uniqueId, holder.requestId)) return
            applyExchangeResponse(player, exchange.respond(player, accepted = false))
        }

        @EventHandler
        fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
            val requester = event.damager as? Player ?: return
            val target = event.entity as? Player ?: return

            val result = exchange.targetContact(
                requester = requester,
                target = target,
            ) { expired ->
                val expiredTarget = Bukkit.getPlayer(expired.targetPlayerUuid)
                val expiredRequester = Bukkit.getPlayer(expired.requesterPlayerUuid)
                expiredRequester?.sendMessage("Your contact exchange request expired.")
                if (expiredTarget != null && expiredTarget.isOnline) {
                    expiredTarget.sendMessage("The contact exchange request expired.")
                    if ((expiredTarget.openInventory.topInventory.holder as? PhonebookExchangeGui)?.requestId == expired.requestId) {
                        closeExchangeInventory(expiredTarget)
                    }
                }
            }
            if (result is TargetContactExchangeResult.NotTargeting) return

            event.isCancelled = true
            applyTargetContactResult(requester, target, result)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            val cancellation = exchange.cancelForQuit(event.player.uniqueId) ?: return
            if (cancellation.playerWasRequester) {
                val target = Bukkit.getPlayer(cancellation.request.targetPlayerUuid)
                if (target != null && target.isOnline) {
                    target.sendMessage("The contact exchange was canceled.")
                    if ((target.openInventory.topInventory.holder as? PhonebookExchangeGui)?.requestId == cancellation.request.requestId) {
                        closeExchangeInventory(target)
                    }
                }
            } else {
                Bukkit.getPlayer(cancellation.request.requesterPlayerUuid)?.sendMessage("The contact exchange was canceled.")
            }
        }
    }

    private fun handlePhonebookClick(event: InventoryClickEvent, player: Player, holder: PhonebookGui) {
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return

        when (event.slot) {
            8 -> {
                player.closeInventory()
                startAddMode(player)
                return
            }

            53 -> {
                openPhonebook(player, holder.page + 1)
                return
            }
        }

        if (event.slot in UI_SLOTS) return
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        val characterUuid = meta.persistentDataContainer.get(characterUuidKey, PersistentDataType.STRING) ?: return
        val ownerPlayerUuid = meta.persistentDataContainer.get(ownerPlayerUuidKey, PersistentDataType.STRING) ?: return

        if (event.click == ClickType.SWAP_OFFHAND) {
            if (service.removeContact(player, characterUuid)) {
                player.sendMessage("Contact removed from your phonebook.")
            }
            openPhonebook(player, holder.page)
            return
        }

        if (event.click != ClickType.LEFT) return

        val visibleContact = service.getVisibleContacts(player).firstOrNull {
            it.characterUuid == characterUuid && it.ownerPlayerUuid == ownerPlayerUuid
        } ?: run {
            player.sendMessage("That contact is no longer available.")
            openPhonebook(player, holder.page)
            return
        }

        val result = service.callContact(player, visibleContact)
        if (result.isFailure) {
            player.sendMessage(result.exceptionOrNull()?.message ?: "Failed to start the phone call.")
        }
    }

    private fun handleExchangeClick(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true
        if (event.clickedInventory != event.view.topInventory) return

        when (event.slot) {
            3 -> {
                closeExchangeInventory(player)
                applyExchangeResponse(player, exchange.respond(player, accepted = true))
            }

            5 -> {
                closeExchangeInventory(player)
                applyExchangeResponse(player, exchange.respond(player, accepted = false))
            }
        }
    }

    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(PhonebookCommand(this).build(), "Open your phonebook")
    )
    override val listeners: List<Listener> = listOf(listener)

    fun openPhonebook(player: Player, requestedPage: Int) {
        if (service.getCurrentCharacter(player) == null) {
            player.sendMessage("You do not have an active character.")
            return
        }

        val visibleContacts = service.getVisibleContacts(player)
        val pageCount = maxOf(1, (visibleContacts.size + CONTACT_SLOTS.size - 1) / CONTACT_SLOTS.size)
        val page = requestedPage.mod(pageCount)
        val holder = PhonebookGui(player.uniqueId, page)
        val inventory = holder.inventory

        for (slot in setOf(17, 26, 35, 44)) {
            inventory.setItem(slot, fillerItem())
        }
        inventory.setItem(8, plusItem())
        inventory.setItem(53, nextPageItem(page))

        val pageContacts = visibleContacts.drop(page * CONTACT_SLOTS.size).take(CONTACT_SLOTS.size)
        for ((index, contact) in pageContacts.withIndex()) {
            inventory.setItem(CONTACT_SLOTS[index], contactItem(contact))
        }

        player.openInventory(inventory)
    }

    fun cancelAddMode(player: Player, message: String? = null) {
        if (!exchange.cancelTargeting(player.uniqueId)) return
        if (message != null) player.sendMessage(message)
    }

    private fun startAddMode(player: Player) {
        exchange.startTargeting(player) { expired ->
            expired.sendMessage("Phonebook targeting expired.")
        }
        player.sendMessage("Hit another player within 30 seconds to exchange contact details.")
    }

    private fun openExchangeGui(target: Player, request: PendingExchangeRequest) {
        val holder = PhonebookExchangeGui(request.requestId, target.uniqueId)
        val inventory = holder.inventory
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, fillerItem())
        }
        inventory.setItem(3, exchangeButton(Material.LIME_DYE, "Accept", "lom:yes"))
        inventory.setItem(5, exchangeButton(Material.RED_DYE, "Decline", "lom:no"))
        target.openInventory(inventory)
        target.sendMessage("${request.requesterCharacterName} wants to exchange contact details.")
    }

    private fun applyTargetContactResult(
        requester: Player,
        target: Player,
        result: TargetContactExchangeResult,
    ) {
        when (result) {
            TargetContactExchangeResult.NotTargeting -> Unit
            TargetContactExchangeResult.SelfTarget -> requester.sendMessage("You cannot exchange contact details with yourself.")
            TargetContactExchangeResult.RequesterMissingCharacter -> requester.sendMessage("You do not have an active character.")
            TargetContactExchangeResult.TargetMissingCharacter -> requester.sendMessage("That player does not have an active character.")
            is TargetContactExchangeResult.AlreadyContact -> requester.sendMessage("${result.targetCharacterName} is already in your phonebook.")
            is TargetContactExchangeResult.Created -> {
                requester.sendMessage("Contact exchange request sent to ${result.request.targetCharacterName}.")
                openExchangeGui(target, result.request)
            }
            TargetContactExchangeResult.RequesterBusy -> requester.sendMessage("You already have a pending exchange request.")
            TargetContactExchangeResult.TargetBusy -> requester.sendMessage("That player is already handling another exchange request.")
        }
    }

    private fun applyExchangeResponse(target: Player, result: ContactExchangeResponseResult) {
        when (result) {
            ContactExchangeResponseResult.Missing -> Unit
            ContactExchangeResponseResult.RequesterOffline -> target.sendMessage("That exchange request is no longer valid.")
            is ContactExchangeResponseResult.CharacterChanged -> {
                Bukkit.getPlayer(result.request.requesterPlayerUuid)?.sendMessage("The contact exchange is no longer valid.")
                target.sendMessage("The contact exchange is no longer valid.")
            }
            is ContactExchangeResponseResult.Declined -> {
                Bukkit.getPlayer(result.request.requesterPlayerUuid)
                    ?.sendMessage("${result.targetCharacterName} declined your contact exchange.")
                target.sendMessage("Contact exchange declined.")
            }
            is ContactExchangeResponseResult.Accepted -> {
                Bukkit.getPlayer(result.request.requesterPlayerUuid)
                    ?.sendMessage("Added ${result.targetCharacterName} to your phonebook.")
                target.sendMessage("Added ${result.requesterCharacterName} to your phonebook.")
            }
            is ContactExchangeResponseResult.Failed -> {
                Bukkit.getPlayer(result.request.requesterPlayerUuid)?.sendMessage(result.message)
                target.sendMessage(result.message)
            }
        }
    }

    private fun closeExchangeInventory(player: Player) {
        suppressExchangeClose.add(player.uniqueId)
        player.closeInventory()
        suppressExchangeClose.remove(player.uniqueId)
    }

    private fun fillerItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.itemMeta = item.itemMeta.apply {
            displayName(Component.text(" "))
        }
        return item
    }

    private fun plusItem(): ItemStack {
        val item = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        item.itemMeta = item.itemMeta.apply {
            displayName(Component.text("Add Contact"))
            lore(listOf(Component.text("Click to start a contact exchange.")))
            applyItemModel("lom:plus")
        }
        return item
    }

    private fun nextPageItem(page: Int): ItemStack {
        val item = ItemStack(Material.BRICK)
        item.itemMeta = item.itemMeta.apply {
            displayName(Component.text("Next Page"))
            lore(listOf(Component.text("Current page: ${page + 1}")))
            setCustomModelData(page)
            applyItemModel("lom:pagecount")
        }
        return item
    }

    private fun exchangeButton(material: Material, label: String, itemModel: String): ItemStack {
        val item = ItemStack(material)
        item.itemMeta = item.itemMeta.apply {
            displayName(Component.text(label))
            applyItemModel(itemModel)
        }
        return item
    }

    private fun contactItem(contact: RenderedContact): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta
        val ownerUuid = UUID.fromString(contact.ownerPlayerUuid)
        val offlineOwner: OfflinePlayer = Bukkit.getOfflinePlayer(ownerUuid)
        meta.setOwningPlayer(offlineOwner)
        meta.displayName(Component.text(contact.characterName))
        meta.lore(
            listOf(
                Component.text("Left click: call ${contact.characterName}"),
                Component.text("F: delete contact"),
            )
        )
        meta.persistentDataContainer.set(characterUuidKey, PersistentDataType.STRING, contact.characterUuid)
        meta.persistentDataContainer.set(ownerPlayerUuidKey, PersistentDataType.STRING, contact.ownerPlayerUuid)
        item.itemMeta = meta
        return item
    }

    private fun ItemMeta.applyItemModel(model: String) {
        runCatching {
            val method = javaClass.methods.firstOrNull { it.name == "setItemModel" && it.parameterCount == 1 } ?: return@runCatching
            method.invoke(this, NamespacedKey.fromString(model))
        }.onFailure {
            logger.fine("Failed to apply item model '$model': ${it.message}")
        }
    }
}
