package com.danidipp.sneakymisc.velocity

import com.danidipp.sneakymisc.pluginversionhistory.IgniteMods
import com.danidipp.sneakymisc.pluginversionhistory.PluginVersionRecorder
import com.danidipp.sneakymisc.pluginversionhistory.PluginVersionSnapshot
import com.danidipp.sneakymisc.pluginversionhistory.ServerIdentity
import com.danidipp.sneakymisc.pluginversionhistory.SnapshotDatabase
import com.danidipp.sneakymisc.pluginversionhistory.SnapshotFiles
import com.danidipp.sneakymisc.pluginversionhistory.SnapshotLog
import com.danidipp.sneakymisc.pluginversionhistory.SnapshotRow
import com.danidipp.sneakymisc.pluginversionhistory.SnapshotRows
import com.danidipp.sneakymisc.pluginversionhistory.SneakyDBSnapshotDatabase
import com.google.inject.Inject
import com.sneakyutils.util.ServerUtil
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor

/**
 * Velocity entry point, loaded only through `velocity-plugin.json`. It references no Bukkit or Paper class, and none
 * of the Paper mechanics load on a proxy: only the plugin version history snapshot runs here.
 */
class SneakyMiscVelocity @Inject constructor(private val proxy: ProxyServer, private val logger: Logger) {

    /** Normal order: SneakyDB registers its provider at the earliest order of this same event. */
    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        val startedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val version = proxy.version
        val containers = proxy.pluginManager.plugins
        val plugins = containers.map { container ->
            val description = container.description
            SnapshotRow.plugin(description.id, description.version.orElse(null), description.source.map { it.fileName.toString() }.orElse(null), null)
        }
        val pluginsDirectory = proxy.pluginManager.fromInstance(this)
            .flatMap { it.description.source }.map { it.toAbsolutePath().parent }.orElse(Path.of("plugins"))
        val loaders = listOf(javaClass.classLoader, proxy.javaClass.classLoader, Thread.currentThread().contextClassLoader, ClassLoader.getSystemClassLoader())

        val recorder = PluginVersionRecorder(slf4jLog(logger), Executor { task -> proxy.scheduler.buildTask(this, task).schedule() })
        recorder.record(ServerIdentity.of(ServerUtil.getServerId(), ServerUtil.getServerName()), ::sneakyDB) { server ->
            PluginVersionSnapshot(
                server, startedAt, "velocity", "${version.name} ${version.version}", null,
                // No unapplied rows: Velocity has no staged-update folder.
                plugins +
                    IgniteMods.rows(loaders, IgniteMods.modsDirectory()) +
                    SnapshotRows.failedJars(SnapshotFiles.jarFileNames(pluginsDirectory), plugins.map { it.jar }),
            )
        }
    }

    /** Checks the plugin before touching `com.sneakydb` classes, which are not loadable without it. */
    private fun sneakyDB(): SnapshotDatabase? =
        if (proxy.pluginManager.isLoaded(SNEAKYDB)) SneakyDBSnapshotDatabase.connect() else null

    private companion object {
        const val SNEAKYDB = "sneakydb"

        fun slf4jLog(logger: Logger) = object : SnapshotLog {
            override fun info(message: String) = logger.info(message)
            override fun warning(message: String) = logger.warn(message)
            override fun error(message: String) = logger.error(message)
        }
    }
}
