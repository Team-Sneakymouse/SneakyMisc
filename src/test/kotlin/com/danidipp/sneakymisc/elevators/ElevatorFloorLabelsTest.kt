package com.danidipp.sneakymisc.elevators

import kotlin.test.*

class ElevatorFloorLabelsTest {
    @Test fun `only G 2 to 99 and B1 to B9 are stored labels`() {
        for (label in listOf("G", "2", "9", "10", "99", "B1", "B9")) assertTrue(ElevatorFloorLabels.isCanonical(label), label)
        for (label in listOf("0", "1", "B", "B0", "B10", "01", "02", "100", "-1", "g", "b1", "1B", "GG", "")) {
            assertFalse(ElevatorFloorLabels.isCanonical(label), label)
        }
    }

    @Test fun `aliases map 1 to G and digit-B to B-digit`() {
        assertEquals("G", ElevatorFloorLabels.canonical("1"))
        assertEquals("B1", ElevatorFloorLabels.canonical("1B"))
        assertEquals("B9", ElevatorFloorLabels.canonical("9B"))
        assertEquals("0B", ElevatorFloorLabels.canonical("0B"))
        assertEquals("12", ElevatorFloorLabels.canonical("12"))
        assertEquals("tower-G", ElevatorFloorLabels.canonicalId("tower-1"))
        assertEquals("tower-B2", ElevatorFloorLabels.canonicalId("tower-2B"))
        assertEquals("tower", ElevatorFloorLabels.canonicalId("tower"))
    }
}
