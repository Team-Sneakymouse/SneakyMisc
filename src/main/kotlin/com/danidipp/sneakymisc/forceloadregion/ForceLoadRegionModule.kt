package com.danidipp.sneakymisc.forceloadregion

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule

internal class ForceLoadRegionModule : SneakyModule() {
    override val commands = listOf(
        SneakyMiscCommand(
            ForceLoadRegionCommand().build(),
            "Force-load every chunk fully inside a WorldGuard region",
        )
    )

    companion object {
        val deps = listOf("WorldGuard")
    }
}
