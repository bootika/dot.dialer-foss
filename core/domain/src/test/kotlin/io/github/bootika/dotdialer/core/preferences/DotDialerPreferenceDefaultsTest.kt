package io.github.bootika.dotdialer.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DotDialerPreferenceDefaultsTest {
    @Test
    fun `uses the reusable interaction and appearance values from the owner preset`() {
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("direct_call_on_tap"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("ambient_droplets_enabled"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("dynamic_colors"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("liquid_glass_ui"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("pocket_mode_prevention"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("scroll_animation_enabled"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("search_diacritic_normalization"))
        assertEquals(false, DotDialerPreferenceDefaults.booleanFor("repeat_contacts_in_search_results"))
        assertNull(DotDialerPreferenceDefaults.booleanFor("avatar_frame"))
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("ignore_system_font_scale"))
        assertEquals(false, DotDialerPreferenceDefaults.booleanFor("expanded_list_details"))
        assertEquals("light", DotDialerPreferenceDefaults.stringFor("theme_mode"))
        assertEquals("full_photo", DotDialerPreferenceDefaults.stringFor("call_screen_appearance"))
        assertEquals("small", DotDialerPreferenceDefaults.stringFor("call_avatar_size"))
        assertEquals("left_top", DotDialerPreferenceDefaults.stringFor("incoming_answer_placement"))
        assertEquals(-8_411_657, DotDialerPreferenceDefaults.intFor("custom_primary_color"))
        assertEquals(140, DotDialerPreferenceDefaults.intFor("ambient_droplet_brightness"))
        assertEquals(365, DotDialerPreferenceDefaults.intFor("recent_history_period_days"))
        assertEquals(1000, DotDialerPreferenceDefaults.intFor("recent_history_max_entries"))
        assertEquals(
            0.40f,
            requireNotNull(DotDialerPreferenceDefaults.floatFor("hangup_button_width")),
            0.001f,
        )
    }

    @Test
    fun `keeps the optional Favorites strip visible by default`() {
        assertEquals(true, DotDialerPreferenceDefaults.booleanFor("show_recents_favorites"))
    }

    @Test
    fun `does not turn personal runtime state into product defaults`() {
        listOf(
            "favorites_order",
            "is_pro_iap",
            "is_pro_sub",
            "last_opened_tab",
            "last_used_account_name",
            "last_used_account_type",
            "onboarding_shown",
            "tab_show_setting",
        ).forEach { key ->
            assertNull(DotDialerPreferenceDefaults.booleanFor(key))
            assertNull(DotDialerPreferenceDefaults.stringFor(key))
            assertNull(DotDialerPreferenceDefaults.intFor(key))
            assertNull(DotDialerPreferenceDefaults.floatFor(key))
        }
    }
}
