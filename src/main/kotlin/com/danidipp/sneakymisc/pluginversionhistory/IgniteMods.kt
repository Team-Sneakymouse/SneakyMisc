package com.danidipp.sneakymisc.pluginversionhistory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Ignite mods loaded by this JVM, as `mod` rows. Servers not running under Ignite have none.
 *
 * Ignite is a launcher, not a plugin: it publishes no plugin we can depend on, and its classes live on the launcher's
 * classpath, which a plugin classloader may or may not see. So this uses reflection against Ignite 1.2.1's public
 * API (`space.vectrix.ignite.Ignite.mods().containers()` → `ModContainer.id()`, `version()`, `resource().locator()`,
 * `resource().path()`), tried through each given classloader. If Ignite is present but that API cannot be used,
 * it falls back to reading `ignite.mod.json` (`id`, `version`) from each JAR in the mods directory.
 */
object IgniteMods {
    private const val IGNITE = "space.vectrix.ignite.Ignite"
    private const val MODS = "space.vectrix.ignite.mod.Mods"
    private const val CONTAINER = "space.vectrix.ignite.mod.ModContainer"
    private const val RESOURCE = "space.vectrix.ignite.mod.ModResource"

    /** Ignite's locator for JARs found in the mods directory; the launcher and game have containers too. */
    private const val MODS_DIRECTORY_LOCATOR = "java_locator"

    private val json = Json { ignoreUnknownKeys = true }

    /** Ignite's own default for `-Dignite.mods`, resolved against the working directory. */
    fun modsDirectory(): Path = Path.of(System.getProperty("ignite.mods", "./mods"))

    fun rows(loaders: List<ClassLoader?>, modsDirectory: Path): List<SnapshotRow> {
        val igniteClasses = loaders.mapNotNull { loader -> runCatching { Class.forName(IGNITE, true, loader) }.getOrNull() }.distinct()
        if (igniteClasses.isEmpty()) return emptyList()
        for (ignite in igniteClasses) {
            runCatching { fromRuntime(ignite) }.getOrNull()?.let { return it }
        }
        return fromModsDirectory(modsDirectory)
    }

    private fun fromRuntime(ignite: Class<*>): List<SnapshotRow> {
        val loader = ignite.classLoader
        val mods = Class.forName(MODS, false, loader)
        val container = Class.forName(CONTAINER, false, loader)
        val resource = Class.forName(RESOURCE, false, loader)
        val id = container.getMethod("id")
        val version = container.getMethod("version")
        val containerResource = container.getMethod("resource")
        val locator = resource.getMethod("locator")
        val path = resource.getMethod("path")

        val containers = mods.getMethod("containers").invoke(ignite.getMethod("mods").invoke(null)) as Collection<*>
        return containers.mapNotNull { mod ->
            val modResource = containerResource.invoke(mod)
            if (locator.invoke(modResource) != MODS_DIRECTORY_LOCATOR) return@mapNotNull null
            val jar = (path.invoke(modResource) as Path?)?.fileName?.toString()
            SnapshotRow.mod(id.invoke(mod) as String, version.invoke(mod) as String?, jar)
        }.sortedBy { it.name }
    }

    fun fromModsDirectory(modsDirectory: Path): List<SnapshotRow> =
        SnapshotFiles.jarFileNames(modsDirectory).sorted().mapNotNull { jar ->
            runCatching { readModJson(modsDirectory.resolve(jar)) }.getOrNull()?.let { (id, version) ->
                SnapshotRow.mod(id, version, jar)
            }
        }

    private fun readModJson(jar: Path): Pair<String, String?>? = JarFile(jar.toFile()).use { file ->
        val entry = file.getJarEntry("ignite.mod.json") ?: return null
        val metadata = file.getInputStream(entry).use { json.parseToJsonElement(it.readBytes().decodeToString()) } as JsonObject
        val id = metadata["id"]?.jsonPrimitive?.content ?: return null
        id to metadata["version"]?.jsonPrimitive?.content
    }
}
