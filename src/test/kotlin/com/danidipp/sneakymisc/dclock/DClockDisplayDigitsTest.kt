package com.danidipp.sneakymisc.dclock

import kotlin.test.Test
import kotlin.test.assertEquals

class DClockDisplayDigitsTest {
    @Test
    fun `pads to the display width`() = assertEquals("00042", DClockDisplay.digits(42, 5))

    @Test
    fun `shows all nines when the value does not fit`() = assertEquals("999", DClockDisplay.digits(12345, 3))

    @Test
    fun `never shows below zero`() = assertEquals("000", DClockDisplay.digits(-5, 3))
}
