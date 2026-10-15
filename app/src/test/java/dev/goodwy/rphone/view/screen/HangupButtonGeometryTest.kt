package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangupButtonGeometryTest {

    @Test
    fun `minimum setting is a usable circle`() {
        val geometry = hangupButtonGeometry(
            availableWidthDp = 371f,
            savedSetting = 0.1f,
        )

        assertEquals(76f, geometry.widthDp, 0.001f)
        assertEquals(76f, geometry.heightDp, 0.001f)
        assertTrue(geometry.isCircle)
    }

    @Test
    fun `every slider step grows on an s25 width`() {
        val widths = (1..10).map { step ->
            hangupButtonGeometry(
                availableWidthDp = 371f,
                savedSetting = step / 10f,
            ).widthDp
        }

        widths.zipWithNext().forEach { (smaller, larger) ->
            assertTrue("Expected $larger to be greater than $smaller", larger > smaller)
        }
    }

    @Test
    fun `maximum setting occupies the available call screen width`() {
        val geometry = hangupButtonGeometry(
            availableWidthDp = 371f,
            savedSetting = 1f,
        )

        assertEquals(371f, geometry.widthDp, 0.001f)
        assertEquals(68f, geometry.heightDp, 0.001f)
        assertFalse(geometry.isCircle)
    }

    @Test
    fun `stored values are clamped safely`() {
        assertEquals(
            76f,
            hangupButtonGeometry(371f, Float.NEGATIVE_INFINITY).widthDp,
            0.001f,
        )
        assertEquals(
            371f,
            hangupButtonGeometry(371f, Float.POSITIVE_INFINITY).widthDp,
            0.001f,
        )
    }
}
