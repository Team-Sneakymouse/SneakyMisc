package com.danidipp.sneakymisc.pluginversionhistory

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

// Platform-free: shared by the Paper mechanic and the Velocity entry point. No Bukkit, Velocity or SneakyDB types.

/** Values of the `kind` column of `plugin_version_history`. */
enum class SnapshotRowKind(val column: String) {
    PLUGIN("plugin"),
    MOD("mod"),
    FAILED("failed"),
    UNAPPLIED("unapplied"),
}

/** One row of a plugin version snapshot, without the server and startup columns it shares with the others. */
data class SnapshotRow(
    val kind: SnapshotRowKind,
    val name: String,
    val version: String?,
    val jar: String?,
    val enabled: Boolean?,
) {
    companion object {
        /** [enabled] is null on Velocity, which has no enabled state. */
        fun plugin(name: String, version: String?, jar: String?, enabled: Boolean?) =
            SnapshotRow(SnapshotRowKind.PLUGIN, name, version, jar, enabled)

        fun mod(id: String, version: String?, jar: String?) = SnapshotRow(SnapshotRowKind.MOD, id, version, jar, null)

        fun failed(jar: String) = SnapshotRow(SnapshotRowKind.FAILED, jar, null, jar, null)

        fun unapplied(file: String) = SnapshotRow(SnapshotRowKind.UNAPPLIED, file, null, file, null)
    }
}

data class ServerIdentity(val id: String, val name: String) {
    companion object {
        /** Null when either part is missing, so callers record nothing rather than invent an identity. */
        fun of(id: String?, name: String?): ServerIdentity? =
            if (id.isNullOrBlank() || name.isNullOrBlank()) null else ServerIdentity(id, name)
    }
}

/** Everything one startup records, written as a single multi-row `INSERT`. */
data class PluginVersionSnapshot(
    val server: ServerIdentity,
    val startedAt: OffsetDateTime,
    val platform: String,
    val platformVersion: String,
    val minecraftVersion: String?,
    val rows: List<SnapshotRow>,
) {
    fun count(kind: SnapshotRowKind) = rows.count { it.kind == kind }
}

object SnapshotRows {
    /**
     * `*.jar` files directly in the plugins directory that did not become a loaded plugin. Matched by filename only,
     * never by path: a plugin loaded from a copy elsewhere (older Paper versions kept remapped Spigot plugins in
     * `plugins/.paper-remapped/` under the same filename) still counts as loaded.
     */
    fun failedJars(jarsInPluginsDir: Collection<String>, loadedJars: Collection<String?>): List<SnapshotRow> {
        val loaded = loadedJars.filterNotNull().toSet()
        return jarsInPluginsDir.filter { it !in loaded }.sorted().map(SnapshotRow::failed)
    }

    fun unapplied(filesInUpdateDir: Collection<String>): List<SnapshotRow> =
        filesInUpdateDir.sorted().map(SnapshotRow::unapplied)

    /**
     * Files left in the update directory. None when it is the plugins directory itself (a blank `update-folder`
     * setting, which turns Paper's updating off): every installed plugin would otherwise look unapplied.
     */
    fun unapplied(updateDirectory: Path, pluginsDirectory: Path): List<SnapshotRow> =
        if (updateDirectory.toAbsolutePath().normalize() == pluginsDirectory.toAbsolutePath().normalize()) emptyList()
        else unapplied(SnapshotFiles.regularFileNames(updateDirectory))

    /** Keeps the first row per primary-key part (`kind`, `name`), so one odd duplicate cannot fail the whole insert. */
    fun distinct(rows: List<SnapshotRow>): List<SnapshotRow> = rows.distinctBy { it.kind to it.name }
}

/** Directory listings for the file-based row kinds. A missing or unreadable directory lists as empty. */
object SnapshotFiles {
    fun jarFileNames(directory: Path): List<String> =
        regularFileNames(directory).filter { it.endsWith(".jar", ignoreCase = true) }

    fun regularFileNames(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) return emptyList()
        return try {
            Files.list(directory).use { files ->
                files.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.toList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    /** Filename of the JAR a class was loaded from, or null when it has no file code source. */
    fun jarFileName(type: Class<*>): String? = runCatching {
        Path.of(type.protectionDomain.codeSource.location.toURI()).fileName?.toString()
    }.getOrNull()
}
