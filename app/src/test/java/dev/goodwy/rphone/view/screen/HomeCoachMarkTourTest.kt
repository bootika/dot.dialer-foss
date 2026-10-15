package dev.goodwy.rphone.view.screen

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoachMarkTourTest {
    @Test fun `search example is George and has no text cursor character`() {
        assertEquals("George", HOME_TOUR_SEARCH_SAMPLE)
        assertEquals(6, tourTypedCharacterCount(0.57f, HOME_TOUR_SEARCH_SAMPLE.length))
    }

    @Test fun `favorite spotlight includes measured header and row at every font size`() {
        for (scale in listOf(1f, 1.5f, 2f)) {
            val header = Rect(0f, 100f, 360f, 100f + 28f * scale)
            val row = Rect(0f, header.bottom, 360f, header.bottom + 86f * scale)
            val step = homeTourSteps(setOf(HomeTourTarget.Favorites)).single()
            assertEquals(Rect(0f, header.top, 360f, row.bottom),
                homeTourSpotlight(homeTourFavoritesBounds(header, row), step, Rect(0f, 0f, 360f, 800f), 12f))
        }
    }

    @Test fun `recent spotlight never expands into section title`() {
        val row = Rect(14f, 228f, 346f, 310f)
        val steps = homeTourSteps(setOf(HomeTourTarget.RecentRow))
        steps.forEach { assertEquals(row, homeTourSpotlight(row, it, Rect(0f, 0f, 360f, 800f), 12f)) }
    }

    @Test
    fun `complete home surface produces six ordered steps`() {
        val steps = homeTourSteps(HomeTourTarget.entries.toSet())

        assertEquals(6, steps.size)
        assertEquals(
            listOf(
                HomeTourTarget.RecentAvatar,
                HomeTourTarget.RecentRow,
                HomeTourTarget.RecentRow,
                HomeTourTarget.Search,
                HomeTourTarget.Favorites,
                HomeTourTarget.Dialpad,
            ),
            steps.map(HomeTourStep::target),
        )
        assertEquals(
            HomeTourDemo.entries,
            steps.map(HomeTourStep::demo),
        )
    }

    @Test
    fun `swipe demo travels left and right before returning to rest`() {
        assertEquals(0f, tourSwipeOffset(0.1f))
        assertTrue(tourSwipeOffset(0.3f) < 0f)
        assertTrue(tourSwipeOffset(0.65f) > 0f)
        assertEquals(0f, tourSwipeOffset(0.9f))
    }

    @Test
    fun `search demo types all characters and clears before repeating`() {
        assertEquals(0, tourTypedCharacterCount(0.1f, 3))
        assertEquals(3, tourTypedCharacterCount(0.57f, 3))
        assertEquals(0, tourTypedCharacterCount(0.9f, 3))
    }

    @Test
    fun `missing recent data omits only recent-specific guidance`() {
        val steps = homeTourSteps(
            setOf(HomeTourTarget.Search, HomeTourTarget.Favorites, HomeTourTarget.Dialpad),
        )

        assertEquals(
            listOf(HomeTourTarget.Search, HomeTourTarget.Favorites, HomeTourTarget.Dialpad),
            steps.map(HomeTourStep::target),
        )
    }

    @Test
    fun `card stays below an upper target when there is room`() {
        val top = coachMarkCardTop(
            target = Rect(10f, 100f, 200f, 180f),
            screenHeightPx = 1_000f,
            cardHeightPx = 250f,
            marginPx = 20f,
        )

        assertEquals(200f, top)
    }

    @Test
    fun `card moves above a lower target and remains on screen`() {
        val top = coachMarkCardTop(
            target = Rect(10f, 800f, 200f, 900f),
            screenHeightPx = 1_000f,
            cardHeightPx = 250f,
            marginPx = 20f,
        )

        assertEquals(530f, top)
        assertTrue(top >= 20f)
    }
}
