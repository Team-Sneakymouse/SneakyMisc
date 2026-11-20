package com.danidipp.sneakymisc.worldpaste

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasteAreaTest {
    @Test
    fun `a clipboard lands with every block keeping its offset from the clipboard origin`() {
        // darkvalescar1.schem: 215 x 151 x 122, Offset -122 -40 -123. The clipboard's own coordinates are arbitrary.
        val origin = intArrayOf(100, 0, -50)
        val min = intArrayOf(100 - 122, 0 - 40, -50 - 123)
        val max = intArrayOf(min[0] + 214, min[1] + 150, min[2] + 121)

        val box = PasteBox.landing(min, max, origin, intArrayOf(4991, 60, 4850))

        assertEquals(PasteBox(4869, 20, 4727, 5083, 170, 4848), box)
    }

    @Test
    fun `a landing that overflows throws instead of wrapping`() {
        assertFailsWith<ArithmeticException> {
            PasteBox.landing(intArrayOf(0, 0, 0), intArrayOf(10, 0, 0), intArrayOf(-10, 0, 0), intArrayOf(Int.MAX_VALUE - 5, 0, 0))
        }
    }

    @Test
    fun `entity positions are inside when within the box's blocks`() {
        val box = PasteBox(0, 10, 0, 15, 20, 15)

        assertTrue(box.contains(0.0, 10.0, 0.0))
        assertTrue(box.contains(15.99, 20.99, 15.99))
        assertFalse(box.contains(16.0, 15.0, 5.0))
        assertFalse(box.contains(5.0, 21.0, 5.0))
        assertFalse(box.contains(-0.01, 15.0, 5.0))
        assertTrue(box.footprintContains(5.0, 5.0))
    }

    @Test
    fun `the footprint ignores height, so players above the box count`() {
        val area = PasteArea(listOf(PasteBox(0, 10, 0, 15, 20, 15)))

        assertTrue(area.footprintContains(8.0, 8.0))
        assertFalse(area.contains(8.0, 300.0, 8.0))
    }

    @Test
    fun `chunks cover every box once, including negative coordinates`() {
        val area = PasteArea(listOf(
            PasteBox(-17, 0, 0, 0, 10, 15),
            PasteBox(0, 0, 0, 31, 10, 15),
        ))

        assertEquals(
            setOf(PasteChunk(-2, 0), PasteChunk(-1, 0), PasteChunk(0, 0), PasteChunk(1, 0)),
            area.chunks().toSet(),
        )
        assertEquals(4, area.chunks().size)
    }
}
