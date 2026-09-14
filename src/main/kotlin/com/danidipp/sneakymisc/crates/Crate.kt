package com.danidipp.sneakymisc.crates

import com.danidipp.sneakymisc.SneakyMisc
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.Tag
import org.bukkit.block.BlockFace
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.UUID

class Crate : InventoryHolder {
    companion object {
        private val crates: MutableMap<UUID, Crate> = mutableMapOf()

        val listener: Listener = object : Listener {
            @EventHandler
            fun onShulkerDeath(event: EntityDeathEvent) {
                val shulker = event.entity as? Shulker ?: return
                if (!shulker.scoreboardTags.contains(CrateUtils.ENTITY_TAG)) return

                val crate = get(shulker)
                if (crate == null) {
                    SneakyMisc.getInstance().logger.warning("Crate not found for shulker ${shulker.uniqueId}")
                    return
                }

                event.drops.clear()
                event.droppedExp = 0
                crate.poof()
            }

            @EventHandler(ignoreCancelled = true)
            fun onShulkerBoxPlace(event: BlockPlaceEvent) {
                if (!Tag.SHULKER_BOXES.isTagged(event.block.type)) return
                event.isCancelled = true
                val prepared = CrateMigration.prepare(event.itemInHand)
                if (prepared.resolution.definitionOrNull(event.player) == null) return
                val item = prepared.item.clone().apply { amount = 1 }
                Crate(item, event.block.location, event.player.facing)

                if (event.player.gameMode != GameMode.CREATIVE) {
                    val remainder = prepared.item.clone().apply { amount -= 1 }
                    event.player.inventory.setItem(event.hand, remainder)
                } else if (prepared.item !== event.itemInHand) {
                    event.player.inventory.setItem(event.hand, prepared.item)
                }
            }

            @EventHandler
            fun onCrateInteract(event: PlayerInteractEntityEvent) {
                if (event.hand != EquipmentSlot.HAND) return
                val shulker = event.rightClicked as? Shulker ?: return
                if (!shulker.scoreboardTags.contains(CrateUtils.ENTITY_TAG)) return

                event.isCancelled = true
                val crate = get(shulker) ?: Crate(shulker)
                if (crate.migratePlaced().definitionOrNull(event.player) == null) return
                if (crate.isValid(event.player)) event.player.openInventory(crate.inventory)
            }

            @EventHandler
            fun onHit(event: PrePlayerAttackEntityEvent) {
                val shulker = event.attacked as? Shulker ?: return
                if (!shulker.scoreboardTags.contains(CrateUtils.ENTITY_TAG)) return

                val crate = get(shulker) ?: Crate(shulker)
                crate.migratePlaced().definitionOrNull(event.player)
                Bukkit.getScheduler().runTask(SneakyMisc.getInstance(), Runnable {
                    if (shulker.isValid) crate.poof()
                })
            }
        }

        fun get(shulker: Shulker): Crate? = crates[shulker.uniqueId]

        fun get(uuid: UUID): Crate? = crates[uuid]

        fun get(backpack: Any): Crate? {
            val sourceId = backpackSourceId(backpack) ?: return null
            return crates[UUID(0L, sourceId.toLong())]
        }

        fun forget(backpack: Any) {
            val sourceId = backpackSourceId(backpack) ?: return
            crates.remove(UUID(0L, sourceId.toLong()))
        }

        private fun backpackSourceId(backpack: Any): Int? {
            val sourceId = runCatching { backpack.javaClass.getMethod("getSourceId").invoke(backpack) }.getOrNull()
            return (sourceId as? Number)?.toInt()
        }

        private fun backpackPlayer(backpack: Any): Player? = runCatching {
            backpack.javaClass.getMethod("getPlayer").invoke(backpack) as? Player
        }.getOrNull()

        private fun backpackShulkerBox(backpack: Any): ItemStack? = runCatching {
            backpack.javaClass.getMethod("getShulkerBox").invoke(backpack) as? ItemStack
        }.getOrNull()
    }

    private val type: CrateType
    private var inventoryInternal: Inventory
    private var backpack: Any?
    private var shulker: Shulker?
    private var itemDisplay: ItemDisplay?

    constructor(backpack: Any) {
        type = CrateType.BACKPACK
        inventoryInternal = inventoryFromShulkerBox(backpackShulkerBox(backpack))
            ?: Bukkit.createInventory(this, 36, Component.text("Crate"))
        this.backpack = backpack
        shulker = null
        itemDisplay = null
        val sourceId = backpackSourceId(backpack)
        if (sourceId != null) crates[UUID(0L, sourceId.toLong())] = this
    }

    constructor(item: ItemStack, location: Location, direction: BlockFace) {
        type = CrateType.SHULKER
        backpack = null
        itemDisplay = null
        item.amount = 1
        val itemMeta = item.itemMeta
        val name = if (itemMeta.hasDisplayName()) itemMeta.displayName() else null
        inventoryInternal = inventoryFromShulkerBox(item)
            ?: Bukkit.createInventory(this, 36, Component.text("Crate"))

        val createdShulker = CrateUtils.summonShulker(location, name, null)
        shulker = createdShulker
        createdShulker.equipment.setHelmet(item, true)
        createdShulker.equipment.helmetDropChance = 1.0f
        itemDisplay = CrateUtils.summonItemDisplay(location, direction, item)
        crates[createdShulker.uniqueId] = this
    }

    constructor(shulker: Shulker) {
        type = CrateType.SHULKER
        this.shulker = shulker
        backpack = null
        itemDisplay = shulker.getNearbyEntities(0.5, 0.5, 0.5)
            .asSequence()
            .filterIsInstance<ItemDisplay>()
            .firstOrNull { it.scoreboardTags.contains(CrateUtils.ENTITY_TAG) }

        val item = shulker.equipment.helmet
        if (item.type == Material.AIR) {
            SneakyMisc.getInstance().logger.severe("Shulker at ${shulker.location} has no helmet!")
            inventoryInternal = Bukkit.createInventory(this, 36, Component.text("Crate"))
        } else {
            inventoryInternal = inventoryFromShulkerBox(item) ?: run {
                SneakyMisc.getInstance().logger.severe("Couldn't get inventory from shulker at ${shulker.location}")
                Bukkit.createInventory(this, 36, Component.text("Crate"))
            }
            crates[shulker.uniqueId] = this
        }
    }

    fun isValid(player: Player? = null): Boolean {
        return resolution.definitionOrNull(player) != null && hasSource()
    }

    fun replaceBackpackItem(item: ItemStack) {
        check(inventoryInternal.viewers.isEmpty()) { "Cannot replace an open backpack." }
        val source = backpack ?: return
        source.javaClass.getMethod("setShulkerBox", ItemStack::class.java).invoke(source, item)
        forget(source)
    }

    private fun migratePlaced(): CrateResolution {
        val original = resolution
        if (original is CrateResolution.Success || type != CrateType.SHULKER) return original
        if (inventoryInternal.viewers.isNotEmpty()) return CrateResolution.Failure("Close the crate before migrating it.")
        val source = shulker?.equipment?.helmet ?: return original
        val prepared = CrateMigration.prepare(source)
        if (prepared.item !== source) {
            shulker?.equipment?.setHelmet(prepared.item, true)
            shulker?.color = null
            shulker?.customName(prepared.item.itemMeta.displayName())
            itemDisplay?.setItemStack(prepared.item)
            inventoryInternal = inventoryFromShulkerBox(prepared.item) ?: inventoryInternal
        }
        return prepared.resolution
    }

    private fun hasSource(): Boolean {
        if (type == CrateType.SHULKER) return shulker?.isValid == true

        val backpack = backpack ?: return false
        val player = backpackPlayer(backpack) ?: return false
        val itemInHand = player.inventory.itemInMainHand
        val expected = backpackShulkerBox(backpack)
        return when {
            !Tag.SHULKER_BOXES.isTagged(itemInHand.type) -> {
                SneakyMisc.getInstance().logger.warning("Player ${player.name} has no shulkerbox in main hand!")
                false
            }
            expected == null || itemInHand != expected -> {
                SneakyMisc.getInstance().logger.warning("Player ${player.name} has different shulkerbox in main hand!")
                false
            }
            else -> true
        }
    }

    private fun inventoryFromShulkerBox(item: ItemStack?): Inventory? {
        if (item == null || !Tag.SHULKER_BOXES.isTagged(item.type)) {
            SneakyMisc.getInstance().logger.warning("Requested inventory from non-shulkerbox item!")
            return null
        }
        val itemMeta = item.itemMeta
        val shulkerBox = (itemMeta as? BlockStateMeta)?.blockState as? ShulkerBox
        if (shulkerBox == null) {
            SneakyMisc.getInstance().logger.warning("Requested inventory from non-shulkerbox item!")
            return null
        }

        val name = itemMeta.displayName() ?: Component.translatable(item.type.translationKey())
        val inventory = Bukkit.createInventory(this, 36, name)
        val definition = CrateDefinitions.resolve(item).definitionOrNull()
        definition?.let { inventory.maxStackSize = it.maxStackSize }
        val oldContents = shulkerBox.inventory.contents
        val contents = arrayOfNulls<ItemStack>(36)
        for (slot in contents.indices) {
            if (slot < oldContents.size) contents[slot] = oldContents[slot]
            if (contents[slot] == null) contents[slot] = CrateGui.getFiller(slot, definition)
        }
        inventory.contents = contents
        return inventory
    }

    fun save() {
        if (type == CrateType.BACKPACK && !hasSource()) return
        val contents = inventoryInternal.contents
            .take(27)
            .map { stack ->
                stack?.takeUnless(CrateGui::isDecoration)
            }
            .toTypedArray()

        val item = if (type == CrateType.BACKPACK) {
            backpack?.let(::backpackShulkerBox)
        } else {
            shulker?.equipment?.helmet
        }

        if (item != null && Tag.SHULKER_BOXES.isTagged(item.type)) {
            val itemMeta = item.itemMeta as? BlockStateMeta
            val shulkerState = itemMeta?.blockState as? ShulkerBox
            if (itemMeta != null && shulkerState != null) {
                shulkerState.inventory.contents = contents
                shulkerState.update()
                itemMeta.blockState = shulkerState
                item.itemMeta = itemMeta

                if (type == CrateType.BACKPACK) {
                    backpack?.let(::backpackPlayer)?.inventory?.setItemInMainHand(item)
                } else {
                    shulker?.equipment?.setHelmet(item, true)
                }
            } else {
                val locationDescription = if (type == CrateType.BACKPACK) {
                    backpack?.let(::backpackPlayer)?.name?.let { "$it's backpack" } ?: "unknown backpack"
                } else {
                    shulker?.location.toString()
                }
                SneakyMisc.getInstance().logger.severe("Non-shulkerbox item in Shulker at $locationDescription!")
                CrateUtils.trySaveContents(contents, uuid.toString())
            }
        } else {
            SneakyMisc.getInstance().logger.severe("Non-shulkerbox item in Shulker at $location!")
        }
    }

    fun poof() {
        if (type == CrateType.BACKPACK) {
            val player = backpack?.let(::backpackPlayer)
            SneakyMisc.getInstance().logger.severe("Poof: ${player?.name} tried to poof a backpack!")
            player?.sendMessage(Component.text("You can't poof a backpack!", NamedTextColor.RED))
            return
        }

        inventoryInternal.viewers.toList().forEach { it.closeInventory() }
        val item = shulkerBox
        val shulker = shulker
        if (item != null && shulker != null) {
            shulker.world.dropItem(shulker.location, item)
            crates.remove(shulker.uniqueId)
            itemDisplay?.remove()
            shulker.world.playSound(shulker.location, Sound.ENTITY_CHICKEN_EGG, SoundCategory.BLOCKS, 1.0f, 0.5f)
            shulker.remove()
        } else {
            SneakyMisc.getInstance().logger.severe("Poof: Can't get shulkerbox item!")
        }
    }

    override fun getInventory(): Inventory = inventoryInternal

    val shulkerBox: ItemStack?
        get() {
            save()
            return if (type == CrateType.BACKPACK) {
                backpack?.let(::backpackShulkerBox)
            } else {
                shulker?.equipment?.helmet
            }
        }

    val definition: CrateDefinition?
        get() = resolution.definitionOrNull()

    private val resolution: CrateResolution
        get() = CrateDefinitions.resolve(
            if (type == CrateType.BACKPACK) backpack?.let(::backpackShulkerBox)
            else shulker?.equipment?.helmet,
        )

    val location: Location
        get() = if (type == CrateType.BACKPACK) {
            backpack?.let(::backpackPlayer)?.location ?: Bukkit.getWorlds()[0].spawnLocation
        } else {
            shulker?.location ?: Bukkit.getWorlds()[0].spawnLocation
        }

    val uuid: UUID
        get() = if (type == CrateType.BACKPACK) {
            UUID(0L, (backpack?.let(::backpackSourceId) ?: 0).toLong())
        } else {
            shulker?.uniqueId ?: UUID.randomUUID()
        }
}
