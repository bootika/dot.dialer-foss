package dev.goodwy.rphone.view.screen

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoachMarkTourTest {
    @Test fun `disabled swipes keep tap and omit the swipe step`() {
        val steps = homeTourSteps(setOf(HomeTourTarget.RecentRow), hasSwipeActions = false)
        assertEquals(listOf(HomeTourDemo.RowTap), steps.map(HomeTourStep::demo))
    }

    @Test fun `favorite spotlight surrounds only the measured avatar`() {
        val avatar = Rect(22f, 174f, 78f, 230f)
        val step = homeTourSteps(setOf(HomeTourTarget.Favorites)).single()
        assertTrue(step.circularSpotlight)
        assertEquals(Rect(19f, 171f, 81f, 233f),
            homeTourSpotlight(avatar, step, Rect(0f, 0f, 360f, 800f), 3f))
    }

    @Test fun `search spotlight matches the real field width and height`() {
        val search = Rect(16f, 30f, 304f, 86f)
        val step = homeTourSteps(setOf(HomeTourTarget.Search)).single()
        assertEquals(search, homeTourSpotlight(search, step, Rect(0f, 0f, 360f, 800f), 3f))
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
    @Test
    fun `extreme viewport keeps the card inside margins even when it must overlap`() {
        val top = coachMarkCardTop(Rect(10f, 100f, 200f, 200f), 320f, 210f, 16f, 12f)
        assertEquals(94f, top)
        assertTrue(top + 210f <= 320f - 16f)
    }
}
