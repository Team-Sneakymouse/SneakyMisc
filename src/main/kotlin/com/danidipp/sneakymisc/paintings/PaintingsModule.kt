package com.danidipp.sneakymisc.paintings

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.event.RegistryEvents
import org.bukkit.event.Listener

class PaintingsModule : SneakyModule() {
    override val commands = listOf(
        SneakyMiscCommand(PaintingsCommand().build(), "Inspect and give custom painting variants")
    )

    companion object {
        fun bootstrap(context: BootstrapContext) {
            val configPath = context.dataDirectory.resolve("paintings.yml")
            val loadResult = PaintingsConfig.load(
                configPath = configPath,
                info = { context.logger.info(it) },
                warn = { context.logger.warn(it) },
            )

            context.lifecycleManager.registerEventHandler(RegistryEvents.PAINTING_VARIANT.freeze()) { event ->
                var registeredCount = 0

                for ((key, definition) in loadResult.definitions) {
                    runCatching {
                        event.registry().register(RegistryKey.PAINTING_VARIANT.typedKey(key)) { builder ->
                            builder.width(definition.width)
                                .height(definition.height)
                                .assetId(definition.assetId)

                            definition.title?.let(builder::title)
                            definition.author?.let(builder::author)
                        }
                    }.onSuccess {
                        registeredCount++
                    }.onFailure {
                        context.logger.warn("Painting '{}': failed to register: {}", key.asString(), it.message ?: it.javaClass.simpleName)
                    }
                }

                context.logger.info(
                    "Paintings bootstrap finished for {}: configured={}, registered={}, skipped={}",
                    configPath.toAbsolutePath().toString(),
                    loadResult.definitions.size,
                    registeredCount,
                    loadResult.skippedEntries.size + (loadResult.definitions.size - registeredCount),
                )
            }
        }
    }
}

