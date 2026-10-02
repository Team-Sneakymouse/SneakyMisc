package com.danidipp.sneakymisc.worlddefinitions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a startup datapack from the small set of world settings exposed in worlds.yml. */
public final class WorldDefinitionsBootstrapSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ID = Pattern.compile("[a-z0-9._-]+:[a-z0-9._/-]+");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final String PACK_NAME = "generated-worlds.zip";

    private WorldDefinitionsBootstrapSupport() {}

    public static void bootstrap(BootstrapContext context) {
        Path configPath = context.getDataDirectory().resolve("worlds.yml");
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            if (!Files.exists(configPath)) {
                if (Files.exists(context.getDataDirectory().resolve(PACK_NAME))) {
                    throw new IllegalStateException("worlds.yml is missing, but a generated world pack exists. Restore worlds.yml before starting this server.");
                }
                Path examplePath = context.getDataDirectory().resolve("worlds.example.yml");
                if (!Files.exists(examplePath)) {
                    try (InputStream sample = WorldDefinitionsBootstrapSupport.class.getResourceAsStream("/worlds.example.yml")) {
                        Files.createDirectories(examplePath.getParent());
                        if (sample != null) Files.copy(sample, examplePath);
                    } catch (IOException exception) {
                        context.getLogger().warn("Could not write {}: {}", examplePath, exception.getMessage());
                    }
                }
                context.getLogger().info("No {} found; custom world definitions are disabled", configPath);
                return;
            }
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(configPath.toFile());
                Map<String, String> entries = buildEntries(yaml);
                if (entries.isEmpty()) {
                    throw new IllegalArgumentException("worlds.yml contains no definitions; remove it only if no configured worlds depend on it");
                }
                Path packPath = context.getDataDirectory().resolve(PACK_NAME);
                writePack(packPath, entries);
                if (event.registrar().discoverPack(packPath, "worlds", options -> options.autoEnableOnServerStart(true)) == null) {
                    throw new IOException("Paper did not discover " + packPath);
                }
                context.getLogger().info("Registered {} generated world definitions from {}", entries.size(), configPath);
            } catch (Exception exception) {
                throw new IllegalStateException("Invalid worlds.yml: " + exception.getMessage(), exception);
            }
        });
    }

    /** Package-visible so config-to-datapack behavior can be checked without a running server. */
    static Map<String, String> buildEntries(YamlConfiguration yaml) throws IOException {
        checkKeys(yaml, "dimension-types", "biomes", "plots-generators", "worlds");
        ConfigurationSection dimensionTypes = section(yaml, "dimension-types");
        ConfigurationSection biomes = section(yaml, "biomes");
        ConfigurationSection plotsGenerators = section(yaml, "plots-generators");
        ConfigurationSection worlds = section(yaml, "worlds");
        Map<String, String> entries = new LinkedHashMap<>();
        addDimensionTypes(dimensionTypes, entries);
        addBiomes(biomes, entries);
        addPlotsGenerators(plotsGenerators, entries);
        addWorlds(worlds, entries);
        checkReferences(dimensionTypes, biomes, plotsGenerators, worlds);
        return entries;
    }

    private static void addDimensionTypes(ConfigurationSection root, Map<String, String> entries) throws IOException {
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = entry(root, id);
            checkKeys(section, "build-min-y", "build-height", "logical-height", "fallback-sky-color", "fallback-fog-color", "ambient-light-color");
            JsonObject json = template("dimension-types/overworld.json").getAsJsonObject();
            int minY = requiredInt(section, "build-min-y");
            int height = requiredInt(section, "build-height");
            int logicalHeight = requiredInt(section, "logical-height");
            if (minY % 16 != 0 || height % 16 != 0 || height < 16 || height > 4064
                || minY < -2032 || (long) minY + height > 2032 || logicalHeight < 1 || logicalHeight > height) {
                throw invalid(section, "invalid dimension height range or logical-height");
            }
            json.addProperty("min_y", minY);
            json.addProperty("height", height);
            json.addProperty("logical_height", logicalHeight);
            JsonObject attributes = new JsonObject();
            setColor(section, attributes, "fallback-sky-color", "minecraft:visual/sky_color");
            setColor(section, attributes, "fallback-fog-color", "minecraft:visual/fog_color");
            setColor(section, attributes, "ambient-light-color", "minecraft:visual/ambient_light_color");
            if (!attributes.isEmpty()) json.add("attributes", attributes);
            put(entries, "data/" + idPath(id, "dimension_type") + ".json", json);
        }
    }

    private static void addBiomes(ConfigurationSection root, Map<String, String> entries) throws IOException {
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = entry(root, id);
            checkKeys(section, "sky-color", "fog-color", "water-color", "water-fog-color", "temperature", "downfall", "precipitation", "vegetation-features");
            JsonObject json = template("biomes/empty.json").getAsJsonObject();
            JsonObject attributes = new JsonObject();
            setColor(section, attributes, "sky-color", "minecraft:visual/sky_color");
            setColor(section, attributes, "fog-color", "minecraft:visual/fog_color");
            setColor(section, attributes, "water-fog-color", "minecraft:visual/water_fog_color");
            if (!attributes.isEmpty()) json.add("attributes", attributes);
            setColor(section, json.getAsJsonObject("effects"), "water-color", "water_color");
            if (!section.contains("water-color")) throw invalid(section, "water-color is required by Minecraft");
            json.addProperty("temperature", requiredDouble(section, "temperature"));
            json.addProperty("downfall", requiredDouble(section, "downfall"));
            json.addProperty("has_precipitation", requiredBoolean(section, "precipitation"));
            if (section.contains("vegetation-features")) {
                if (!section.isList("vegetation-features")) throw invalid(section, "vegetation-features must be a list");
                JsonArray vegetation = new JsonArray();
                for (Object value : section.getList("vegetation-features")) {
                    if (!(value instanceof String feature)) throw invalid(section, "vegetation-features must contain only namespaced IDs");
                    splitId(feature);
                    vegetation.add(feature);
                }
                JsonArray features = new JsonArray();
                if (!vegetation.isEmpty()) features.add(vegetation);
                json.add("features", features);
            }
            put(entries, "data/" + idPath(id, "worldgen/biome") + ".json", json);
        }
    }

    private static void addPlotsGenerators(ConfigurationSection root, Map<String, String> entries) throws IOException {
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = entry(root, id);
            checkKeys(section, "biome", "terrain-y", "noise-min-y", "noise-height", "surface");
            String biome = requiredId(section, "biome");
            String surface = section.contains("surface") ? requiredString(section, "surface") : "grass";
            if (!surface.equals("grass") && !surface.equals("corruption")) {
                throw invalid(section, "surface must be grass or corruption");
            }
            int terrainY = requiredInt(section, "terrain-y");
            int minY = requiredInt(section, "noise-min-y");
            int height = requiredInt(section, "noise-height");
            if (minY % 16 != 0 || height % 16 != 0 || height < 16 || height > 4064
                || minY < -2032 || (long) minY + height > 2032 || terrainY < minY || terrainY >= minY + height) {
                throw invalid(section, "invalid generator range or terrain-y outside it");
            }
            String[] parts = splitId(id);
            String functionRoot = "data/" + parts[0] + "/worldgen/density_function/" + parts[1] + "/";
            String functionId = id;
            JsonObject noise = template("plots/noise_settings.json").getAsJsonObject();
            JsonObject noiseRange = noise.getAsJsonObject("noise");
            noiseRange.addProperty("min_y", minY);
            noiseRange.addProperty("height", height);
            JsonObject router = noise.getAsJsonObject("noise_router");
            JsonObject preliminary = router.getAsJsonObject("preliminary_surface_level");
            preliminary.addProperty("lower_bound", minY);
            preliminary.addProperty("upper_bound", minY + height);
            if (surface.equals("grass")) {
                JsonArray topLayer = noise.getAsJsonObject("surface_rule").getAsJsonArray("sequence")
                    .get(0).getAsJsonObject().getAsJsonObject("then_run").getAsJsonArray("sequence");
                topLayer.remove(0);
            }
            replaceStrings(noise, "plots:plots/sloped_cheese", functionId + "/sloped_cheese");
            replaceStrings(noise, "plots:plots", biome);
            put(entries, "data/" + idPath(id, "worldgen/noise_settings") + ".json", noise);

            JsonObject sloped = template("plots/sloped_cheese.json").getAsJsonObject();
            JsonObject gradient = sloped.getAsJsonObject("argument1").getAsJsonObject("argument1");
            gradient.addProperty("from_y", minY);
            gradient.addProperty("to_y", minY + height);
            replaceStrings(sloped, "plots:plots/surface_level", functionId + "/surface_level");
            replaceStrings(sloped, "plots:plots/offset", functionId + "/offset");
            // The gradient controls the shape; terrain-y is the explicit base level parameter.
            put(entries, functionRoot + "sloped_cheese.json", sloped);
            put(entries, functionRoot + "offset.json", template("plots/offset.json"));
            entries.put(functionRoot + "surface_level.json", Integer.toString(terrainY));
        }
    }

    private static void addWorlds(ConfigurationSection root, Map<String, String> entries) {
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = entry(root, id);
            checkKeys(section, "dimension-type", "generator");
            if (id.startsWith("minecraft:")) {
                throw invalid(section, "new worlds need a custom namespace, not minecraft");
            }
            JsonObject world = new JsonObject();
            world.addProperty("type", requiredId(section, "dimension-type"));
            ConfigurationSection generator = section.getConfigurationSection("generator");
            if (generator == null) throw invalid(section, "generator section is required");
            JsonObject generatorJson = new JsonObject();
            String type = requiredString(generator, "type");
            if (type.equals("flat")) {
                checkKeys(generator, "type", "biome", "features", "lakes", "layers");
                generatorJson.addProperty("type", "minecraft:flat");
                JsonObject settings = new JsonObject();
                settings.addProperty("biome", requiredId(generator, "biome"));
                settings.addProperty("features", generator.contains("features") && requiredBoolean(generator, "features"));
                settings.addProperty("lakes", generator.contains("lakes") && requiredBoolean(generator, "lakes"));
                if (!generator.isList("layers")) throw invalid(generator, "layers must be a list, use [] for a void world");
                JsonArray layers = new JsonArray();
                for (Object item : generator.getList("layers")) {
                    if (!(item instanceof Map<?, ?> layer)) throw invalid(generator, "each flat layer must be a map with block and height");
                    if (!layer.keySet().equals(Set.of("block", "height"))) throw invalid(generator, "each flat layer needs only block and height");
                    Object block = layer.get("block");
                    Object count = layer.get("height");
                    if (!(block instanceof String blockId) || !validId(blockId)
                        || !(count instanceof Number number) || number.intValue() < 0 || number.intValue() > 4064
                        || number.doubleValue() != number.intValue()) {
                        throw invalid(generator, "each flat layer needs a block id and nonnegative integer height");
                    }
                    JsonObject layerJson = new JsonObject();
                    layerJson.addProperty("block", blockId);
                    layerJson.addProperty("height", number.intValue());
                    layers.add(layerJson);
                }
                settings.add("layers", layers);
                generatorJson.add("settings", settings);
            } else if (type.equals("noise")) {
                checkKeys(generator, "type", "settings", "biome");
                generatorJson.addProperty("type", "minecraft:noise");
                generatorJson.addProperty("settings", requiredId(generator, "settings"));
                JsonObject source = new JsonObject();
                source.addProperty("type", "minecraft:fixed");
                source.addProperty("biome", requiredId(generator, "biome"));
                generatorJson.add("biome_source", source);
            } else {
                throw invalid(generator, "type must be flat or noise");
            }
            world.add("generator", generatorJson);
            put(entries, "data/" + idPath(id, "dimension") + ".json", world);
        }
    }

    private static void checkReferences(ConfigurationSection dimensionTypes, ConfigurationSection biomes,
                                        ConfigurationSection plotsGenerators, ConfigurationSection worlds) {
        if (plotsGenerators != null) {
            for (String id : plotsGenerators.getKeys(false)) {
                ConfigurationSection generator = entry(plotsGenerators, id);
                checkReference(generator, "biome", biomes, "biome");
            }
        }
        if (worlds != null) {
            for (String id : worlds.getKeys(false)) {
                ConfigurationSection world = entry(worlds, id);
                checkReference(world, "dimension-type", dimensionTypes, "dimension type");
                ConfigurationSection generator = section(world, "generator");
                checkReference(generator, "biome", biomes, "biome");
                if ("noise".equals(requiredString(generator, "type"))) {
                    String settings = checkReference(generator, "settings", plotsGenerators, "noise settings");
                    if (plotsGenerators != null && plotsGenerators.contains(settings)) {
                        String surfaceBiome = requiredId(entry(plotsGenerators, settings), "biome");
                        if (!surfaceBiome.equals(requiredId(generator, "biome"))) {
                            throw invalid(generator, "biome must match plots-generators." + settings + ".biome so its surface rules apply");
                        }
                    }
                }
            }
        }
    }

    private static String checkReference(ConfigurationSection source, String key, ConfigurationSection definitions, String kind) {
        String id = requiredId(source, key);
        if (!id.startsWith("minecraft:") && (definitions == null || !definitions.contains(id))) {
            throw invalid(source, key + " references missing " + kind + " " + id + "; define it in worlds.yml");
        }
        return id;
    }

    private static ConfigurationSection section(ConfigurationSection root, String key) {
        if (!root.contains(key)) return null;
        ConfigurationSection result = root.getConfigurationSection(key);
        if (result == null) throw invalid(root, key + " must be a section");
        return result;
    }

    private static void checkKeys(ConfigurationSection section, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : section.getKeys(false)) {
            if (!names.contains(key)) throw invalid(section, "unknown key " + key + "; allowed: " + names);
        }
    }

    private static void replaceStrings(JsonElement element, String from, String to) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals(from)) {
                    object.addProperty(key, to);
                } else replaceStrings(value, from, to);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement value = array.get(i);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals(from)) {
                    array.set(i, new com.google.gson.JsonPrimitive(to));
                } else replaceStrings(value, from, to);
            }
        }
    }

    private static void writePack(Path packPath, Map<String, String> entries) throws IOException {
        Files.createDirectories(packPath.getParent());
        Path temporary = Files.createTempFile(packPath.getParent(), "generated-worlds-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                writeEntry(zip, "pack.mcmeta", "{\"pack\":{\"description\":\"SneakyMisc configured worlds\",\"min_format\":[107,1],\"max_format\":[107,1]}}");
                for (Map.Entry<String, String> entry : entries.entrySet()) writeEntry(zip, entry.getKey(), entry.getValue());
            }
            try {
                Files.move(temporary, packPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, packPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String path, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static JsonElement template(String resource) throws IOException {
        try (InputStream input = WorldDefinitionsBootstrapSupport.class.getResourceAsStream("/world-templates/" + resource)) {
            if (input == null) throw new IOException("Missing world template " + resource);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection entry(ConfigurationSection root, String id) {
        splitId(id);
        ConfigurationSection section = root.getConfigurationSection(id);
        if (section == null) throw invalid(root, id + " must be a section");
        return section;
    }

    private static String requiredString(ConfigurationSection section, String key) {
        Object raw = section.get(key);
        if (!(raw instanceof String value) || value.isBlank()) throw invalid(section, key + " must be a nonempty string");
        return value.trim();
    }

    private static String requiredId(ConfigurationSection section, String key) {
        String value = requiredString(section, key);
        splitId(value);
        return value;
    }

    private static int requiredInt(ConfigurationSection section, String key) {
        if (!section.isInt(key)) throw invalid(section, key + " must be an integer");
        return section.getInt(key);
    }

    private static double requiredDouble(ConfigurationSection section, String key) {
        if (!(section.get(key) instanceof Number)) throw invalid(section, key + " must be a number");
        double value = section.getDouble(key);
        if (!Double.isFinite(value)) throw invalid(section, key + " must be finite");
        return value;
    }

    private static boolean requiredBoolean(ConfigurationSection section, String key) {
        if (!section.isBoolean(key)) throw invalid(section, key + " must be boolean");
        return section.getBoolean(key);
    }

    private static void setColor(ConfigurationSection section, JsonObject target, String key, String jsonKey) {
        if (!section.contains(key)) return;
        String color = requiredString(section, key);
        if (!COLOR.matcher(color).matches()) throw invalid(section, key + " must be a six-digit #RRGGBB color");
        target.addProperty(jsonKey, color.toLowerCase(java.util.Locale.ROOT));
    }

    private static void put(Map<String, String> entries, String path, JsonElement json) {
        if (entries.putIfAbsent(path, GSON.toJson(json)) != null) throw new IllegalArgumentException("Duplicate generated entry " + path);
    }

    private static String idPath(String id, String registry) {
        String[] parts = splitId(id);
        return parts[0] + "/" + registry + "/" + parts[1];
    }

    private static String[] splitId(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid namespaced id: " + id);
        String[] parts = id.split(":", 2);
        for (String segment : parts[1].split("/", -1)) {
            if (segment.equals(".") || segment.equals("..") || segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid namespaced id: " + id);
            }
        }
        return parts;
    }

    private static boolean validId(String id) {
        return ID.matcher(id).matches();
    }

    private static IllegalArgumentException invalid(ConfigurationSection section, String message) {
        String path = section.getCurrentPath();
        return new IllegalArgumentException((path == null || path.isEmpty() ? "worlds.yml" : path) + ": " + message);
    }
}
