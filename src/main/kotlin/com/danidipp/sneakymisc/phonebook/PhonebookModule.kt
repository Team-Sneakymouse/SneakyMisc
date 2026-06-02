package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyModule
import java.util.logging.Logger

class PhonebookModule(private val logger: Logger) : SneakyModule() {
    companion object {
        val deps = listOf("SneakyCharacterManager", "SneakyCellPhones")
    }
}
