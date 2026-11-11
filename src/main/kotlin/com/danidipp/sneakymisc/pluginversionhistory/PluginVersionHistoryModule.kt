package com.danidipp.sneakymisc.pluginversionhistory

import com.danidipp.sneakymisc.SneakyModule
import com.sneakyutils.util.ServerUtil
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.plugin.Plugin
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor
import java.util.logging.Logger

/**
 * Paper side of the plugin version history mechanic: once every plugin is enabled at startup, records a snapshot of
 * the loaded plugins, Ignite mods, failed JARs and unapplied staged updates. SneakyDB is optional. See
 * `docs/plugin-version-history.md`.
 */
class PluginVersionHistoryModule(private val plugin: Plugin) : SneakyModule() {
    private val recorder = PluginVersionRecorder(
        julLog(plugin.logger),
        Executor { task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task) },
    )

    override val listeners: List<Listener> = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onServerLoad(event: ServerLoadEvent) {
            if (event.type == ServerLoadEvent.LoadType.STARTUP) record()
        }
    })

    private fun record() {
        // Bukkit state is read here on the main thread; directory scans run in collect, off it.
        val startedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val platformVersion = Bukkit.getVersion()
        val minecraftVersion = Bukkit.getMinecraftVersion()
        val plugins = Bukkit.getPluginManager().plugins.map { loaded ->
            SnapshotRow.plugin(loaded.name, loaded.pluginMeta.version, SnapshotFiles.jarFileName(loaded.javaClass), loaded.isEnabled)
        }
        val pluginsDirectory = Bukkit.getPluginsFolder().toPath()
        val updateDirectory = Bukkit.getUpdateFolderFile().toPath()
        val loaders = listOf(javaClass.classLoader, Bukkit.getServer().javaClass.classLoader, Thread.currentThread().contextClassLoader, ClassLoader.getSystemClassLoader())

        recorder.record(ServerIdentity.of(ServerUtil.getServerId(), ServerUtil.getServerName()), ::sneakyDB) { server ->
            PluginVersionSnapshot(
                server, startedAt, "paper", platformVersion, minecraftVersion,
                plugins +
                    IgniteMods.rows(loaders, IgniteMods.modsDirectory()) +
                    SnapshotRows.failedJars(SnapshotFiles.jarFileNames(pluginsDirectory), plugins.map { it.jar }) +
                    SnapshotRows.unapplied(updateDirectory, pluginsDirectory),
            )
        }
    }

    /** Checks the plugin before touching `com.sneakydb` classes, which are not loadable without it. */
    private fun sneakyDB(): SnapshotDatabase? {
        val sneakyDB = Bukkit.getPluginManager().getPlugin(SNEAKYDB) ?: return null
        check(sneakyDB.isEnabled) { "SneakyDB is installed but not enabled" }
        return SneakyDBSnapshotDatabase.connect()
    }

    companion object {
        private const val SNEAKYDB = "SneakyDB"

        fun julLog(logger: Logger) = object : SnapshotLog {
            override fun info(message: String) = logger.info(message)
            override fun warning(message: String) = logger.warning(message)
            override fun error(message: String) = logger.severe(message)
        }
    }
}
