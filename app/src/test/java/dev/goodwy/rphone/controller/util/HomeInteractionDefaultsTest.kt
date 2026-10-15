package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInteractionDefaultsTest {
    @Test
    fun `new installs use the approved home interaction defaults`() {
        assertTrue(PreferenceManager.DEFAULT_DIRECT_CALL_ON_TAP)
        assertTrue(PreferenceManager.DEFAULT_CLEAR_SEARCH_AFTER_CALL)
        assertTrue(PreferenceManager.DEFAULT_SWIPE_ENABLED)
        assertEquals(
            PreferenceManager.SWIPE_ACTION_MESSAGE,
            PreferenceManager.DEFAULT_SWIPE_RIGHT_ACTION,
        )
        assertEquals(
            PreferenceManager.SWIPE_ACTION_WHATSAPP,
            PreferenceManager.DEFAULT_SWIPE_LEFT_ACTION,
        )
    }

    @Test
    fun `new installs use the approved call screen defaults`() {
        assertEquals(
            PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
            PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
        )
        assertEquals(
            PreferenceManager.CALL_AVATAR_SIZE_SMALL,
            PreferenceManager.DEFAULT_CALL_AVATAR_SIZE,
        )
        assertEquals(
            PreferenceManager.INCOMING_CALL_UI_DEFAULT_SWIPE,
            PreferenceManager.DEFAULT_INCOMING_CALL_UI_MODE,
        )
        assertEquals(
            PreferenceManager.INCOMING_ANSWER_PLACEMENT_LEFT_TOP,
            PreferenceManager.DEFAULT_INCOMING_ANSWER_PLACEMENT,
        )
        assertEquals(0.40f, PreferenceManager.DEFAULT_HANGUP_WIDTH, 0.001f)
        assertFalse(PreferenceManager.DEFAULT_FROSTED_CALL_BACKGROUND)
        assertTrue(AmbientDropletPreference.DEFAULT_ENABLED)
        assertEquals(140, AmbientDropletPreference.DEFAULT_BRIGHTNESS)
        assertEquals(10, AmbientDropletPreference.MIN_BRIGHTNESS)
        assertEquals(200, AmbientDropletPreference.MAX_BRIGHTNESS)
        assertEquals(70f, AmbientDropletPreference.DEFAULT_OPACITY_PERCENT)
    }
}
