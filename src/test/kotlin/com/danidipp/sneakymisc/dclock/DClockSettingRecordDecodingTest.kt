package com.danidipp.sneakymisc.dclock

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DClockSettingRecordDecodingTest {
    @Test
    fun `floors fractional scientific notation values`() {
        val record = Json.decodeFromString<SettingRecord>(
            """{"id":"9195r6z2omp337a","value":1.8125439144e+09}"""
        )

        assertEquals(1_812_543_914L, record.value)
    }
}
