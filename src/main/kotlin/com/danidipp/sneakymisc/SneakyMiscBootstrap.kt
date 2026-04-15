package com.danidipp.sneakymisc

import com.danidipp.sneakymisc.paintings.PaintingsModule
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap

class SneakyMiscBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        PaintingsModule.bootstrap(context);
    }
}