package com.danidipp.sneakymisc

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.Listener

/**
 * A self-contained mechanic whose commands and listeners are registered by [SneakyMisc].
 * Mechanics that require another plugin expose a `deps` list checked before construction in
 * `SneakyMisc.onEnable`. Declare those plugins in `src/resources/paper-plugin.yml` too, with
 * optional dependencies marked `required: false` and the needed load order/classpath access.
 * Optional integrations in always-registered mechanics must remain loadable when the plugin is absent.
 */
abstract class SneakyModule {
    open val commands: List<SneakyMiscCommand> = listOf()
    open val listeners: List<Listener> = listOf()

    open fun registerBrigadierCommands(commands: Commands) {}
}
