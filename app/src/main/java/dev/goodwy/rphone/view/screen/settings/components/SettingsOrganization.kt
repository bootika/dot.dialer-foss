package dev.goodwy.rphone.view.screen.settings.components

import dev.goodwy.rphone.R

internal enum class SettingsEditor {
    ACCESSIBILITY, ADVANCED, REPORTING, INTERFACE, AVATARS, FONT, HOME, HISTORY, CALL_UI, CALL_EFFECTS, ACCOUNTS, NUMBERS, DEVICE, QUICK, CALLS, SOUND, SCROLL, FRAME, KEYPAD, SOURCES, CONTACTS, BLOCKED, AUTH, CALL_AUTH, SUPPORT, DIAGNOSTICS, CONTEXT, LEGACY, RECORDINGS
}

internal enum class SettingsFunction(val title: Int) {
    INTERFACE(R.string.interface_settings), CALLS(R.string.call_behavior), CONTACTS(R.string.contacts_and_data),
    SOUND(R.string.sound_and_vibration), PRIVACY(R.string.privacy_and_blocking), HELP(R.string.help_and_diagnostics)
}

internal data class SettingsOption(
    val id: String,
    val label: Int,
    val editor: SettingsEditor,
    val advanced: Boolean,
    val category: Int,
) {
    val planFeature: io.github.bootika.dotdialer.core.license.PlanFeature? get() = when (id) {
        "H11" -> io.github.bootika.dotdialer.core.license.PlanFeature.CALL_PRODUCTIVITY
        "K14", "K15" -> io.github.bootika.dotdialer.core.license.PlanFeature.RECORDINGS_AUTOMATIC
        "K11" -> io.github.bootika.dotdialer.core.license.PlanFeature.CALL_MESSAGE_CHANNEL
        "I03", "C07", "C02", "A05", "A07", "C03", "C04", "C08", "A06" -> io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE
        "T19", "T17" -> io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER
        "T09", "T10", "T11", "T12", "T14", "T15", "T16" -> io.github.bootika.dotdialer.core.license.PlanFeature.MANUAL_OPTIMIZER
        else -> null
    }
    val function: SettingsFunction get() = when (editor) {
        SettingsEditor.INTERFACE, SettingsEditor.AVATARS, SettingsEditor.FONT, SettingsEditor.KEYPAD,
        SettingsEditor.FRAME, SettingsEditor.ACCESSIBILITY, SettingsEditor.HOME, SettingsEditor.HISTORY,
        SettingsEditor.CALL_UI, SettingsEditor.CALL_EFFECTS -> SettingsFunction.INTERFACE
        SettingsEditor.ACCOUNTS, SettingsEditor.NUMBERS, SettingsEditor.DEVICE,
        SettingsEditor.QUICK, SettingsEditor.CALLS, SettingsEditor.RECORDINGS -> SettingsFunction.CALLS
        SettingsEditor.SOUND, SettingsEditor.SCROLL -> SettingsFunction.SOUND
        SettingsEditor.SOURCES, SettingsEditor.CONTACTS -> SettingsFunction.CONTACTS
        SettingsEditor.BLOCKED, SettingsEditor.AUTH, SettingsEditor.CALL_AUTH, SettingsEditor.REPORTING -> SettingsFunction.PRIVACY
        SettingsEditor.SUPPORT, SettingsEditor.DIAGNOSTICS, SettingsEditor.ADVANCED,
        SettingsEditor.CONTEXT, SettingsEditor.LEGACY -> SettingsFunction.HELP
    }
    fun isAvailable(flavor: String, autoAvailable: Boolean, testBuild: Boolean = dev.goodwy.rphone.BuildConfig.DEBUG): Boolean = when (id) {
        "K13", "K14", "K15" -> dev.goodwy.rphone.BuildConfig.DICTATE_INTEGRATION_ENABLED
        "O02" -> testBuild
        "T09", "T10" -> flavor == "gplay"
        "F01" -> flavor == "foss"
        "K04" -> autoAvailable
        else -> editor != SettingsEditor.CONTEXT && editor != SettingsEditor.LEGACY
    }
}

/** Canonical navigation/search classification; preference keys and defaults are not duplicated here. */
internal val settingsOptions = listOf(
    SettingsOption("I01", R.string.app_language, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("I02", R.string.theme, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("I03", R.string.accent_color, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("I06", R.string.contact_name_size, SettingsEditor.ACCESSIBILITY, false, R.string.accessibility),
    SettingsOption("I07", R.string.expanded_list_details, SettingsEditor.ACCESSIBILITY, false, R.string.accessibility),
    SettingsOption("I08", R.string.interface_scale, SettingsEditor.ACCESSIBILITY, false, R.string.accessibility),
    SettingsOption("I09", R.string.subtle_animations, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("I11", R.string.high_contrast_text, SettingsEditor.ACCESSIBILITY, false, R.string.accessibility),
    SettingsOption("I12", R.string.voice_search_title, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("A03", R.string.show_picture_in_avatar, SettingsEditor.AVATARS, false, R.string.avatars_settings),
    SettingsOption("I04", R.string.avatar_palette, SettingsEditor.LEGACY, false, R.string.avatars_settings),
    SettingsOption("I05", R.string.avatar_size, SettingsEditor.AVATARS, false, R.string.avatars_settings),
    SettingsOption("A01", R.string.show_first_letter_in_avatar, SettingsEditor.AVATARS, false, R.string.avatars_settings),
    SettingsOption("I10", R.string.ignore_system_font_scale, SettingsEditor.ACCESSIBILITY, true, R.string.accessibility),
    SettingsOption("H04", R.string.show_recents_favorites, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H08", R.string.recent_history_period, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H11", R.string.quick_action_gesture, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H01", R.string.tap_a_recent, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H02", R.string.swipe_right_action, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H03", R.string.swipe_left_action, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H07", R.string.search_ignore_diacritics, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H06", R.string.search_repeat_contacts, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H05", R.string.clear_search_after_call, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("H09", R.string.recent_history_maximum, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("C05", R.string.incoming_call_ui, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C06", R.string.incoming_answer_placement, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C01", R.string.call_appearance, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C09", R.string.small_call_photos_in_circle, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C07", R.string.settings_call_default_background, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C02", R.string.frosted_call_background, SettingsEditor.CALL_EFFECTS, true, R.string.call_screen),
    SettingsOption("A05", R.string.liquid_glass_call_screen, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("A07", R.string.blur_effect_call_screen, SettingsEditor.CALL_EFFECTS, true, R.string.call_screen),
    SettingsOption("A04", R.string.settings_interface_hide_avatar_with_bg, SettingsEditor.LEGACY, false, R.string.call_screen),
    SettingsOption("C03", R.string.ambient_droplets, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C04", R.string.ambient_droplet_brightness, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("C08", R.string.customize_width, SettingsEditor.CALL_EFFECTS, true, R.string.settings_fine_tuning),
    SettingsOption("K13", R.string.recordings_title, SettingsEditor.RECORDINGS, false, R.string.recordings_title),
    SettingsOption("K14", R.string.recordings_auto_transcribe, SettingsEditor.RECORDINGS, false, R.string.recordings_title),
    SettingsOption("K15", R.string.recordings_auto_translate, SettingsEditor.RECORDINGS, false, R.string.recordings_title),
    SettingsOption("K01", R.string.default_sim, SettingsEditor.ACCOUNTS, false, R.string.calling_accounts),
    SettingsOption("K10", R.string.manage_calling_accounts_system, SettingsEditor.ACCOUNTS, false, R.string.calling_accounts),
    SettingsOption("K02", R.string.add_country_code_automatically, SettingsEditor.CALLS, true, R.string.phone_number_formatting),
    SettingsOption("K03", R.string.phone_number_region, SettingsEditor.CALLS, true, R.string.phone_number_formatting),
    SettingsOption("K06", R.string.proximity_sensor, SettingsEditor.CALLS, true, R.string.compatibility),
    SettingsOption("K07", R.string.pocket_mode_prevention, SettingsEditor.CALLS, false, R.string.call_behavior),
    SettingsOption("K04", R.string.android_auto_integration, SettingsEditor.CALLS, false, R.string.call_behavior),
    SettingsOption("K12", R.string.open_in_dot, SettingsEditor.CALLS, false, R.string.call_behavior),
    SettingsOption("K11", R.string.call_message_channel, SettingsEditor.CALLS, false, R.string.call_behavior),
    SettingsOption("K05", R.string.quick_responses, SettingsEditor.QUICK, false, R.string.quick_responses),
    SettingsOption("K09", R.string.fullscreen_calls, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("H10", R.string.show_all_recent_calls, SettingsEditor.HOME, false, R.string.home_and_gestures),
    SettingsOption("K08", R.string.floating_ongoing_call, SettingsEditor.CALL_UI, false, R.string.call_screen),
    SettingsOption("V01", R.string.ringtone_settings, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V02", R.string.dialpad_tones, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V03", R.string.vibrate_on_answer, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V04", R.string.vibrate_on_hang_up, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V05", R.string.tap_haptics, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V06", R.string.haptics_custom, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V07", R.string.scroll_haptics, SettingsEditor.SOUND, false, R.string.sound_and_vibration),
    SettingsOption("V08", R.string.haptic_interval, SettingsEditor.SCROLL, true, R.string.settings_fine_tuning),
    SettingsOption("V09", R.string.haptic_strength, SettingsEditor.SCROLL, true, R.string.settings_fine_tuning),
    SettingsOption("A06", R.string.liquid_glass_dialpad, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("A08", R.string.blur_effect_dialpad, SettingsEditor.LEGACY, true, R.string.keypad),
    SettingsOption("A09", R.string.keypad_effect_strength, SettingsEditor.INTERFACE, false, R.string.interface_settings),
    SettingsOption("T01", R.string.managing_contact_sources, SettingsEditor.SOURCES, false, R.string.managing_contact_sources),
    SettingsOption("T02", R.string.sort_by, SettingsEditor.CONTACTS, false, R.string.contacts_and_data),
    SettingsOption("T03", R.string.name_format, SettingsEditor.CONTACTS, false, R.string.contacts_and_data),
    SettingsOption("T09", R.string.high_res_call_photos_title, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T11", R.string.whatsapp_avatar_sync_title, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T18", R.string.contact_photos_only_same_or_larger, SettingsEditor.LEGACY, false, R.string.contact_photos_title),
    SettingsOption("T19", R.string.contact_photos_auto_policy, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T17", R.string.contact_photos_auto_update, SettingsEditor.LEGACY, false, R.string.contact_photos_title),
    SettingsOption("T12", R.string.whatsapp_avatar_normalize_numbers, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T13", R.string.whatsapp_avatar_existing_photo, SettingsEditor.LEGACY, false, R.string.contact_photos_title),
    SettingsOption("T16", R.string.contact_photos_business, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T14", R.string.contact_photos_gravatar, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T15", R.string.contact_photos_account, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("T10", R.string.contact_photo_scan_action, SettingsEditor.CONTACTS, false, R.string.contact_photos_title),
    SettingsOption("B01", R.string.log_visibility, SettingsEditor.BLOCKED, false, R.string.manage_blocked),
    SettingsOption("B02", R.string.authentication_method, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("B03", R.string.lock_app_on_open, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("B04", R.string.lock_timeout, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("B05", R.string.lock_call_actions, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("B06", R.string.lock_scope, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("B07", R.string.settings_protected_numbers, SettingsEditor.AUTH, false, R.string.authentication),
    SettingsOption("O01", R.string.settings_reporting, SettingsEditor.REPORTING, false, R.string.privacy_and_blocking),
    SettingsOption("O02", R.string.diagnostic_log_level, SettingsEditor.DIAGNOSTICS, true, R.string.diagnostics),
    SettingsOption("F01", R.string.unlock_all_features, SettingsEditor.SUPPORT, false, R.string.continue_support),
    SettingsOption("T04", R.string.preferred_calling_account, SettingsEditor.CONTEXT, false, R.string.contact_action),
    SettingsOption("T05", R.string.contact_action, SettingsEditor.CONTEXT, false, R.string.contact_action),
    SettingsOption("T06", R.string.favorites, SettingsEditor.CONTEXT, false, R.string.contact_action),
    SettingsOption("T07", R.string.ringtone_settings, SettingsEditor.CONTEXT, false, R.string.contact_action),
    SettingsOption("T08", R.string.settings_call_default_background, SettingsEditor.CONTEXT, false, R.string.contact_action),
    SettingsOption("B08", R.string.unknown_caller_actions, SettingsEditor.LEGACY, true, R.string.call_settings),
)
