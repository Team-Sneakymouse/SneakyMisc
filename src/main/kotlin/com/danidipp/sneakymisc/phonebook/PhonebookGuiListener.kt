package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

class PhonebookGuiListener(
    private val guiActions: PhonebookGuiActions,
    private val inventoryFactory: PhonebookInventoryFactory,
    private val exchangeGateway: PhonebookExchangeGateway = NoOpPhonebookExchangeGateway,
) : Listener {
    @EventHandler
    fun onCharacterSwitch(event: LoadCharacterEvent) {
        closeBrowsingPhonebookOnCharacterSwitch(event.player)
    }

    fun closeBrowsingPhonebookOnCharacterSwitch(player: Player) {
        val holder = player.openInventory.topInventory.holder as? PhonebookBrowsingHolder
        if (holder != null) {
            holder.suppressReturnMenuOnClose()
            player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? PhonebookBrowsingHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        val actionMetadata = inventoryFactory.browserActionMetadata(event.currentItem)
        when (actionMetadata?.action) {
            PhonebookBrowserAction.AddContact -> {
                player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
                holder.suppressReturnMenuOnClose()
                player.closeInventory()
                exchangeGateway.startSeeking(player.uniqueId)
                return
            }
            PhonebookBrowserAction.PreviousPage -> {
                player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
                guiActions.previousPage(
                    BukkitPhonebookViewer(player, inventoryFactory),
                    PhonebookBrowserActionSelection(holder.state, actionMetadata.renderToken, actionMetadata.page, actionMetadata.action),
                )
                return
            }
            PhonebookBrowserAction.NextPage -> {
                player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
                guiActions.nextPage(
                    BukkitPhonebookViewer(player, inventoryFactory),
                    PhonebookBrowserActionSelection(holder.state, actionMetadata.renderToken, actionMetadata.page, actionMetadata.action),
                )
                return
            }
            null -> Unit
        }

        val selectedContact = inventoryFactory.contactMetadata(event.currentItem) ?: return
        val selection = PhonebookBrowserContactSelection(
            holderState = holder.state,
            itemRenderToken = selectedContact.renderToken,
            itemPage = selectedContact.page,
            itemSlot = selectedContact.slot,
            contactCharacterId = selectedContact.contactCharacterId,
        )

        if (event.click == ClickType.SWAP_OFFHAND) {
            player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
            guiActions.removeContact(BukkitPhonebookViewer(player, inventoryFactory), selection)
            return
        }

        if (!event.isLeftClick) return

        player.playSound(player, "lom:ui.button.click", SoundCategory.MASTER, 1f, 1f)
        holder.suppressReturnMenuOnClose()
        val result = guiActions.callContact(BukkitPhonebookViewer(player, inventoryFactory), selection)
        if (result != PhonebookContactClickResult.Called) {
            holder.allowReturnMenuOnClose()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? PhonebookBrowsingHolder ?: return
        val player = event.player as? Player ?: return
        if (holder.consumeReturnMenuSuppression()) return

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ms cast as ${player.name} item-mlmorb-menu")
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is PhonebookBrowsingHolder) {
            event.isCancelled = true
        }
    }
}

interface PhonebookExchangeGateway {
    fun startSeeking(initiatorAccountId: UUID)
}

private object NoOpPhonebookExchangeGateway : PhonebookExchangeGateway {
    override fun startSeeking(initiatorAccountId: UUID) = Unit
}
