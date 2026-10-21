package com.danidipp.sneakymisc.elevators

import kotlin.test.*

class ElevatorFloorInputTest {
    @Test fun `selection evaluates after tens then ones including leading zero`() {
        val input = ElevatorFloorInput()
        assertNull(input.digit(0))
        assertEquals("5", input.digit(5))
        assertNull(input.digit(9))
        input.reset()
        assertNull(input.digit(9))
        assertNull(input.ones)
        assertEquals("92", input.digit(2))
    }

    @Test fun `clear removes a partial selection and starts again with tens`() {
        val input = ElevatorFloorInput()
        input.digit(7)
        input.reset()
        assertNull(input.tens)
        assertNull(input.ones)
        assertNull(input.digit(0))
        assertEquals("0", input.digit(0))
    }
}
