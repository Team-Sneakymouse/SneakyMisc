package com.danidipp.sneakymisc.crates

import com.danidipp.sneakymisc.SneakyMisc
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Shulker
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import java.io.File

object CrateUtils {
    const val ENTITY_TAG = "dipp.crate"

    fun summonShulker(location: Location, name: Component?, color: DyeColor?): Shulker {
        val tempLocation = location.clone().add(0.0, -2.0, 0.0)
        val shulker = tempLocation.world.spawnEntity(tempLocation, EntityType.SHULKER) as Shulker
        shulker.addScoreboardTag(ENTITY_TAG)
        shulker.setAI(false)
        shulker.isInvulnerable = true
        shulker.isSilent = true
        shulker.setGravity(false)
        shulker.isInvisible = true
        shulker.customName(name)
        shulker.isCustomNameVisible = false
        shulker.color = color
        Bukkit.getScheduler().runTaskLater(
            SneakyMisc.getInstance(),
            Runnable { shulker.teleport(location) },
            1L,
        )
        return shulker
    }

    fun summonItemDisplay(location: Location, direction: BlockFace, item: ItemStack): ItemDisplay {
        val spawnLocation = location.clone().add(0.5, 0.5, 0.5)
        val itemDisplay = spawnLocation.world.spawnEntity(spawnLocation, EntityType.ITEM_DISPLAY) as ItemDisplay
        itemDisplay.addScoreboardTag(ENTITY_TAG)
        itemDisplay.setItemStack(item)
        itemDisplay.setRotation(
            when (direction) {
                BlockFace.NORTH -> 0f
                BlockFace.EAST -> 90f
                BlockFace.SOUTH -> 180f
                BlockFace.WEST -> 270f
                else -> 0f
            },
            0f,
        )
        val transform: Transformation = itemDisplay.transformation
        transform.scale.set(0.875f)
        transform.translation.set(0.0f, -0.125f, 0.0f)
        itemDisplay.transformation = transform
        return itemDisplay
    }

    fun makeItem(material: Material, customModelData: Int): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.setHideTooltip(true)
        meta.setCustomModelData(customModelData)
        item.itemMeta = meta
        return item
    }

    fun trySaveContents(contents: Array<ItemStack?>, uuid: String) {
        val plugin = SneakyMisc.getInstance()
        val file = File(plugin.dataFolder, "$uuid.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("contents", contents.filterNotNull().map(ItemStack::serialize))

        try {
            config.save(file)
        } catch (exception: Exception) {
            plugin.logger.severe("Couldn't save crate contents!")
            exception.printStackTrace()
        }

        plugin.logger.severe("Saving potentially lost items: $uuid.yml")
    }
}
