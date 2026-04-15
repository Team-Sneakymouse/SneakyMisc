package com.danidipp.sneakymisc.grounditems

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import io.papermc.paper.datacomponent.DataComponentTypes
import net.coreprotect.CoreProtect
import net.coreprotect.CoreProtectAPI
import net.coreprotect.listener.entity.EntityPickupItemListener
import net.coreprotect.listener.player.PlayerDropItemListener
import net.kyori.adventure.text.Component
import io.papermc.paper.event.player.PlayerPickItemEvent
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.command.CommandSender
import org.bukkit.FluidCollisionMode
import org.bukkit.Effect
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Item
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataHolder
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID

class GroundItemsModule : SneakyModule(), Listener {
    companion object {
        val deps = listOf<String>()

        private const val MAX_DISTANCE = 2.5
        private const val POP_RAYCAST_DISTANCE = 8.0
        private const val SURFACE_EPSILON = 0.02
        private const val SURFACE_RANDOM_OFFSET = 0.01
        private const val INTERACTION_SIZE = 0.5f
        private const val RAYTRACE_SIZE = 0.25
        private const val SOUND_COOLDOWN_MS = 500L

        private const val KIND_DISPLAY = "display"
        private const val KIND_INTERACTION = "interaction"
        private const val PERMANENT_LOCK_VALUE = "permanent"
        private const val GROUND_ITEM_TAG = "grounditem"

        const val ADMIN_PERMISSION = "sneakymisc.command.grounditem.admin"
    }

    enum class LockMode {
        NONE,
        TEMPORARY,
        PERMANENT
    }

    data class GroundItemTarget(
        val display: ItemDisplay,
        val interaction: Interaction,
        val pairId: String,
    )

    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(GroundItemCommand(this).build(), "Lock or unlock ground items")
    )
    override val listeners: List<Listener> = listOf(this)

    private val plugin = SneakyMisc.getInstance()
    private val kindKey = NamespacedKey(plugin, "ground_item_kind")
    private val pairIdKey = NamespacedKey(plugin, "ground_item_pair_id")
    private val partnerUuidKey = NamespacedKey(plugin, "ground_item_partner_uuid")
    private val permanentLockModeKey = NamespacedKey(plugin, "ground_item_lock_mode")
    private val temporaryLockDateKey = NamespacedKey(plugin, "ground_item_temporary_lock_date")
    private val soulboundOwnerKey = NamespacedKey.fromString("magicspells:soulbound_owner")
        ?: throw IllegalStateException("Failed to create NamespacedKey for magicspells:soulbound_owner")
    private val lockZone = ZoneId.of("UTC-07:00")
    private val lastGroundItemCreationByPlayer = mutableMapOf<UUID, Long>()
    private val lastFeedbackSoundByPlayer = mutableMapOf<UUID, Long>()
    private val coreProtectApi: CoreProtectAPI? by lazy {
        val plugin = Bukkit.getPluginManager().getPlugin("CoreProtect") as? CoreProtect ?: return@lazy null
        plugin.api?.takeIf { it.isEnabled }
    }

    init {
        processLoadedChunks()
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.isCancelled) return
        if (isMagicSpellsDropEvent()) return

        val world = event.player.world
        val eyeLocation = event.player.eyeLocation
        val forbiddenHit = world.rayTraceBlocks(
            eyeLocation,
            eyeLocation.direction,
            MAX_DISTANCE,
            FluidCollisionMode.ALWAYS,
            false
        )
        if (isVanillaThrowBlock(forbiddenHit?.hitBlock)) return

        val result = world.rayTraceBlocks(
            eyeLocation,
            eyeLocation.direction,
            MAX_DISTANCE,
            FluidCollisionMode.NEVER,
            true
        ) ?: return

        val hitPosition = result.hitPosition ?: return
        val hitFace = result.hitBlockFace ?: return
        val droppedStack = event.itemDrop.itemStack.clone()
        val placement = buildPlacement(event.player.location.yaw, hitPosition.toLocation(world), hitFace, droppedStack)

        if (tryMergeIntoNearbyDisplay(event, placement.location, droppedStack)) {
            return
        }

        if (!canCreateGroundItem(event.player.uniqueId)) {
            event.isCancelled = true
            return
        }

        if (!spawnPair(placement, droppedStack)) return

        markGroundItemCreated(event.player.uniqueId)
        playGroundItemFeedback(event.player.uniqueId, placement.location, "lom:sfx.clothingequip")
        logCoreProtectDrop(event.player, placement.location, droppedStack)
        event.itemDrop.remove()
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val interaction = event.entity as? Interaction ?: return
        if (!hasKind(interaction, KIND_INTERACTION)) return

        val target = resolveGroundItemTarget(interaction) ?: run {
            interaction.remove()
            return
        }
        if (getLockMode(target.display) != LockMode.NONE) {
            player.playEffect(target.display.location, Effect.SMOKE, BlockFace.UP)
            return
        }
        if (!isPickupAllowed(player.uniqueId, target.display)) return

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val stack = target.display.itemStack.clone()
            val pickupLocation = target.display.location.clone()
            removePair(target.display, target.interaction)

            val onlinePlayer = Bukkit.getPlayer(player.uniqueId)
            if (onlinePlayer == null || !onlinePlayer.isOnline) {
                pickupLocation.world.dropItemNaturally(pickupLocation, stack)
                return@Runnable
            }

            val overflow = giveCollectedItem(onlinePlayer, stack)
            for (item in overflow) {
                onlinePlayer.world.dropItemNaturally(onlinePlayer.location, item)
            }

            playGroundItemFeedback(onlinePlayer.uniqueId, pickupLocation, "lom:sfx.clothingunequip")
            logCoreProtectPickup(onlinePlayer, pickupLocation, stack)
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        lastGroundItemCreationByPlayer.remove(event.player.uniqueId)
        lastFeedbackSoundByPlayer.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        processChunk(event.chunk)
    }

    @EventHandler
    fun onPlayerPickItem(event: PlayerPickItemEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) return

        val target = findLookedAtGroundItem(event.player) ?: return
        event.isCancelled = true
        event.player.inventory.setItem(event.targetSlot, target.display.itemStack.clone())
        event.player.inventory.heldItemSlot = event.targetSlot
    }

    fun findLookedAtInteraction(player: Player): Interaction? {
        val result = player.world.rayTraceEntities(
            player.eyeLocation,
            player.eyeLocation.direction,
            MAX_DISTANCE,
            RAYTRACE_SIZE
        ) { entity ->
            entity is Interaction && hasKind(entity, KIND_INTERACTION)
        } ?: return null

        return result.hitEntity as? Interaction
    }

    fun findLookedAtGroundItem(player: Player): GroundItemTarget? {
        val interaction = findLookedAtInteraction(player) ?: return null
        return resolveGroundItemTarget(interaction)
    }

    fun findPopLocation(player: Player): Location? {
        val result = player.world.rayTraceBlocks(
            player.eyeLocation,
            player.eyeLocation.direction,
            POP_RAYCAST_DISTANCE,
            FluidCollisionMode.NEVER,
            true
        ) ?: return null

        return result.hitPosition?.toLocation(player.world)
    }

    fun popGroundItems(center: Location, radius: Double): Int {
        val targets = collectPopTargets(center, radius)
        for (target in targets) {
            popGroundItem(target)
        }
        return targets.size
    }

    fun resolveGroundItemTarget(entity: Entity): GroundItemTarget? {
        val pairId = getPairId(entity) ?: return null
        return when {
            entity is Interaction && hasKind(entity, KIND_INTERACTION) -> {
                val display = resolvePartner(entity, KIND_DISPLAY) as? ItemDisplay ?: return null
                GroundItemTarget(display, entity, pairId)
            }

            entity is ItemDisplay && hasKind(entity, KIND_DISPLAY) -> {
                val interaction = resolvePartner(entity, KIND_INTERACTION) as? Interaction ?: return null
                GroundItemTarget(entity, interaction, pairId)
            }

            else -> null
        }
    }

    fun lockTarget(actor: CommandSender, target: GroundItemTarget): LockMode {
        return when (determineLockMode(actor, target.display.location)) {
            LockMode.PERMANENT -> {
                setPermanentLock(target)
                LockMode.PERMANENT
            }

            else -> {
                setTemporaryLock(target)
                LockMode.TEMPORARY
            }
        }
    }

    fun getLockMode(target: GroundItemTarget): LockMode = getLockMode(target.display)

    fun canUnlock(actor: CommandSender, target: GroundItemTarget): Boolean {
        if (actor.hasPermission(ADMIN_PERMISSION)) return true

        val player = actor as? Player ?: return false
        return isPlotMember(player, target.display.location)
    }

    fun unlockTarget(target: GroundItemTarget) {
        clearLock(target)
    }

    fun formatBoundMessage(stack: ItemStack, temporary: Boolean): Component {
        val suffix = if (temporary) {
            " is now bound in place for the rest of the day"
        } else {
            " is now bound in place"
        }
        return stack.displayName().append(Component.text(suffix))
    }

    fun formatUnboundMessage(stack: ItemStack): Component {
        return stack.displayName().append(Component.text(" is now unbound"))
    }

    fun formatAlreadyBoundMessage(stack: ItemStack): Component {
        return stack.displayName().append(Component.text(" is already bound in place"))
    }

    fun formatAlreadyUnboundMessage(stack: ItemStack): Component {
        return stack.displayName().append(Component.text(" is already unbound"))
    }

    private fun processLoadedChunks() {
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                processChunk(chunk)
            }
        }
    }

    private fun processChunk(chunk: Chunk) {
        val processedPairs = mutableSetOf<String>()

        for (entity in chunk.entities) {
            if (!isTaggedGroundItem(entity)) continue

            val pairId = getPairId(entity)
            if (pairId != null && !processedPairs.add(pairId)) continue

            val target = resolveGroundItemTarget(entity)
            if (target == null) {
                entity.remove()
                continue
            }

            clearExpiredTemporaryLock(target)
        }
    }

    private fun spawnPair(placement: Placement, stack: ItemStack): Boolean {
        var display: ItemDisplay? = null
        var interaction: Interaction? = null

        return runCatching {
            val pairId = UUID.randomUUID().toString()

            display = placement.location.world.spawn(placement.location, ItemDisplay::class.java) { itemDisplay ->
                itemDisplay.isPersistent = true
                itemDisplay.setGravity(false)
                itemDisplay.setItemStack(stack)
                itemDisplay.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                itemDisplay.billboard = Display.Billboard.FIXED
                itemDisplay.transformation = placement.transformation
                itemDisplay.setRotation(0.0f, 0.0f)
                tagEntity(itemDisplay, KIND_DISPLAY, pairId, "")
            }

            interaction = placement.interactionLocation.world.spawn(placement.interactionLocation, Interaction::class.java) { target ->
                target.isPersistent = true
                target.setGravity(false)
                target.isResponsive = true
                target.interactionWidth = INTERACTION_SIZE
                target.interactionHeight = INTERACTION_SIZE
                tagEntity(target, KIND_INTERACTION, pairId, "")
            }

            val spawnedDisplay = display ?: return@runCatching false
            val spawnedInteraction = interaction ?: return@runCatching false

            setPartner(spawnedDisplay, spawnedInteraction.uniqueId)
            setPartner(spawnedInteraction, spawnedDisplay.uniqueId)
            true
        }.getOrElse {
            display?.remove()
            interaction?.remove()
            false
        }
    }

    private fun buildPlacement(playerYaw: Float, hitPosition: Location, hitFace: BlockFace, stack: ItemStack): Placement {
        val normal = hitFace.direction.clone().normalize()
        val randomOffset = ThreadLocalRandom.current().nextDouble(0.0, SURFACE_RANDOM_OFFSET)
        val anchor = hitPosition.clone()
            .add(normal.clone().multiply(SURFACE_EPSILON))
            .add(normal.clone().multiply(randomOffset))
        return Placement(
            location = anchor,
            interactionLocation = anchor.clone(),
            transformation = transformationForFace(hitFace, playerYaw, scaleForStack(stack))
        )
    }

    private fun tryMergeIntoNearbyDisplay(
        event: PlayerDropItemEvent,
        placementLocation: Location,
        droppedStack: ItemStack,
    ): Boolean {
        val mergeTarget = findMergeTarget(placementLocation, droppedStack) ?: return false
        val currentStack = mergeTarget.itemStack.clone()
        val maxStackSize = currentStack.maxStackSize.coerceAtLeast(1)
        val spaceLeft = maxStackSize - currentStack.amount
        if (spaceLeft <= 0) return false

        val mergeAmount = minOf(spaceLeft, droppedStack.amount)
        currentStack.amount += mergeAmount
        mergeTarget.setItemStack(currentStack)
        mergeTarget.transformation = rescaleTransformation(mergeTarget.transformation, scaleForStack(currentStack))
        playGroundItemFeedback(event.player.uniqueId, mergeTarget.location, "lom:sfx.clothingequip")
        logCoreProtectDrop(event.player, mergeTarget.location, droppedStack.clone().apply { amount = mergeAmount })

        val remainder = droppedStack.amount - mergeAmount
        if (remainder > 0) {
            val remainderStack = droppedStack.clone().apply { amount = remainder }
            val overflow = event.player.inventory.addItem(remainderStack).values
            for (item in overflow) {
                event.player.world.dropItemNaturally(event.player.location, item)
            }
        }

        event.itemDrop.remove()
        return true
    }

    private fun findMergeTarget(location: Location, stack: ItemStack): ItemDisplay? {
        return location.world.getNearbyEntities(location, 0.25, 0.25, 0.25)
            .asSequence()
            .filterIsInstance<ItemDisplay>()
            .filter { hasKind(it, KIND_DISPLAY) }
            .filter { getLockMode(it) == LockMode.NONE }
            .filter { it.itemStack.isSimilar(stack) }
            .filter { it.itemStack.amount < it.itemStack.maxStackSize.coerceAtLeast(1) }
            .minByOrNull { it.location.distanceSquared(location) }
    }

    private fun collectPopTargets(center: Location, radius: Double): List<GroundItemTarget> {
        val world = center.world ?: return emptyList()
        val radiusSquared = radius * radius
        val processedPairs = mutableSetOf<String>()
        val targets = mutableListOf<GroundItemTarget>()

        for (entity in world.getNearbyEntities(center, radius, radius, radius)) {
            if (!isTaggedGroundItem(entity)) continue

            val pairId = getPairId(entity)
            if (pairId != null && !processedPairs.add(pairId)) continue

            val target = resolveGroundItemTarget(entity)
            if (target == null) {
                entity.remove()
                continue
            }
            if (getLockMode(target) != LockMode.NONE) continue
            if (target.display.location.world != world) continue
            if (target.display.location.distanceSquared(center) > radiusSquared) continue

            targets.add(target)
        }

        return targets
    }

    private fun popGroundItem(target: GroundItemTarget) {
        val stack = target.display.itemStack.clone()
        val dropLocation = target.display.location.clone()
        removePair(target.display, target.interaction)
        spawnVanillaDrop(dropLocation, stack)
    }

    private fun spawnVanillaDrop(location: Location, stack: ItemStack) {
        location.world.dropItem(location, stack)
    }

    private fun determineLockMode(actor: CommandSender, location: Location): LockMode {
        val player = actor as? Player
        if (player != null && isPlotMember(player, location)) {
            return LockMode.PERMANENT
        }
        if (actor.hasPermission(ADMIN_PERMISSION) && player?.gameMode == GameMode.CREATIVE) {
            return LockMode.PERMANENT
        }
        return LockMode.TEMPORARY
    }

    private fun isPlotMember(player: Player, location: Location): Boolean {
        val world = location.world ?: return false
        val worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard") as? WorldGuardPlugin ?: return false
        val regionManager = WorldGuard.getInstance().platform.regionContainer[BukkitAdapter.adapt(world)] ?: return false
        val applicableRegions = regionManager.getApplicableRegions(BlockVector3.at(location.x, location.y, location.z))
        val worldGuardPlayer = worldGuardPlugin.wrapPlayer(player)

        return applicableRegions.any { region ->
            region.priority == 5 && (region.isOwner(worldGuardPlayer) || region.isMember(worldGuardPlayer))
        }
    }

    private fun todayLockDate(): String = LocalDate.now(lockZone).toString()

    private fun getLockMode(display: ItemDisplay): LockMode {
        val data = display.persistentDataContainer
        return when {
            data.get(permanentLockModeKey, PersistentDataType.STRING) == PERMANENT_LOCK_VALUE -> LockMode.PERMANENT
            data.has(temporaryLockDateKey, PersistentDataType.STRING) -> LockMode.TEMPORARY
            else -> LockMode.NONE
        }
    }

    private fun clearExpiredTemporaryLock(target: GroundItemTarget) {
        val temporaryDate = target.display.persistentDataContainer.get(temporaryLockDateKey, PersistentDataType.STRING) ?: return
        if (temporaryDate == todayLockDate()) return

        target.display.persistentDataContainer.remove(temporaryLockDateKey)
        target.interaction.persistentDataContainer.remove(temporaryLockDateKey)
    }

    private fun setPermanentLock(target: GroundItemTarget) {
        target.display.persistentDataContainer.set(permanentLockModeKey, PersistentDataType.STRING, PERMANENT_LOCK_VALUE)
        target.interaction.persistentDataContainer.set(permanentLockModeKey, PersistentDataType.STRING, PERMANENT_LOCK_VALUE)
        target.display.persistentDataContainer.remove(temporaryLockDateKey)
        target.interaction.persistentDataContainer.remove(temporaryLockDateKey)
    }

    private fun setTemporaryLock(target: GroundItemTarget) {
        val date = todayLockDate()
        target.display.persistentDataContainer.remove(permanentLockModeKey)
        target.interaction.persistentDataContainer.remove(permanentLockModeKey)
        target.display.persistentDataContainer.set(temporaryLockDateKey, PersistentDataType.STRING, date)
        target.interaction.persistentDataContainer.set(temporaryLockDateKey, PersistentDataType.STRING, date)
    }

    private fun clearLock(target: GroundItemTarget) {
        target.display.persistentDataContainer.remove(permanentLockModeKey)
        target.interaction.persistentDataContainer.remove(permanentLockModeKey)
        target.display.persistentDataContainer.remove(temporaryLockDateKey)
        target.interaction.persistentDataContainer.remove(temporaryLockDateKey)
    }

    private fun isPickupAllowed(playerUuid: UUID, display: ItemDisplay): Boolean {
        return !isSoulboundToAnotherPlayer(playerUuid, display.itemStack)
    }

    private fun giveCollectedItem(player: Player, stack: ItemStack): Collection<ItemStack> {
        if (player.inventory.itemInMainHand.type.isAir) {
            player.inventory.setItem(player.inventory.heldItemSlot, stack)
            return emptyList()
        }

        return player.inventory.addItem(stack).values
    }

    private fun isSoulboundToAnotherPlayer(playerUuid: UUID, stack: ItemStack): Boolean {
        val meta = stack.itemMeta ?: return false
        val owner = meta.persistentDataContainer.get(soulboundOwnerKey, PersistentDataType.STRING) ?: return false
        val ownerUuid = runCatching { UUID.fromString(owner) }.getOrNull() ?: return true
        return ownerUuid != playerUuid
    }

    private fun rescaleTransformation(existing: Transformation, stackScale: Float): Transformation {
        return Transformation(
            Vector3f(existing.translation),
            Quaternionf(existing.leftRotation),
            Vector3f(stackScale, stackScale, stackScale),
            Quaternionf(existing.rightRotation)
        )
    }

    private fun canCreateGroundItem(playerId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val lastCreatedAt = lastGroundItemCreationByPlayer[playerId] ?: 0L
        return now - lastCreatedAt >= SOUND_COOLDOWN_MS
    }

    private fun isMagicSpellsDropEvent(): Boolean {
        return Thread.currentThread().stackTrace.any { element ->
            element.className.startsWith("com.nisovin.magicspells")
        }
    }

    private fun isVanillaThrowBlock(block: Block?): Boolean {
        val type = block?.type ?: return false
        return type == Material.FIRE ||
            type == Material.SOUL_FIRE ||
            type == Material.WATER ||
            type == Material.LAVA ||
            type.name.endsWith("CAULDRON")
    }

    private fun markGroundItemCreated(playerId: UUID) {
        lastGroundItemCreationByPlayer[playerId] = System.currentTimeMillis()
    }

    private fun playGroundItemFeedback(playerId: UUID, location: Location, sound: String) {
        location.world.spawnParticle(Particle.WHITE_SMOKE, location, 1, 0.0, 0.0, 0.0, 0.0)
        val now = System.currentTimeMillis()
        val lastPlayedAt = lastFeedbackSoundByPlayer[playerId] ?: 0L
        if (now - lastPlayedAt < SOUND_COOLDOWN_MS) return

        lastFeedbackSoundByPlayer[playerId] = now
        location.world.playSound(location, sound, SoundCategory.MASTER, 0.25f, 1.75f)
    }

    private fun logCoreProtectDrop(player: Player, location: Location, stack: ItemStack) {
        if (coreProtectApi == null) return
        PlayerDropItemListener.playerDropItem(location, player.name, stack)
    }

    private fun logCoreProtectPickup(player: Player, location: Location, stack: ItemStack) {
        if (coreProtectApi == null) return
        EntityPickupItemListener.onItemPickup(player, location, stack)
    }

    private fun transformationForFace(face: BlockFace, playerYaw: Float, stackScale: Float): Transformation {
        val rotation = when (face) {
            BlockFace.UP -> Quaternionf()
                .rotateY(Math.toRadians((-playerYaw + 180.0)).toFloat())
                .rotateX((-Math.PI / 2.0).toFloat())

            BlockFace.DOWN -> Quaternionf()
                .rotateY(Math.toRadians((-playerYaw + 180.0)).toFloat())
                .rotateX((Math.PI / 2.0).toFloat())

            BlockFace.NORTH -> Quaternionf().rotateY(Math.PI.toFloat())
            BlockFace.SOUTH -> Quaternionf()
            BlockFace.EAST -> Quaternionf().rotateY((Math.PI / 2.0).toFloat())
            BlockFace.WEST -> Quaternionf().rotateY((-Math.PI / 2.0).toFloat())
            else -> Quaternionf()
        }

        return Transformation(
            Vector3f(),
            rotation,
            Vector3f(stackScale, stackScale, stackScale),
            Quaternionf()
        )
    }

    private fun scaleForStack(stack: ItemStack): Float {
        val maxStackSize = stack.maxStackSize.coerceAtLeast(1)
        if (maxStackSize == 1) return 2.0f

        val amount = stack.amount.coerceIn(1, maxStackSize)
        val progress = (amount - 1).toFloat() / (maxStackSize - 1).toFloat()
        return 0.5f + (progress * 1.5f)
    }

    private fun tagEntity(entity: PersistentDataHolder, kind: String, pairId: String, partnerUuid: String) {
        entity.persistentDataContainer.set(kindKey, PersistentDataType.STRING, kind)
        entity.persistentDataContainer.set(pairIdKey, PersistentDataType.STRING, pairId)
        entity.persistentDataContainer.set(partnerUuidKey, PersistentDataType.STRING, partnerUuid)
        (entity as? Entity)?.addScoreboardTag(GROUND_ITEM_TAG)
    }

    private fun getPairId(entity: Entity): String? {
        return entity.persistentDataContainer.get(pairIdKey, PersistentDataType.STRING)
    }

    private fun setPartner(entity: PersistentDataHolder, partnerUuid: UUID) {
        entity.persistentDataContainer.set(partnerUuidKey, PersistentDataType.STRING, partnerUuid.toString())
    }

    private fun hasKind(entity: Entity, expectedKind: String): Boolean {
        return entity.persistentDataContainer.get(kindKey, PersistentDataType.STRING) == expectedKind
    }

    private fun isTaggedGroundItem(entity: Entity): Boolean {
        return entity.persistentDataContainer.has(kindKey, PersistentDataType.STRING)
    }

    private fun resolvePartner(entity: Entity, expectedKind: String): Entity? {
        val partnerId = entity.persistentDataContainer.get(partnerUuidKey, PersistentDataType.STRING) ?: return null
        val partnerUuid = runCatching { UUID.fromString(partnerId) }.getOrNull() ?: return null
        val partner = Bukkit.getEntity(partnerUuid) ?: return null
        return partner.takeIf { hasKind(it, expectedKind) }
    }

    private fun removePair(display: ItemDisplay, interaction: Interaction) {
        display.remove()
        interaction.remove()
    }

    private data class Placement(
        val location: Location,
        val interactionLocation: Location,
        val transformation: Transformation,
    )
}
