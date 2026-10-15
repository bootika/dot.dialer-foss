package io.github.bootika.dotdialer.core.preferences

/**
 * Product defaults reconciled with the owner's device snapshot on 2026-10-08.
 *
 * Only reusable UI and interaction preferences belong here. Runtime state,
 * account identifiers, entitlement flags, onboarding state and contact-specific
 * data must remain device/user data and are deliberately excluded.
 */
object DotDialerPreferenceDefaults {
    const val INSTALLATION_PROFILE_KEY = "owner_defaults_installation_profile"
    const val OWNER_PROFILE_REVISION = 2
    const val DEFAULT_HANGUP_WIDTH = 0.40f
    const val DEFAULT_CALL_SCREEN_APPEARANCE = "full_photo"
    const val DEFAULT_FROSTED_CALL_BACKGROUND = false
    const val DEFAULT_DROPLET_BRIGHTNESS = 140
    const val DEFAULT_IGNORE_SYSTEM_FONT_SCALE = true
    const val DEFAULT_QUICK_RESPONSES = "Nu pot vorbi, scrie-mi pe WhatsApp|||Nu pot vorbi, te sun eu mai tarziu|||Nu pot vorbi, suna-ma mai tarziu"
    private val booleanDefaults = mapOf(
        "avatar_frame" to false,
        "ignore_system_font_scale" to DEFAULT_IGNORE_SYSTEM_FONT_SCALE,
        "expanded_list_details" to false,
        "ambient_droplets_enabled" to true,
        "direct_call_on_tap" to true,
        "dynamic_colors" to true,
        "liquid_glass_ui" to true,
        "outgoing_number_normalization" to true,
        "search_diacritic_normalization" to true,
        "repeat_contacts_in_search_results" to false,
        "pocket_mode_prevention" to true,
        "scroll_animation_enabled" to true,
        "show_recents_favorites" to true,
        "android_auto_enabled" to false,
        "BLUR_dialpad" to false,
        "frosted_call_background" to DEFAULT_FROSTED_CALL_BACKGROUND,
        "lg_dialpad" to true,
        "show_first_letter" to true,
        "show_picture" to true,
    )

    private val stringDefaults = mapOf(
        "call_screen_appearance" to DEFAULT_CALL_SCREEN_APPEARANCE,
        "call_avatar_size" to "small",
        "contact_avatar_size" to "medium",
        "incoming_answer_placement" to "left_top",
        "theme_mode" to "light",
        "diagnostics_reporting_mode" to "automatic",
        "custom_quick_responses" to DEFAULT_QUICK_RESPONSES,
    )

    private val floatDefaults = mapOf(
        "hangup_button_width" to DEFAULT_HANGUP_WIDTH,
    )

    private val intDefaults = mapOf(
        "dialpad_glass_strength_percent" to DialpadGlassPolicy.DEFAULT_STRENGTH_PERCENT,
        "ambient_droplet_brightness" to DEFAULT_DROPLET_BRIGHTNESS,
        "incoming_call_ui_mode" to 10,
        "custom_primary_color" to -8_411_657,
        "recent_history_period_days" to RecentHistoryPolicy.DEFAULT_PERIOD_DAYS,
        "recent_history_max_entries" to RecentHistoryPolicy.DEFAULT_MAX_ENTRIES,
    )

    fun booleanFor(key: String): Boolean? = booleanDefaults[key]

    fun stringFor(key: String): String? = stringDefaults[key]

    fun floatFor(key: String): Float? = floatDefaults[key]

    fun intFor(key: String): Int? = intDefaults[key]

    /** Explicit allowlist of the reusable device snapshot; never seed account or runtime state. */
    fun freshInstallPreset(): Map<String, Any> = setOf("ambient_droplet_brightness", "android_auto_enabled",
        "BLUR_dialpad", "call_avatar_size", "call_screen_appearance", "contact_avatar_size",
        "dialpad_glass_strength_percent", "frosted_call_background", "hangup_button_width",
        "ignore_system_font_scale", "incoming_call_ui_mode", "lg_dialpad", "recent_history_max_entries",
        "show_first_letter", "show_picture", "theme_mode", "custom_quick_responses").associateWith { key ->
        booleanFor(key) ?: stringFor(key) ?: intFor(key) ?: floatFor(key) ?: error("Unspecified preset key")
    }
}
