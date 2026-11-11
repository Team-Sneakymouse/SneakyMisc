package com.danidipp.sneakymisc.pluginversionhistory

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * One JAR is both a Paper and a Velocity plugin. Each entry point, and the platform-free core it uses, must link
 * without the other platform's API and without SneakyDB, which is optional on both.
 */
class PlatformIsolationTest {
    private val core = listOf(
        "pluginversionhistory.PluginVersionRecorder",
        "pluginversionhistory.PluginVersionHistorySql",
        "pluginversionhistory.PluginVersionSnapshot",
        "pluginversionhistory.SnapshotRows",
        "pluginversionhistory.SnapshotFiles",
        "pluginversionhistory.IgniteMods",
        "pluginversionhistory.SnapshotDatabase",
    )

    @Test
    fun `the core links without Paper, Velocity or SneakyDB`() {
        check(listOf("org.bukkit.", "io.papermc.", "com.velocitypowered.", "com.sneakydb."), core)
    }

    @Test
    fun `the Velocity entry point links without Paper or SneakyDB`() {
        check(listOf("org.bukkit.", "io.papermc.", "com.sneakydb."), core + "velocity.SneakyMiscVelocity")
    }

    @Test
    fun `the Paper mechanic links without Velocity or SneakyDB`() {
        check(listOf("com.velocitypowered.", "com.sneakydb."), core + "pluginversionhistory.PluginVersionHistoryModule")
    }

    @Test
    fun `the check notices a reference to an absent platform`() {
        assertFailsWith<LinkageError> { check(listOf("org.bukkit."), listOf("pluginversionhistory.PluginVersionHistoryModule")) }
    }

    /**
     * Linking only resolves the types in signatures. A call inside a method body resolves when it first runs, so
     * this reads the compiled classes instead: every class a class can reach is named in its constant pool.
     */
    @Test
    fun `no compiled class names a platform it must run without`() {
        val classes = Path.of(PluginVersionRecorder::class.java.protectionDomain.codeSource.location.toURI())
        val paper = listOf("org/bukkit/", "io/papermc/")
        val velocity = listOf("com/velocitypowered/")
        val sneakyDB = listOf("com/sneakydb/")
        val violations = Files.walk(classes.resolve("com/danidipp/sneakymisc")).use { files ->
            files.filter { it.toString().endsWith(".class") }.toList()
        }.flatMap { file ->
            val name = classes.relativize(file).toString().replace('\\', '/').removePrefix("com/danidipp/sneakymisc/")
            val forbidden = when {
                name.startsWith("velocity/") -> paper + sneakyDB
                !name.startsWith("pluginversionhistory/") -> emptyList() // The Paper mechanics, never loaded on a proxy.
                name.startsWith("pluginversionhistory/PluginVersionHistoryModule") -> velocity + sneakyDB
                name.startsWith("pluginversionhistory/SneakyDBSnapshotDatabase") -> paper + velocity
                else -> paper + velocity + sneakyDB
            }
            val bytecode = String(Files.readAllBytes(file), Charsets.ISO_8859_1)
            forbidden.filter(bytecode::contains).map { "$name names $it" }
        }

        assertEquals(emptyList(), violations)
    }

    private fun check(forbidden: List<String>, types: List<String>) {
        val location = PluginVersionRecorder::class.java.protectionDomain.codeSource.location
        object : URLClassLoader(arrayOf(location), javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (forbidden.any(name::startsWith)) throw ClassNotFoundException("Absent here: $name")
                if (!name.startsWith("com.danidipp.sneakymisc.")) return super.loadClass(name, resolve)
                synchronized(getClassLoadingLock(name)) {
                    val loaded = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(loaded)
                    return loaded
                }
            }
        }.use { loader ->
            for (type in types) {
                val loaded = Class.forName("com.danidipp.sneakymisc.$type", true, loader)
                loaded.declaredConstructors
                loaded.declaredMethods
                loaded.declaredFields
            }
        }
    }
}
