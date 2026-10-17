package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import java.util.Locale

@Composable
internal fun SettingsPreferenceSwitch(
    prefs: PreferenceManager, key: String, default: Boolean, label: Int,
    supporting: String? = null, enabled: Boolean = true,
) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val checked = remember(version) { prefs.getBoolean(key, default) }
    val feature = dev.goodwy.rphone.controller.util.PlanPreferencePolicy.feature(key)
    val allowed = remember(version) { feature == null || prefs.allowsFeature(feature) }
    DotSettingsSwitchRow(stringResource(label), supporting, checked,
        { prefs.setBoolean(key, it) }, enabled = enabled || !allowed, contentActive = allowed)
}

@Composable
internal fun AdvancedCallVisualControls(prefs: PreferenceManager) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_FROSTED_CALL_BACKGROUND,
        PreferenceManager.DEFAULT_FROSTED_CALL_BACKGROUND, R.string.frosted_call_background,
        stringResource(R.string.frosted_call_background_supporting))
    DotSettingsDivider()
    val glass = remember(version) { prefs.getBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true) }
    SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_BLUR_CALL_SCREEN, true, R.string.blur_effect_call_screen,
        stringResource(R.string.settings_blur_dependency), enabled = !glass || android.os.Build.VERSION.SDK_INT < 31)
}

@Composable
internal fun AdvancedNumberControls(prefs: PreferenceManager) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val enabled = remember(version) { prefs.getBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
        PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION) }
    SettingsPreferenceSwitch(prefs, PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,
        PreferenceManager.DEFAULT_OUTGOING_NUMBER_NORMALIZATION, R.string.add_country_code_automatically,
        stringResource(R.string.add_country_code_automatically_subtitle))
    if (enabled) {
        val automatic = stringResource(R.string.automatic_selected_sim)
        val choices = remember(automatic) {
            listOf(automatic to "") + Locale.getISOCountries().map { iso ->
                Locale.Builder().setRegion(iso).build().displayCountry to iso
            }.filter { it.first.isNotBlank() }.distinctBy { it.second }
                .sortedBy { it.first.lowercase(Locale.getDefault()) }
        }
        val region = remember(version) { prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, "").orEmpty() }
        DotSettingsDivider()
        DotSettingsStringChoiceRow(stringResource(R.string.phone_number_region),
            choices.firstOrNull { it.second == region }?.first ?: automatic, choices, region,
            { prefs.setString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, it.takeIf(String::isNotBlank)) })
    }
}
