package com.danidipp.sneakymisc;

import com.danidipp.sneakymisc.paintings.PaintingsBootstrapSupport;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

public final class SneakyMiscBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(final BootstrapContext context) {
        PaintingsBootstrapSupport.bootstrap(context);
    }
}
