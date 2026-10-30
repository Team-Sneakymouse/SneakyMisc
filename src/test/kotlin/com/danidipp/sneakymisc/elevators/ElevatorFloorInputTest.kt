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

    @Test fun `zero one selects ground and zero zero asks for the nonexistent floor 0`() {
        val input = ElevatorFloorInput()
        input.digit(0)
        assertEquals("G", input.digit(1))
        input.reset()
        input.digit(0)
        assertEquals("0", input.digit(0))
    }

    @Test fun `basement takes the tens slot and completes with one digit`() {
        val input = ElevatorFloorInput()
        input.basement()
        assertTrue(input.basement)
        assertNull(input.tens)
        assertEquals("B3", input.digit(3))
        assertNull(input.digit(4))
        input.reset()
        input.basement()
        assertEquals("B0", input.digit(0))
    }

    @Test fun `basement is ignored after a digit`() {
        val input = ElevatorFloorInput()
        input.digit(1)
        input.basement()
        assertFalse(input.basement)
        assertEquals("12", input.digit(2))
    }

    @Test fun `clear removes a partial selection and starts again with tens`() {
        val input = ElevatorFloorInput()
        input.basement()
        input.reset()
        assertFalse(input.basement)
        input.digit(7)
        input.reset()
        assertNull(input.tens)
        assertNull(input.ones)
        assertNull(input.digit(0))
        assertEquals("0", input.digit(0))
    }
}
