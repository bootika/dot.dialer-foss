package dev.goodwy.rphone.view.screen.settings.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.GITHUB_URL
import dev.goodwy.rphone.privacyPolicyUrl
import dev.goodwy.rphone.termsAndConditionsUrl
import androidx.compose.ui.platform.LocalConfiguration
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.auto.AndroidAutoIntegration

@Composable
internal fun createSettingsSearchEntries(
    context: Context,
    navigator: DestinationsNavigator,
    appVersion: String,
    storeName: String,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onStartUiTour: () -> Unit,
): List<SettingsSearchEntry> {
    val normal = stringResource(R.string.settings)
    val advanced = stringResource(R.string.advanced_settings)
    val language = LocalConfiguration.current.locales[0].language
    val entries = settingsOptions.filter { it.isAvailable(BuildConfig.FLAVOR, AndroidAutoIntegration.isAvailable()) }.map { option ->
        val category = stringResource(option.category)
        val terms = settingsSearchTerms(option) + settingsMenuAliases(option) + settingsLabelAliases(option.id)
        SettingsSearchEntry(
            headline = stringResource(option.label),
            supporting = settingsBreadcrumb(option),
            leadingIcon = Icons.Rounded.Tune,
            options = terms + category,
        ) { navigator.navigate(SettingsOptionScreenDestination(option.id)) }
    }
    fun action(label: Int, category: Int, action: () -> Unit): SettingsSearchEntry = SettingsSearchEntry(
        headline = context.getString(label), supporting = when (category) {
            R.string.contact_tools -> "$normal › ${context.getString(R.string.contacts)} › ${context.getString(R.string.manage_contacts)} › ${context.getString(category)}"
            R.string.advanced_settings -> advanced
            else -> "$normal › ${context.getString(category)}"
        },
        leadingIcon = Icons.Rounded.Tune, onClick = action,
    )
    return entries + listOf(
        action(R.string.advanced_settings, R.string.advanced_settings) { navigator.navigate(AdvancedSettingsScreenDestination) },
        action(R.string.standardize_phone_numbers, R.string.contact_tools) { navigator.navigate(ContactManagementScreenDestination(toolsOnly = true)) },
        action(R.string.merging_contacts, R.string.contact_tools) { navigator.navigate(ContactMergeDuplicatesScreenDestination) },
        action(R.string.unmerging_contacts, R.string.contact_tools) { navigator.navigate(ContactUnmergeDuplicatesScreenDestination) },
        action(R.string.create_backup, R.string.backup_and_restore, onCreateBackup),
        action(R.string.restore_backup, R.string.backup_and_restore, onRestoreBackup),
        action(R.string.ui_tour_title, R.string.help, onStartUiTour),
        action(R.string.diagnostics, R.string.advanced_settings) { navigator.navigate(DiagnosticsScreenDestination) },
        action(if (BuildConfig.FLAVOR == "foss") R.string.continue_support else R.string.plans_title, R.string.help) { navigator.navigate(DonateScreenDestination) },
        action(R.string.about, R.string.help) { navigator.navigate(AboutAppScreenDestination) },
        action(R.string.contributors, R.string.help) { navigator.navigate(ContributorsScreenDestination) },
        action(R.string.source_code, R.string.help) { openLink(context, GITHUB_URL) },
        action(R.string.privacy_policy, R.string.help) { openLink(context, privacyPolicyUrl(language)) },
        action(R.string.terms_and_conditions, R.string.help) { openLink(context, termsAndConditionsUrl(language)) },
    )
}

internal fun DestinationsNavigator.openSettingsEditor(editor: SettingsEditor) {
    when (editor) {
        SettingsEditor.ACCESSIBILITY -> navigate(InterfaceScreenDestination(accessibility = true))
        SettingsEditor.ADVANCED -> navigate(AdvancedSettingsScreenDestination)
        SettingsEditor.REPORTING -> navigate(ReportingSettingsScreenDestination)
        SettingsEditor.INTERFACE -> navigate(InterfaceScreenDestination())
        SettingsEditor.AVATARS -> navigate(AvatarsPreferenceScreenDestination())
        SettingsEditor.FONT -> navigate(AdvancedSettingsScreenDestination)
        SettingsEditor.HOME -> navigate(HomeGesturesScreenDestination)
        SettingsEditor.HISTORY -> navigate(HomeGesturesScreenDestination)
        SettingsEditor.CALL_UI -> navigate(CallerUIScreenDestination())
        SettingsEditor.CALL_EFFECTS -> navigate(CallerUIScreenDestination(advanced = true))
        SettingsEditor.ACCOUNTS -> navigate(CallAccountsScreenDestination)
        SettingsEditor.NUMBERS -> navigate(CallSettingScreenDestination(section = "numbers"))
        SettingsEditor.DEVICE -> navigate(CallSettingScreenDestination(section = "device"))
        SettingsEditor.QUICK -> navigate(QuickResponsesScreenDestination)
        SettingsEditor.CALLS -> navigate(CallSettingScreenDestination())
        SettingsEditor.SOUND -> navigate(SoundVibrationScreenDestination())
        SettingsEditor.SCROLL -> navigate(SoundVibrationScreenDestination(advanced = true))
        SettingsEditor.FRAME -> navigate(AvatarsPreferenceScreenDestination())
        SettingsEditor.KEYPAD -> navigate(InterfaceScreenDestination())
        SettingsEditor.SOURCES -> navigate(ContactVisibilityScreenDestination)
        SettingsEditor.CONTACTS -> navigate(ContactManagementScreenDestination())
        SettingsEditor.BLOCKED -> navigate(BlockedNumbersScreenDestination)
        SettingsEditor.AUTH -> navigate(BiometricScreenDestination())
        SettingsEditor.CALL_AUTH -> navigate(BiometricScreenDestination())
        SettingsEditor.DIAGNOSTICS -> navigate(DiagnosticsScreenDestination)
        SettingsEditor.SUPPORT -> navigate(DonateScreenDestination)
        SettingsEditor.CONTEXT, SettingsEditor.LEGACY -> error("Not a global settings editor")
    }
}

/** Real choices only: old hex/font/radius/legacy toggles are intentionally not searchable. */
@Composable
private fun settingsSearchTerms(option: SettingsOption): List<String> = when (option.id) {
    "O01" -> listOf(R.string.diagnostic_automatic, R.string.diagnostic_on_demand)
    "I02" -> listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
    "I03" -> listOf(R.string.accent_color)
    "I10" -> listOf(R.string.ignore_system_font_scale_description)
    "I07" -> listOf(R.string.expanded_list_details_description)
    "C05" -> listOf(R.string.incoming_call_ui_default_swipe, R.string.incoming_call_ui_tap_buttons,
        R.string.incoming_call_ui_samsung_drag, R.string.incoming_call_ui_horizontal_slider,
        R.string.incoming_call_ui_horizontal_swipe, R.string.incoming_call_ui_vertical_swipe)
    "C06" -> listOf(R.string.incoming_answer_placement_left_top, R.string.incoming_answer_placement_right_bottom)
    "C01" -> listOf(R.string.call_appearance_circular_avatar, R.string.call_appearance_full_photo)
    "K04" -> listOf(R.string.android_auto, R.string.android_auto_integration_subtitle)
    "K03" -> listOf(R.string.automatic_selected_sim)
    "B02" -> listOf(R.string.pin, R.string.password, R.string.system_biometrics, R.string.remove_biometric)
    "V05", "V06" -> listOf(R.string.haptics_soft, R.string.haptics_strong, R.string.haptics_custom)
    "H01", "H02", "H03" -> listOf(R.string.no_action, R.string.call, R.string.sms, R.string.contact_action, R.string.copy_action)
    else -> emptyList()
}.map { stringResource(it) } + when (option.id) {
    "H01", "H02", "H03" -> listOf("WhatsApp")
    "C02" -> listOf("frost", "frosted")
    "C03", "C04" -> listOf("droplets", "particles")
    "K02", "K03" -> listOf("country", "region")
    else -> emptyList()
}


@Composable
internal fun settingsBreadcrumb(option: SettingsOption): String {
    val root = when (option.editor) {
        SettingsEditor.INTERFACE, SettingsEditor.AVATARS, SettingsEditor.KEYPAD, SettingsEditor.FRAME -> R.string.interface_settings
        SettingsEditor.ACCESSIBILITY -> R.string.accessibility
        SettingsEditor.HOME, SettingsEditor.HISTORY -> R.string.home_and_gestures
        SettingsEditor.CALLS, SettingsEditor.CALL_UI, SettingsEditor.ACCOUNTS, SettingsEditor.QUICK -> R.string.calls
        SettingsEditor.CONTACTS, SettingsEditor.SOURCES -> R.string.contacts
        SettingsEditor.SOUND -> R.string.sound_and_vibration
        SettingsEditor.AUTH, SettingsEditor.CALL_AUTH, SettingsEditor.BLOCKED, SettingsEditor.REPORTING -> R.string.privacy_and_blocking
        SettingsEditor.SUPPORT -> R.string.help
        else -> R.string.advanced_settings
    }
    val sections = mutableListOf(stringResource(R.string.settings), stringResource(root))
    val category = stringResource(option.category)
    if (category != sections.last()) sections += category
    if (option.editor == SettingsEditor.SCROLL) sections += stringResource(R.string.settings_scroll_calibration)
    return sections.joinToString(" › ")
}

@Composable
private fun settingsMenuAliases(option: SettingsOption): List<String> = buildList {
    if (option.id.startsWith("K") || option.id.startsWith("C") || option.id == "H10") add(stringResource(R.string.settings_alias_call_settings))
    if (option.id.startsWith("T")) {
        add(stringResource(R.string.settings_alias_manage_contacts))
        add(stringResource(R.string.settings_alias_contact_tools))
    }
    if (option.id.startsWith("B")) add(stringResource(R.string.settings_alias_authentication))
    if (option.id == "F01") add(stringResource(R.string.settings_alias_continue_support))
    if (option.id.startsWith("H")) add(stringResource(R.string.settings_alias_home_and_gestures))
    if (option.id.startsWith("I") || option.id == "A06" || option.id == "A09") add(stringResource(R.string.settings_alias_interface_settings))
    if (option.id.startsWith("B")) add(stringResource(R.string.settings_alias_privacy_and_blocking))
    if (option.id in listOf("H09", "V07", "V08", "V09")) add(stringResource(R.string.settings_alias_history_and_feedback))
}

@Composable
private fun settingsLabelAliases(id: String): List<String> = when (id) {
    "I05" -> listOf(stringResource(R.string.settings_alias_avatar_size))
    "I07" -> listOf(stringResource(R.string.settings_alias_expanded_list_details))
    "I09" -> listOf(stringResource(R.string.settings_alias_subtle_animations))
    "H05" -> listOf(stringResource(R.string.settings_alias_clear_search_after_call))
    "H06" -> listOf(stringResource(R.string.settings_alias_search_repeat_contacts))
    "C05" -> listOf(stringResource(R.string.settings_alias_incoming_call_ui))
    "C07" -> listOf(stringResource(R.string.settings_alias_settings_call_default_background))
    "V03" -> listOf(stringResource(R.string.settings_alias_vibrate_on_answer))
    "B02" -> listOf(stringResource(R.string.settings_alias_authentication_method))
    "B05" -> listOf(stringResource(R.string.settings_alias_lock_call_actions))
    "B06" -> listOf(stringResource(R.string.settings_alias_lock_scope), stringResource(R.string.settings_alias_blacklist),
        stringResource(R.string.settings_alias_whitelist), stringResource(R.string.blacklist), stringResource(R.string.whitelist))
    "B07" -> listOf(stringResource(R.string.settings_alias_lock_scope))
    "V01" -> listOf(stringResource(R.string.settings_alias_ringtone_settings))
    "T01" -> listOf(stringResource(R.string.settings_alias_managing_contact_sources))
    "V05", "V06" -> listOf(stringResource(R.string.settings_alias_tap_haptics))
    "O01" -> listOf(stringResource(R.string.diagnostic_reporting_mode), stringResource(R.string.product_reporting_title))
    else -> emptyList()
}
