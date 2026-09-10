package com.danidipp.sneakymisc.paintings

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule

class PaintingsModule : SneakyModule() {
    override val commands = listOf(
        SneakyMiscCommand(PaintingsCommand().build(), "Inspect and give custom painting variants")
    )
}
