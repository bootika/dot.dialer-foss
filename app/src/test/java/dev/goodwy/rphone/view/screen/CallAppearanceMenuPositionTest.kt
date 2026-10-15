package dev.goodwy.rphone.view.screen

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CallAppearanceMenuPositionTest {
    @Test
    fun `menu opens centered directly below the long press`() {
        val position = callAppearancePopupPosition(
            anchor = IntOffset(500, 700),
            windowSize = IntSize(1080, 2200),
            popupSize = IntSize(300, 180),
            gapPx = 20,
            marginPx = 16,
        )

        assertEquals(IntOffset(350, 720), position)
    }

    @Test
    fun `menu stays inside horizontal screen edges`() {
        val leftPosition = callAppearancePopupPosition(
            anchor = IntOffset(5, 600),
            windowSize = IntSize(1080, 2200),
            popupSize = IntSize(300, 180),
            gapPx = 20,
            marginPx = 16,
        )
        val rightPosition = callAppearancePopupPosition(
            anchor = IntOffset(1075, 600),
            windowSize = IntSize(1080, 2200),
            popupSize = IntSize(300, 180),
            gapPx = 20,
            marginPx = 16,
        )

        assertEquals(16, leftPosition.x)
        assertEquals(764, rightPosition.x)
    }

    @Test
    fun `menu flips above the finger near the bottom edge`() {
        val position = callAppearancePopupPosition(
            anchor = IntOffset(500, 2140),
            windowSize = IntSize(1080, 2200),
            popupSize = IntSize(300, 180),
            gapPx = 20,
            marginPx = 16,
        )

        assertEquals(IntOffset(350, 1940), position)
    }
}
