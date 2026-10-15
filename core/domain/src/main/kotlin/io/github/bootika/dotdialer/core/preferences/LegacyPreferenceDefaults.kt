package io.github.bootika.dotdialer.core.preferences

/** Frozen V215 fallback: an upgrade must not replace unset user choices with a new preset. */
object LegacyPreferenceDefaults {
    private val booleans = mapOf("avatar_frame" to false, "ignore_system_font_scale" to false,
        "expanded_list_details" to false, "ambient_droplets_enabled" to true,
        "direct_call_on_tap" to true, "dynamic_colors" to true, "liquid_glass_ui" to true,
        "outgoing_number_normalization" to true, "search_diacritic_normalization" to true,
        "repeat_contacts_in_search_results" to false, "pocket_mode_prevention" to true,
        "scroll_animation_enabled" to true, "show_recents_favorites" to true,
        "frosted_call_background" to true)
    private val strings = mapOf("call_screen_appearance" to "circular_avatar", "call_avatar_size" to "small",
        "incoming_answer_placement" to "left_top", "theme_mode" to "auto", "diagnostics_reporting_mode" to "automatic")
    private val ints = mapOf("dialpad_glass_strength_percent" to 30, "ambient_droplet_brightness" to 100,
        "custom_primary_color" to -8_411_657, "recent_history_period_days" to 365, "recent_history_max_entries" to 500)
    fun booleanFor(key: String): Boolean? = booleans[key]
    fun stringFor(key: String): String? = strings[key]
    fun intFor(key: String): Int? = ints[key]
    fun floatFor(key: String): Float? = if (key == "hangup_button_width") 0.40f else null
}
