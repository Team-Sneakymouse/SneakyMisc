package com.danidipp.sneakymisc.dclock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class DClockSettingRecordDecodingTest {
    @Test
    fun `floors fractional seconds`() {
        assertEquals(1234L, SettingRecord.dclockValue("""{"id":"9195r6z2omp337a","key":"dclock","value":1234.8}"""))
    }

    @Test
    fun `floors scientific notation values`() {
        assertEquals(1_812_543_914L, SettingRecord.dclockValue("""{"key":"dclock","value":1.8125439144e+09}"""))
    }

    @Test
    fun `ignores other settings whatever their value`() {
        assertNull(SettingRecord.dclockValue("""{"key":"donation_timer_x","value":{"end":1}}"""))
        assertNull(SettingRecord.dclockValue("""{"key":"dvz_next_show","value":true}"""))
    }

    @Test
    fun `null value is unknown`() {
        assertNull(SettingRecord.dclockValue("""{"key":"dclock","value":null}"""))
    }

    @Test
    fun `non-numeric dclock value fails`() {
        assertFails { SettingRecord.dclockValue("""{"key":"dclock","value":{"a":1}}""") }
    }
}
