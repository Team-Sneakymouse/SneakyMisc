@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")
package com.danidipp.sneakymisc.databasesync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountRecord(
    @SerialName("id") val recordId: String? = null,
    var name: String,
    val owner: String,
    val main: Boolean,
    val dvz: Boolean,
)

@Serializable
data class CharacterRecord(
    @SerialName("id") val recordId: String? = null,
    var name: String,
    val account: String,
    var tags: Map<String, String>? = mapOf(),
)
