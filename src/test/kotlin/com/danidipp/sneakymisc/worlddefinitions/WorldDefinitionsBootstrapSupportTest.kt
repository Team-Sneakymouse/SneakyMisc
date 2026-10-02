package com.danidipp.sneakymisc.worlddefinitions

import com.google.gson.JsonParser
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldDefinitionsBootstrapSupportTest {
    @Test
    fun `example config generates matching registry definitions`() {
        val stream = javaClass.getResourceAsStream("/worlds.example.yml")!!
        val yaml = YamlConfiguration.loadConfiguration(stream.bufferedReader())
        val entries = WorldDefinitionsBootstrapSupport.buildEntries(yaml)

        assertEquals(3, entries.keys.count { it.contains("/dimension_type/") })
        assertEquals(3, entries.keys.count { it.contains("/worldgen/biome/") })
        assertEquals(1, entries.keys.count { it.contains("/worldgen/noise_settings/") })
        assertEquals(0, entries.keys.count { it.contains("/dimension/") })

        val peak = JsonParser.parseString(entries.getValue("data/lom/dimension_type/peak.json")).asJsonObject
        assertEquals(-2032, peak.get("min_y").asInt)
        assertEquals(4064, peak.get("height").asInt)

        val plots = JsonParser.parseString(entries.getValue("data/plots/worldgen/biome/plots.json")).asJsonObject
        assertEquals("#b10a00", plots.getAsJsonObject("attributes").get("minecraft:visual/sky_color").asString)

        val adventure = JsonParser.parseString(entries.getValue("data/plots/worldgen/biome/adventure_biome.json")).asJsonObject
        assertEquals(5, adventure.getAsJsonArray("features")[0].asJsonArray.size())

        listOf("data/plots/dimension_type/plots.json", "data/lom/dimension_type/rebeii.json", "data/lom/dimension_type/peak.json").forEach { path ->
            val dimension = JsonParser.parseString(entries.getValue(path)).asJsonObject
            assertTrue(!dimension.has("attributes"))
        }

        val noise = JsonParser.parseString(entries.getValue("data/plots/worldgen/noise_settings/plots_generator.json")).asJsonObject
        assertEquals(-64, noise.getAsJsonObject("noise").get("min_y").asInt)
        assertEquals(384, noise.getAsJsonObject("noise").get("height").asInt)
        assertTrue(noise.getAsJsonObject("surface_rule").toString().contains("minecraft:white_glazed_terracotta"))
        assertEquals("35", entries.getValue("data/plots/worldgen/density_function/plots_generator/surface_level.json"))
    }

    @Test
    fun `new world references the selected dimension type and generator`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            dimension-types:
              'lom:peak':
                build-min-y: -2032
                build-height: 4064
                logical-height: 4064
            biomes:
              'lom:peak_base':
                temperature: 0.69
                downfall: 0.69
                precipitation: true
                water-color: '#3f76e4'
            worlds:
              'sneakymisc:test':
                dimension-type: 'lom:peak'
                generator:
                  type: flat
                  biome: 'lom:peak_base'
                  layers:
                    - block: 'minecraft:bedrock'
                      height: 1
            """.trimIndent().reader()
        )
        val entries = WorldDefinitionsBootstrapSupport.buildEntries(yaml)
        val world = JsonParser.parseString(entries.getValue("data/sneakymisc/dimension/test.json")).asJsonObject

        assertEquals("lom:peak", world.get("type").asString)
        assertEquals("minecraft:flat", world.getAsJsonObject("generator").get("type").asString)
        assertEquals("lom:peak_base", world.getAsJsonObject("generator").getAsJsonObject("settings").get("biome").asString)
    }

    @Test
    fun `plots preset can be copied with independent ids and terrain levels`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            biomes:
              'sneakymisc:high_plots_biome':
                temperature: 0.69
                downfall: 0.69
                precipitation: true
                water-color: '#3f76e4'
            plots-generators:
              'sneakymisc:high_plots':
                biome: 'sneakymisc:high_plots_biome'
                terrain-y: 80
                noise-min-y: 0
                noise-height: 512
                surface: corruption
            """.trimIndent().reader()
        )
        val entries = WorldDefinitionsBootstrapSupport.buildEntries(yaml)
        val noise = JsonParser.parseString(entries.getValue("data/sneakymisc/worldgen/noise_settings/high_plots.json")).asJsonObject
        val density = noise.getAsJsonObject("noise_router").getAsJsonObject("final_density").toString()
        val surface = noise.getAsJsonObject("surface_rule").toString()
        val sloped = entries.getValue("data/sneakymisc/worldgen/density_function/high_plots/sloped_cheese.json")

        assertTrue(density.contains("sneakymisc:high_plots/sloped_cheese"))
        assertTrue(surface.contains("sneakymisc:high_plots_biome"))
        assertTrue(surface.contains("minecraft:white_glazed_terracotta"))
        assertTrue(sloped.contains("sneakymisc:high_plots/surface_level"))
        assertEquals("80", entries.getValue("data/sneakymisc/worldgen/density_function/high_plots/surface_level.json"))
    }

    @Test
    fun `default grass surface removes the glazed terracotta branch`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            biomes:
              'plots:adventure_biome':
                temperature: 0.69
                downfall: 0.69
                precipitation: true
                water-color: '#3f76e4'
            plots-generators:
              'plots:adventure_generator':
                biome: 'plots:adventure_biome'
                terrain-y: 64
                noise-min-y: -64
                noise-height: 384
            """.trimIndent().reader()
        )
        val entries = WorldDefinitionsBootstrapSupport.buildEntries(yaml)
        val surface = JsonParser.parseString(entries.getValue("data/plots/worldgen/noise_settings/adventure_generator.json"))
            .asJsonObject.getAsJsonObject("surface_rule")
        val topLayer = surface.getAsJsonArray("sequence")[0].asJsonObject
            .getAsJsonObject("then_run").getAsJsonArray("sequence")

        assertEquals(1, topLayer.size())
        assertEquals("minecraft:grass_block", topLayer[0].asJsonObject.getAsJsonObject("result_state").get("Name").asString)
        assertTrue(surface.toString().contains("minecraft:dirt"))
        assertTrue(!surface.toString().contains("glazed_terracotta"))
    }

    @Test
    fun `invalid height range fails before a pack is written`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            dimension-types:
              'lom:bad':
                build-min-y: -127
                build-height: 384
                logical-height: 384
            """.trimIndent().reader()
        )
        val error = assertFailsWith<IllegalArgumentException> {
            WorldDefinitionsBootstrapSupport.buildEntries(yaml)
        }
        assertTrue(error.message!!.contains("height range"))
    }

    @Test
    fun `unknown config keys fail with their section path`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            biomes:
              'test:biome':
                sky-colour: '#ffffff'
            """.trimIndent().reader()
        )
        val error = assertFailsWith<IllegalArgumentException> { WorldDefinitionsBootstrapSupport.buildEntries(yaml) }
        assertTrue(error.message!!.contains("biomes.test:biome: unknown key sky-colour"))
    }

    @Test
    fun `missing custom references fail during config validation`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            worlds:
              'test:world':
                dimension-type: 'test:missing'
                generator:
                  type: flat
                  biome: 'minecraft:plains'
                  layers: []
            """.trimIndent().reader()
        )
        val error = assertFailsWith<IllegalArgumentException> { WorldDefinitionsBootstrapSupport.buildEntries(yaml) }
        assertTrue(error.message!!.contains("dimension-type references missing dimension type test:missing"))
    }

    @Test
    fun `flat layers reject entries that would be silently skipped`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            worlds:
              'test:world':
                dimension-type: 'minecraft:overworld'
                generator:
                  type: flat
                  biome: 'minecraft:plains'
                  layers:
                    - 'minecraft:stone'
            """.trimIndent().reader()
        )
        val error = assertFailsWith<IllegalArgumentException> { WorldDefinitionsBootstrapSupport.buildEntries(yaml) }
        assertTrue(error.message!!.contains("each flat layer must be a map"))
    }

    @Test
    fun `noise world biome must match its surface rule biome`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            plots-generators:
              'test:noise':
                biome: 'minecraft:plains'
                terrain-y: 35
                noise-min-y: -64
                noise-height: 384
            worlds:
              'test:world':
                dimension-type: 'minecraft:overworld'
                generator:
                  type: noise
                  settings: 'test:noise'
                  biome: 'minecraft:desert'
            """.trimIndent().reader()
        )
        val error = assertFailsWith<IllegalArgumentException> { WorldDefinitionsBootstrapSupport.buildEntries(yaml) }
        assertTrue(error.message!!.contains("biome must match"))
    }

    @Test
    fun `dimension fallback colors are opt in`() {
        val yaml = YamlConfiguration.loadConfiguration(
            """
            dimension-types:
              'test:dim':
                build-min-y: 0
                build-height: 384
                logical-height: 384
                fallback-sky-color: '#123456'
            """.trimIndent().reader()
        )
        val entries = WorldDefinitionsBootstrapSupport.buildEntries(yaml)
        val attributes = JsonParser.parseString(entries.getValue("data/test/dimension_type/dim.json"))
            .asJsonObject.getAsJsonObject("attributes")
        assertEquals("#123456", attributes.get("minecraft:visual/sky_color").asString)
        assertTrue(!attributes.has("minecraft:visual/fog_color"))
    }
}
