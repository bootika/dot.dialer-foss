package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallActionGridTest {
    @Test fun `normal and enlarged labels use one whole pixel pitch`() {
        listOf(240, 264, 312, 360, 480, 720).forEach { width ->
            listOf(102, 110, 132).forEach { minimum ->
                val geometry = callActionGridGeometry(width, 88, minimum)
                assertTrue(geometry.pitch >= minimum)
                assertTrue(geometry.cellWidth + (geometry.columns - 1) * geometry.pitch <= width)
                assertTrue(geometry.columns in 1..3)
            }
        }
    }
    @Test fun `normal phone keeps three columns without overlapping large font labels`() {
        assertEquals(CallActionGridGeometry(3, 102, 88), callActionGridGeometry(312, 88, 102))
        assertEquals(CallActionGridGeometry(3, 110, 88), callActionGridGeometry(312, 88, 110))
    }
    @Test fun `narrow windows reflow instead of squeezing touch targets`() {
        assertEquals(2, callActionGridGeometry(240, 88, 110).columns)
        assertEquals(1, callActionGridGeometry(160, 88, 110).columns)
        assertEquals(144, callActionGridGeometry(312, 144, 152).cellWidth)
        assertEquals(2, callActionGridGeometry(312, 144, 152).columns)
    }
}
