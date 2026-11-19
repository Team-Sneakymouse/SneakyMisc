package com.danidipp.sneakymisc

import com.danidipp.sneakymisc.closeinventory.CloseInventoryModule
import com.danidipp.sneakymisc.attributes.AttributesModule
import com.danidipp.sneakymisc.crates.CratesModule
import com.danidipp.sneakymisc.elevators.ElevatorsModule
import com.danidipp.sneakymisc.forceloadregion.ForceLoadRegionModule
import com.danidipp.sneakymisc.leaderboards.LeaderboardsModule
import com.danidipp.sneakymisc.lomarchive.LomArchiveModule
import com.danidipp.sneakymisc.magicspells.MagicSpellsModule
import com.danidipp.sneakymisc.nophysics.NoPhysicsModule
import com.danidipp.sneakymisc.paintings.PaintingsModule
import com.danidipp.sneakymisc.phonebook.PhonebookModule
import com.danidipp.sneakymisc.pluginversionhistory.PluginVersionHistoryModule
import com.danidipp.sneakymisc.clientupdatereminder.ClientUpdateReminderModule
import com.danidipp.sneakymisc.ptimereset.PTimeResetModule
import com.danidipp.sneakymisc.deathsound.DeathSoundModule
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class SneakyMisc : JavaPlugin() {
    private val modules = mutableListOf<SneakyModule>()

    override fun onLoad() {
        instance = this
    }
    override fun onEnable() {
        registerModule(AttributesModule(logger))
        registerModule(CratesModule(this))
        if (dependenciesAvailable(ClientUpdateReminderModule.deps)) registerModule(ClientUpdateReminderModule())
        if (dependenciesAvailable(PTimeResetModule.deps))       registerModule(PTimeResetModule())
        if (dependenciesAvailable(ElevatorsModule.deps))        registerModule(ElevatorsModule(logger))
        if (dependenciesAvailable(CloseInventoryModule.deps))   registerModule(CloseInventoryModule())
        if (dependenciesAvailable(MagicSpellsModule.deps))       registerModule(MagicSpellsModule(this))
        if (dependenciesAvailable(ForceLoadRegionModule.deps))  registerModule(ForceLoadRegionModule())
        if (dependenciesAvailable(LeaderboardsModule.deps))     registerModule(LeaderboardsModule(this))
        registerModule(PaintingsModule())
        registerModule(DeathSoundModule())
        // SneakyDB is optional; without it the mechanic logs one line at startup and records nothing.
        registerModule(PluginVersionHistoryModule(this))
        if (dependenciesAvailable(LomArchiveModule.deps))
            if (Bukkit.getWorld("lom_archive") != null)  registerModule(LomArchiveModule(logger, "lom_archive"))
        if (dependenciesAvailable(PhonebookModule.deps))        registerModule(PhonebookModule(this))
//        if (dependenciesAvailable(NoPhysicsModule.deps))        registerModule(NoPhysicsModule())
    }
    private fun dependenciesAvailable(dependencies: List<String>) = dependencies.all { Bukkit.getPluginManager().isPluginEnabled(it) }
    private fun registerModule(module: SneakyModule) {
        modules.add(module)
        logger.info("Registering module ${module.javaClass.name} with ${module.commands.size} commands and ${module.listeners.size} listeners")

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            module.registerBrigadierCommands(event.registrar())
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
