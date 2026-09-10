package com.danidipp.sneakymisc.crates

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule

class CratesModule(plugin: SneakyMisc) : SneakyModule() {
    override val commands = listOf(
        SneakyMiscCommand(CrateCheckCommand().build(), "Check whether a player's crate is full"),
    )

    override val listeners = listOf(Crate.listener, CrateGui.listener)

    init {
        CrateGui.registerExternalEvents(plugin)
    }
}
