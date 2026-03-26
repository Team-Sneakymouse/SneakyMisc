package com.danidipp.sneakymisc

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack

data class SneakyMiscCommand(
    val node: LiteralCommandNode<CommandSourceStack>,
    val description: String,
) {}