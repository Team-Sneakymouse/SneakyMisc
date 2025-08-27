@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")
package com.danidipp.sneakymisc.databasesync

import com.danidipp.sneakypocketbase.BaseRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AccountRecord(
    @Transient override val recordId: String? = null,
    var name: String,
    val owner: String,
    val main: Boolean,
    val dvz: Boolean,
): BaseRecord(recordId)

@Serializable
data class CharacterRecord(
    @Transient override val recordId: String? = null,
    var name: String,
    val account: String,
    var tags: Map<String, String>? = mapOf(),
): BaseRecord(recordId)