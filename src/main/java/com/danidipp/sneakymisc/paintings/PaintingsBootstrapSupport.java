package com.danidipp.sneakymisc.paintings;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.PaintingVariantRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PaintingsBootstrapSupport {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();

    private PaintingsBootstrapSupport() {}

    public static void bootstrap(final BootstrapContext context) {
        final Path configPath = context.getDataDirectory().resolve("paintings.yml");
        final LoadResult loadResult = load(configPath, context);

        context.getLifecycleManager().registerEventHandler(RegistryEvents.PAINTING_VARIANT.freeze(), event -> {
            int registeredCount = 0;

            for (Map.Entry<NamespacedKey, Definition> entry : loadResult.definitions.entrySet()) {
                final NamespacedKey key = entry.getKey();
                final Definition definition = entry.getValue();

                try {
                    event.registry().register(RegistryKey.PAINTING_VARIANT.typedKey(key), builder -> {
                        builder.width(definition.width)
                            .height(definition.height)
                            .assetId(definition.assetId);

                        if (definition.title != null) {
                            builder.title(definition.title);
                        }
                        if (definition.author != null) {
                            builder.author(definition.author);
                        }
                    });
                    registeredCount++;
                } catch (Throwable throwable) {
                    context.getLogger().warn(
                        "Painting '{}': failed to register: {}",
                        key.asString(),
                        throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName()
                    );
                }
            }

            context.getLogger().info(
                "Paintings bootstrap finished for {}: configured={}, registered={}, skipped={}",
                configPath.toAbsolutePath().toString(),
                loadResult.definitions.size(),
                registeredCount,
                loadResult.skippedEntries + (loadResult.definitions.size() - registeredCount)
            );
        });
    }

    private static LoadResult load(final Path configPath, final BootstrapContext context) {
        if (!configPath.toFile().exists()) {
            context.getLogger().info("No paintings.yml found at {}, no custom paintings will be registered", configPath.toAbsolutePath().toString());
            return new LoadResult(new LinkedHashMap<>(), 0);
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configPath.toFile());
        final LinkedHashMap<NamespacedKey, Definition> definitions = new LinkedHashMap<>();
        int skippedEntries = 0;

        for (String entryKey : yaml.getKeys(false)) {
            final ConfigurationSection section = yaml.getConfigurationSection(entryKey);
            if (section == null) {
                skippedEntries += skip(context, entryKey, "Invalid configuration section");
                continue;
            }

            final NamespacedKey paintingKey = parseExplicitNamespacedKey(entryKey);
            if (paintingKey == null) {
                skippedEntries += skip(context, entryKey, "Invalid painting id '" + entryKey + "'");
                continue;
            }

            if (!section.isInt("width")) {
                skippedEntries += skip(context, entryKey, "Missing or invalid integer 'width'");
                continue;
            }
            if (!section.isInt("height")) {
                skippedEntries += skip(context, entryKey, "Missing or invalid integer 'height'");
                continue;
            }

            final int width = section.getInt("width");
            if (width < 1 || width > 16) {
                skippedEntries += skip(context, entryKey, "Width must be between 1 and 16, found " + width);
                continue;
            }

            final int height = section.getInt("height");
            if (height < 1 || height > 16) {
                skippedEntries += skip(context, entryKey, "Height must be between 1 and 16, found " + height);
                continue;
            }

            final String assetIdRaw = section.getString("asset-id", "").trim();
            final NamespacedKey assetId = parseExplicitNamespacedKey(assetIdRaw);
            if (assetId == null) {
                skippedEntries += skip(context, entryKey, "Missing or invalid namespaced 'asset-id'");
                continue;
            }

            final Component title = parseOptionalMiniMessage(context, entryKey, "title", section.getString("title"));
            if (title == INVALID_COMPONENT) {
                skippedEntries++;
                continue;
            }

            final Component author = parseOptionalMiniMessage(context, entryKey, "author", section.getString("author"));
            if (author == INVALID_COMPONENT) {
                skippedEntries++;
                continue;
            }

            definitions.put(paintingKey, new Definition(width, height, assetId, title, author));
        }

        return new LoadResult(definitions, skippedEntries);
    }

    private static final Component INVALID_COMPONENT = Component.text("__invalid__");

    private static Component parseOptionalMiniMessage(
        final BootstrapContext context,
        final String entryKey,
        final String fieldName,
        final String value
    ) {
        final String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (count(trimmed, '<') != count(trimmed, '>')) {
            context.getLogger().warn(
                "Painting '{}': Invalid MiniMessage in '{}': Unbalanced tag delimiters",
                entryKey,
                fieldName
            );
            return INVALID_COMPONENT;
        }

        if (trimmed.contains("</")) {
            try {
                STRICT_MINI_MESSAGE.deserialize(trimmed);
            } catch (Throwable throwable) {
                context.getLogger().warn(
                    "Painting '{}': Invalid MiniMessage in '{}': {}",
                    entryKey,
                    fieldName,
                    throwable.getMessage()
                );
                return INVALID_COMPONENT;
            }
        }

        try {
            return MINI_MESSAGE.deserialize(trimmed);
        } catch (Throwable throwable) {
            context.getLogger().warn(
                "Painting '{}': Invalid MiniMessage in '{}': {}",
                entryKey,
                fieldName,
                throwable.getMessage()
            );
            return INVALID_COMPONENT;
        }
    }

    private static int count(final String value, final char target) {
        int total = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                total++;
            }
        }
        return total;
    }

    private static NamespacedKey parseExplicitNamespacedKey(final String value) {
        if (value == null || !value.contains(":")) {
            return null;
        }
        return NamespacedKey.fromString(value);
    }

    private static int skip(final BootstrapContext context, final String entryKey, final String reason) {
        context.getLogger().warn("Painting '{}': {}", entryKey, reason);
        return 1;
    }

    private record Definition(
        int width,
        int height,
        NamespacedKey assetId,
        Component title,
        Component author
    ) {}

    private record LoadResult(
        Map<NamespacedKey, Definition> definitions,
        int skippedEntries
    ) {}
}
