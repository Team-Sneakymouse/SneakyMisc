package com.danidipp.sneakymisc.pluginversionhistory

import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotRowsTest {
    @Test
    fun `each row kind fills the columns its kind defines`() {
        assertEquals(
            SnapshotRow(SnapshotRowKind.PLUGIN, "SneakyMisc", "2026.11.11-abc", "SneakyMisc.jar", true),
            SnapshotRow.plugin("SneakyMisc", "2026.11.11-abc", "SneakyMisc.jar", true),
        )
        assertEquals(
            SnapshotRow(SnapshotRowKind.MOD, "sneakypatches", "0.2.0", "SneakyPatches-0.2.0.jar", null),
            SnapshotRow.mod("sneakypatches", "0.2.0", "SneakyPatches-0.2.0.jar"),
        )
        assertEquals(SnapshotRow(SnapshotRowKind.FAILED, "Broken.jar", null, "Broken.jar", null), SnapshotRow.failed("Broken.jar"))
        assertEquals(SnapshotRow(SnapshotRowKind.UNAPPLIED, "New.jar", null, "New.jar", null), SnapshotRow.unapplied("New.jar"))
        assertEquals(listOf("plugin", "mod", "failed", "unapplied"), SnapshotRowKind.entries.map { it.column })
    }

    @Test
    fun `a JAR in plugins that no loaded plugin came from is failed`() {
        val failed = SnapshotRows.failedJars(
            jarsInPluginsDir = listOf("SneakyMisc.jar", "Broken.jar", "MissingDependency.jar"),
            loadedJars = listOf("SneakyMisc.jar", null),
        )

        assertEquals(listOf(SnapshotRow.failed("Broken.jar"), SnapshotRow.failed("MissingDependency.jar")), failed)
    }

    @Test
    fun `failed JARs match by filename, not path, so a plugin loaded from a copy counts as loaded`() {
        // Older Paper versions loaded remapped Spigot plugins from plugins/.paper-remapped/ under the same filename.
        val plugins = Files.createTempDirectory("plugins")
        plugins.resolve("OldSpigot.jar").createFile()
        val remapped = plugins.resolve(".paper-remapped").createDirectory().resolve("OldSpigot.jar")
        val fixture = RemappedPluginFixture::class.java
        val entry = fixture.name.replace('.', '/') + ".class"
        JarOutputStream(Files.newOutputStream(remapped)).use { jar ->
            jar.putNextEntry(JarEntry(entry))
            fixture.classLoader.getResourceAsStream(entry)!!.use { it.transferTo(jar) }
        }
        val loadedJar = URLClassLoader(arrayOf(remapped.toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            SnapshotFiles.jarFileName(loader.loadClass(fixture.name))
        }

        assertEquals("OldSpigot.jar", loadedJar)
        assertEquals(emptyList(), SnapshotRows.failedJars(SnapshotFiles.jarFileNames(plugins), listOf(loadedJar)))
        assertEquals(listOf(SnapshotRow.failed("oldspigot.jar")), SnapshotRows.failedJars(listOf("oldspigot.jar"), listOf("OldSpigot.jar")))
    }

    @Test
    fun `everything left in the update folder is unapplied`() {
        assertEquals(
            listOf(SnapshotRow.unapplied("A.jar"), SnapshotRow.unapplied("B.jar")),
            SnapshotRows.unapplied(listOf("B.jar", "A.jar")),
        )
    }

    @Test
    fun `an update folder that is the plugins directory itself has nothing unapplied`() {
        val plugins = Files.createTempDirectory("plugins")
        plugins.resolve("Installed.jar").createFile()
        val update = plugins.resolve(".update").createDirectory()
        update.resolve("Staged.jar").createFile()

        assertEquals(listOf(SnapshotRow.unapplied("Staged.jar")), SnapshotRows.unapplied(update, plugins))
        // A blank update-folder setting resolves to the plugins directory, here spelled differently.
        assertEquals(emptyList(), SnapshotRows.unapplied(plugins.resolve(".update").resolve(".."), plugins))
    }

    @Test
    fun `duplicate kind and name keep the first row so the primary key holds`() {
        val rows = SnapshotRows.distinct(
            listOf(
                SnapshotRow.mod("same", "1", "a.jar"),
                SnapshotRow.mod("same", "2", "b.jar"),
                SnapshotRow.plugin("same", "1", "same.jar", true),
            )
        )

        assertEquals(listOf(SnapshotRow.mod("same", "1", "a.jar"), SnapshotRow.plugin("same", "1", "same.jar", true)), rows)
    }

    @Test
    fun `directory listings take only regular files directly inside`() {
        val plugins = Files.createTempDirectory("plugins")
        plugins.resolve("A.jar").createFile()
        plugins.resolve("b.JAR").createFile()
        plugins.resolve("notes.txt").createFile()
        plugins.resolve(".paper-remapped").createDirectory().resolve("Nested.jar").createFile()
        plugins.resolve("Folder.jar").createDirectory()

        assertEquals(listOf("A.jar", "b.JAR"), SnapshotFiles.jarFileNames(plugins).sorted())
        assertEquals(listOf("A.jar", "b.JAR", "notes.txt"), SnapshotFiles.regularFileNames(plugins).sorted())
        assertEquals(emptyList(), SnapshotFiles.regularFileNames(plugins.resolve("missing")))
    }

    @Test
    fun `server identity needs both id and name`() {
        assertEquals(ServerIdentity("dev-main", "Dev Main"), ServerIdentity.of("dev-main", "Dev Main"))
        assertEquals(null, ServerIdentity.of(null, "Dev Main"))
        assertEquals(null, ServerIdentity.of("dev-main", " "))
    }
}

/** Loaded from a JAR under `.paper-remapped/` by the failed-JAR test; references nothing else. */
class RemappedPluginFixture
