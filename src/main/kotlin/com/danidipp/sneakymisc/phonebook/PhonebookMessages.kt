package com.danidipp.sneakymisc.phonebook

import net.kyori.adventure.text.Component

data class PhonebookMessage(
    val key: String,
    val arguments: Map<String, Component> = emptyMap(),
)
