package com.danidipp.sneakymisc.pluginversionhistory

import space.vectrix.ignite.Ignite
import space.vectrix.ignite.mod.ModContainer
import space.vectrix.ignite.mod.ModResource
import space.vectrix.ignite.mod.Mods
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `space.vectrix.ignite` classes on the test classpath are stand-ins for Ignite 1.2.1's public API. */
class IgniteModsTest {
    private val mods = Files.createTempDirectory("mods")
    private val withIgnite = listOf(IgniteModsTest::class.java.classLoader)

    @AfterTest
    fun resetIgnite() {
        Ignite.mods = null
    }

    @Test
    fun `a server not running under Ignite has no mod rows, even with a mods directory`() {
        modJar("SneakyPatches-0.2.0.jar", """{"id": "sneakypatches", "version": "0.2.0"}""")

        assertEquals(emptyList(), IgniteMods.rows(listOf(ClassLoader.getPlatformClassLoader(), null), mods))
    }

    @Test
    fun `mods come from Ignite's runtime API, excluding the launcher and game containers`() {
        Ignite.mods = mods(
            container("ignite", "1.2.1", "launcher_locator", Path.of("ignite.jar")),
            container("velocity", "3.4.0", "game_locator", Path.of("velocity.jar")),
            container("sneakypatches", "0.2.0", "java_locator", Path.of("mods", "SneakyPatches-0.2.0.jar")),
            container("another", "1", "java_locator", Path.of("mods", "Another.jar")),
        )

        assertEquals(
            listOf(SnapshotRow.mod("another", "1", "Another.jar"), SnapshotRow.mod("sneakypatches", "0.2.0", "SneakyPatches-0.2.0.jar")),
            IgniteMods.rows(listOf(ClassLoader.getPlatformClassLoader()) + withIgnite, mods),
        )
    }

    @Test
    fun `when Ignite is present but its API fails, ignite mod json is read from the mods directory`() {
        // Ignite.mods() throws "Ignite has not been initialized yet!" while unset.
        modJar("SneakyPatches-0.2.0.jar", """{"id": "sneakypatches", "version": "0.2.0", "mixins": ["a.json"]}""")
        modJar("NoVersion.jar", """{"id": "noversion"}""")
        modJar("PlainLibrary.jar", null)
        mods.resolve("Broken.jar").createFile()
        mods.resolve("readme.txt").createFile()

        assertEquals(
            listOf(SnapshotRow.mod("noversion", null, "NoVersion.jar"), SnapshotRow.mod("sneakypatches", "0.2.0", "SneakyPatches-0.2.0.jar")),
            IgniteMods.rows(withIgnite, mods),
        )
    }

    @Test
    fun `the mods directory follows ignite mods with Ignite's default`() {
        val previous = System.getProperty("ignite.mods")
        try {
            System.clearProperty("ignite.mods")
            assertEquals(Path.of("./mods"), IgniteMods.modsDirectory())
            System.setProperty("ignite.mods", "custom-mods")
            assertEquals(Path.of("custom-mods"), IgniteMods.modsDirectory())
        } finally {
            if (previous == null) System.clearProperty("ignite.mods") else System.setProperty("ignite.mods", previous)
        }
    }

    private fun modJar(name: String, modJson: String?) {
        JarOutputStream(Files.newOutputStream(mods.resolve(name))).use { jar ->
            jar.putNextEntry(JarEntry(if (modJson == null) "library.txt" else "ignite.mod.json"))
            jar.write((modJson ?: "not a mod").toByteArray())
        }
    }

    private fun mods(vararg containers: ModContainer) = object : Mods {
        override fun containers(): Collection<ModContainer> = containers.toList()
    }

    // Package-private implementations, like Ignite's own: the lookup must call through the public interfaces.
    private fun container(id: String, version: String, locator: String, path: Path): ModContainer = object : ModContainer {
        override fun id() = id
        override fun version() = version
        override fun resource() = object : ModResource {
            override fun locator() = locator
            override fun path() = path
        }
    }
}
