package com.danidipp.sneakymisc

import com.danidipp.sneakymisc.closeinventory.CloseInventoryModule
import com.danidipp.sneakymisc.databasesync.DBSyncModule
import com.danidipp.sneakymisc.dclock.DClockModule
import com.danidipp.sneakymisc.dvzregistrations.RegistrationModule
import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.danidipp.sneakymisc.grounditems.GroundItemsModule
import com.danidipp.sneakymisc.leaderboards.LeaderboardsModule
import com.danidipp.sneakymisc.lomarchive.LomArchiveModule
import com.danidipp.sneakymisc.metaoverlayhelper.MetaOverlayHelper
import com.danidipp.sneakymisc.nophysics.NoPhysicsModule
import com.danidipp.sneakymisc.paintings.PaintingsModule
import com.danidipp.sneakymisc.phonebook.PhonebookModule
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class SneakyMisc : JavaPlugin() {
    private val modules = mutableListOf<SneakyModule>()

    override fun onLoad() {
        instance = this
    }
    override fun onEnable() {
        if (dependenciesAvailable(ElevatorsModule.deps))        registerModule(ElevatorsModule(logger))
        if (dependenciesAvailable(MetaOverlayHelper.deps))      registerModule(MetaOverlayHelper(logger))
        if (dependenciesAvailable(CloseInventoryModule.deps))   registerModule(CloseInventoryModule())
        if (dependenciesAvailable(DBSyncModule.deps))           registerModule(DBSyncModule(logger))
        if (dependenciesAvailable(RegistrationModule.deps))     registerModule(RegistrationModule(logger))
        if (dependenciesAvailable(DClockModule.deps))           registerModule(DClockModule(logger))
        if (dependenciesAvailable(LeaderboardsModule.deps))     registerModule(LeaderboardsModule(this))
        if (dependenciesAvailable(GroundItemsModule.deps))      registerModule(GroundItemsModule())
        registerModule(PaintingsModule())
        if (dependenciesAvailable(LomArchiveModule.deps))
            if (Bukkit.getWorld("lom_archive") != null)  registerModule(LomArchiveModule(logger, "lom_archive"))
//        if (dependenciesAvailable(PhonebookModule.deps))        registerModule(PhonebookModule(logger))
//        if (dependenciesAvailable(NoPhysicsModule.deps))        registerModule(NoPhysicsModule())
    }
    private fun dependenciesAvailable(dependencies: List<String>) = dependencies.all { Bukkit.getPluginManager().isPluginEnabled(it) }
    private fun registerModule(module: SneakyModule) {
        modules.add(module)
        logger.info("Registering module ${module.javaClass.name} with ${module.commands.size} commands and ${module.listeners.size} listeners")

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            for (command in module.commands) event.registrar().register(command.node, command.description)
        }

        for (listener in module.listeners) {
            Bukkit.getServer().pluginManager.registerEvents(listener, this)
        }
    }

    companion object {
        const val IDENTIFIER = "sneakymisc"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0"
        private lateinit var instance: SneakyMisc

        fun getInstance(): SneakyMisc {
            return instance
        }
    }
}
