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

    fun getColor(itemMaterial: Material): DyeColor? = when (itemMaterial) {
        Material.SHULKER_BOX -> null
        Material.WHITE_SHULKER_BOX -> DyeColor.WHITE
        Material.ORANGE_SHULKER_BOX -> DyeColor.ORANGE
        Material.MAGENTA_SHULKER_BOX -> DyeColor.MAGENTA
        Material.LIGHT_BLUE_SHULKER_BOX -> DyeColor.LIGHT_BLUE
        Material.YELLOW_SHULKER_BOX -> DyeColor.YELLOW
        Material.LIME_SHULKER_BOX -> DyeColor.LIME
        Material.PINK_SHULKER_BOX -> DyeColor.PINK
        Material.GRAY_SHULKER_BOX -> DyeColor.GRAY
        Material.LIGHT_GRAY_SHULKER_BOX -> DyeColor.LIGHT_GRAY
        Material.CYAN_SHULKER_BOX -> DyeColor.CYAN
        Material.PURPLE_SHULKER_BOX -> DyeColor.PURPLE
        Material.BLUE_SHULKER_BOX -> DyeColor.BLUE
        Material.BROWN_SHULKER_BOX -> DyeColor.BROWN
        Material.GREEN_SHULKER_BOX -> DyeColor.GREEN
        Material.RED_SHULKER_BOX -> DyeColor.RED
        Material.BLACK_SHULKER_BOX -> DyeColor.BLACK
        else -> null
    }

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

    fun getLabel(shulkerMaterial: Material, customModelData: Int): ItemStack? = when (shulkerMaterial) {
        Material.SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.APPLE, 22, "cheese")
            2 -> makeItem(Material.GLASS_BOTTLE, 0, "emptyBottle")
            3 -> makeItem(Material.RABBIT_FOOT, 56, "lightPetal")
            4 -> makeItem(Material.CARROT, 5, "oil")
            else -> null
        }
        Material.ORANGE_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.RABBIT_FOOT, 46, "pearlRaw")
            2 -> makeItem(Material.RABBIT_FOOT, 47, "pearlFine")
            3 -> makeItem(Material.RABBIT_FOOT, 48, "pearlExquisite")
            4 -> makeItem(Material.APPLE, 19, "worms")
            5 -> makeItem(Material.RABBIT_FOOT, 16, "shell")
            else -> null
        }
        Material.LIGHT_BLUE_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.RABBIT_FOOT, 51, "arcanePetal")
            2 -> makeItem(Material.RABBIT_FOOT, 61, "spellThread")
            3 -> makeItem(Material.CARROT, 7, "sprinkles")
            else -> null
        }
        Material.YELLOW_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.APPLE, 28, "honey")
            2 -> makeItem(Material.RABBIT_FOOT, 12, "goo")
            else -> null
        }
        Material.LIME_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.APPLE, 20, "grapes")
            2 -> makeItem(Material.RABBIT_FOOT, 57, "seeds")
            3 -> makeItem(Material.RABBIT_FOOT, 53, "earthPetal")
            4 -> makeItem(Material.CARROT, 5, "wine")
            else -> null
        }
        Material.PINK_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.CARROT, 3, "goatMilk")
            2 -> makeItem(Material.CARROT, 6, "soap")
            else -> null
        }
        Material.LIGHT_GRAY_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.RABBIT_FOOT, 18, "stone")
            2 -> makeItem(Material.RABBIT_FOOT, 14, "ore")
            3 -> makeItem(Material.RABBIT_FOOT, 11, "goldOre")
            4 -> makeItem(Material.RABBIT_FOOT, 55, "icePetal")
            else -> null
        }
        Material.BROWN_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.RABBIT_FOOT, 13, "muk")
            2 -> makeItem(Material.ROTTEN_FLESH, 1, "mukPie")
            else -> null
        }
        Material.RED_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.RABBIT_FOOT, 54, "firepetal")
            else -> null
        }
        Material.BLACK_SHULKER_BOX -> when (customModelData) {
            1 -> makeItem(Material.APPLE, 3, "devilishApple")
            2 -> makeItem(Material.RABBIT_FOOT, 58, "ashes")
            3 -> makeItem(Material.BONE, 0, "bone")
            4 -> makeItem(Material.RABBIT_FOOT, 52, "darkPetal")
            else -> null
        }
        else -> null
    }

    fun makeItem(material: Material, customModelData: Int, name: String): ItemStack {
        val item = makeItem(material, customModelData)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
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
