package com.danidipp.sneakymisc.phonebook

import com.destroystokyo.paper.profile.ProfileProperty
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.kyori.adventure.text.Component
import net.sneakycharactermanager.paper.handlers.skins.SkinCache
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

enum class PhonebookBrowserAction {
    AddContact,
    PreviousPage,
    NextPage,
}

data class PhonebookContactItemMetadata(
    val renderToken: Long,
    val page: Int,
    val slot: Int,
    val contactCharacterId: UUID,
)

data class PhonebookBrowserActionMetadata(
    val renderToken: Long,
    val page: Int,
    val action: PhonebookBrowserAction,
)

data class PhonebookSkinUpdateTarget(
    val renderToken: Long,
    val page: Int,
    val slot: Int,
    val contactCharacterId: UUID,
) {
    fun canApply(holderState: PhonebookBrowserState, currentItem: PhonebookContactItemMetadata?): Boolean =
        currentItem != null &&
            renderToken == holderState.renderToken &&
            page == holderState.page &&
            currentItem.renderToken == renderToken &&
            currentItem.page == page &&
            currentItem.slot == slot &&
            currentItem.contactCharacterId == contactCharacterId
}

data class PhonebookResolvedHeadSkin(
    val texture: String,
    val signature: String,
)

interface PhonebookHeadResolver {
    fun resolve(player: Player, contact: VisiblePhonebookContact): CompletableFuture<PhonebookResolvedHeadSkin?>
}

object NoOpPhonebookHeadResolver : PhonebookHeadResolver {
    override fun resolve(player: Player, contact: VisiblePhonebookContact): CompletableFuture<PhonebookResolvedHeadSkin?> =
        CompletableFuture.completedFuture(null)
}

object SneakyCharacterPhonebookHeadResolver : PhonebookHeadResolver {
    override fun resolve(player: Player, contact: VisiblePhonebookContact): CompletableFuture<PhonebookResolvedHeadSkin?> {
        val skin = contact.skin ?: return CompletableFuture.completedFuture(null)
        if (!skin.texture.isNullOrBlank() && !skin.signature.isNullOrBlank()) {
            return CompletableFuture.completedFuture(PhonebookResolvedHeadSkin(skin.texture, skin.signature))
        }

        val skinUrl = skin.skin?.takeIf { it.isNotBlank() } ?: return CompletableFuture.completedFuture(null)
        return SkinCache.resolve(player, skinUrl, skin.slim).thenApply { result ->
            if (result.status == SkinCache.ResolveStatus.ERROR) {
                null
            } else if (!result.texture.isNullOrBlank() && !result.signature.isNullOrBlank()) {
                PhonebookResolvedHeadSkin(result.texture, result.signature)
            } else {
                null
            }
        }
    }
}

class PhonebookBrowsingHolder(state: PhonebookBrowserState) : InventoryHolder {
    private lateinit var backingInventory: Inventory
    var state: PhonebookBrowserState = state
        private set

    fun attach(inventory: Inventory) {
        backingInventory = inventory
    }

    fun updateState(state: PhonebookBrowserState) {
        this.state = state
    }

    override fun getInventory(): Inventory = backingInventory
}

class PhonebookInventoryFactory(private val plugin: Plugin, private val headResolver: PhonebookHeadResolver = SneakyCharacterPhonebookHeadResolver) {
    val contactCharacterKey = NamespacedKey(plugin, "phonebook_contact_character")
    private val browserActionKey = NamespacedKey(plugin, "phonebook_browser_action")
    private val renderTokenKey = NamespacedKey(plugin, "phonebook_render_token")
    private val pageKey = NamespacedKey(plugin, "phonebook_page")
    private val slotKey = NamespacedKey(plugin, "phonebook_slot")

    private companion object {
        const val ADD_CONTACT_ACTION = "add_contact"
    }

    fun create(model: PhonebookBrowserModel, player: Player? = null): Inventory {
        val holder = PhonebookBrowsingHolder(model.state)
        val inventory = Bukkit.createInventory(
            holder,
            PhonebookBrowserRenderer.INVENTORY_SIZE,
            Component.translatable(PhonebookMessageKeys.TITLE),
        )
        holder.attach(inventory)
        populate(inventory, model, player)

        return inventory
    }

    fun populate(inventory: Inventory, model: PhonebookBrowserModel, player: Player? = null) {
        (inventory.holder as? PhonebookBrowsingHolder)?.updateState(model.state)

        repeat(PhonebookBrowserRenderer.INVENTORY_SIZE) { slot ->
            inventory.setItem(slot, null)
        }

        for (item in model.contactItems) {
            inventory.setItem(item.slot, contactItem(model.state, item))
            if (player != null) {
                resolveHeadSkin(player, inventory, model.state, item)
            }
        }
        inventory.setItem(
            PhonebookBrowserRenderer.ADD_CONTACT_SLOT,
            actionItem(model.state, Material.LIME_DYE, PhonebookMessageKeys.EXCHANGE_ADD_CONTACT, PhonebookBrowserAction.AddContact),
        )
        if (model.hasPreviousPage) {
            inventory.setItem(
                PhonebookBrowserRenderer.PREVIOUS_PAGE_SLOT,
                actionItem(model.state, Material.ARROW, PhonebookMessageKeys.PREVIOUS_PAGE, PhonebookBrowserAction.PreviousPage),
            )
        }
        if (model.hasNextPage) {
            inventory.setItem(
                PhonebookBrowserRenderer.NEXT_PAGE_SLOT,
                actionItem(model.state, Material.ARROW, PhonebookMessageKeys.NEXT_PAGE, PhonebookBrowserAction.NextPage),
            )
        }
    }

    fun selectedContactCharacter(item: ItemStack?): UUID? =
        contactMetadata(item)?.contactCharacterId

    fun contactMetadata(item: ItemStack?): PhonebookContactItemMetadata? {
        val container = item?.itemMeta?.persistentDataContainer ?: return null
        val contactCharacterId = container.get(contactCharacterKey, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return null
        val renderToken = container.get(renderTokenKey, PersistentDataType.LONG) ?: return null
        val page = container.get(pageKey, PersistentDataType.INTEGER) ?: return null
        val slot = container.get(slotKey, PersistentDataType.INTEGER) ?: return null
        return PhonebookContactItemMetadata(renderToken, page, slot, contactCharacterId)
    }

    fun isAddContact(item: ItemStack?): Boolean =
        browserAction(item) == PhonebookBrowserAction.AddContact

    fun browserAction(item: ItemStack?): PhonebookBrowserAction? =
        browserActionMetadata(item)?.action

    fun browserActionMetadata(item: ItemStack?): PhonebookBrowserActionMetadata? {
        val container = item?.itemMeta?.persistentDataContainer ?: return null
        val value = container.get(browserActionKey, PersistentDataType.STRING)
            ?: return null
        val action = if (value == ADD_CONTACT_ACTION) {
            PhonebookBrowserAction.AddContact
        } else {
            runCatching { PhonebookBrowserAction.valueOf(value) }.getOrNull() ?: return null
        }
        val renderToken = container.get(renderTokenKey, PersistentDataType.LONG) ?: return null
        val page = container.get(pageKey, PersistentDataType.INTEGER) ?: return null
        return PhonebookBrowserActionMetadata(renderToken, page, action)
    }

    private fun contactItem(state: PhonebookBrowserState, browserItem: PhonebookBrowserContactItem): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta
        val contact = browserItem.contact
        meta.displayName(Component.text(contact.displayName))
        meta.persistentDataContainer.set(contactCharacterKey, PersistentDataType.STRING, contact.characterId.toString())
        meta.persistentDataContainer.set(renderTokenKey, PersistentDataType.LONG, state.renderToken)
        meta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, state.page)
        meta.persistentDataContainer.set(slotKey, PersistentDataType.INTEGER, browserItem.slot)
        item.itemMeta = meta
        return item
    }

    private fun actionItem(state: PhonebookBrowserState, material: Material, key: String, action: PhonebookBrowserAction): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.translatable(key))
        meta.persistentDataContainer.set(browserActionKey, PersistentDataType.STRING, action.name)
        meta.persistentDataContainer.set(renderTokenKey, PersistentDataType.LONG, state.renderToken)
        meta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, state.page)
        item.itemMeta = meta
        return item
    }

    private fun resolveHeadSkin(
        player: Player,
        inventory: Inventory,
        state: PhonebookBrowserState,
        browserItem: PhonebookBrowserContactItem,
    ) {
        val target = PhonebookSkinUpdateTarget(
            renderToken = state.renderToken,
            page = state.page,
            slot = browserItem.slot,
            contactCharacterId = browserItem.contact.characterId,
        )
        headResolver.resolve(player, browserItem.contact)
            .exceptionally { null }
            .thenAccept { skin ->
                if (skin == null) return@thenAccept
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    applyHeadSkin(inventory, target, skin)
                })
            }
    }

    fun applyHeadSkin(inventory: Inventory, target: PhonebookSkinUpdateTarget, skin: PhonebookResolvedHeadSkin) {
        val holder = inventory.holder as? PhonebookBrowsingHolder ?: return
        val currentItem = inventory.getItem(target.slot) ?: return
        if (!target.canApply(holder.state, contactMetadata(currentItem))) return

        val meta = currentItem.itemMeta as? SkullMeta ?: return
        val profile = Bukkit.createProfile(target.contactCharacterId)
        profile.setProperty(ProfileProperty("textures", skin.texture, skin.signature))
        meta.playerProfile = profile
        currentItem.itemMeta = meta
        inventory.setItem(target.slot, currentItem)
    }
}

