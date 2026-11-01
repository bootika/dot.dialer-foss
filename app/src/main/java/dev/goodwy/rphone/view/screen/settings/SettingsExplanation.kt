package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

/** Help never requests a permission or changes a preference. Shared by settings and plan guides. */
@Composable
internal fun SettingsHelpText(resource: Int, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable(resource) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=4.dp)) {
        TextButton(onClick={expanded=!expanded}, contentPadding=PaddingValues(vertical=8.dp)) {
            Text(stringResource(R.string.guide_more))
        }
        if(expanded) Text(stringResource(resource) + if(resource==R.string.guide_auth) "\n\n"+stringResource(R.string.guide_lock) else if(resource==R.string.guide_recording) "\n\n"+stringResource(R.string.capture_preconnect_help) else "", style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(bottom=12.dp))
    }
}

internal data class SettingExplanation(val label: Int,val text: Int,val detailed: Boolean = false)
internal val LocalSettingsExplanations = staticCompositionLocalOf { true }
internal val settingExplanations = listOf(
    SettingExplanation(R.string.app_language,R.string.guide_language),
    SettingExplanation(R.string.high_contrast_text,R.string.guide_contrast),
    SettingExplanation(R.string.phone_number_region,R.string.guide_region),
    SettingExplanation(R.string.haptic_interval,R.string.guide_interval),
    SettingExplanation(R.string.high_res_call_photos_title,R.string.guide_google,true),
    SettingExplanation(R.string.contact_photos_account,R.string.guide_optimizer,true),
    SettingExplanation(R.string.contact_photos_title,R.string.guide_optimizer,true),
    SettingExplanation(R.string.contact_photos_gravatar,R.string.contact_photos_gravatar_disclosure,true),
    SettingExplanation(R.string.contact_photos_business,R.string.contact_photos_business_disclosure,true),
    SettingExplanation(R.string.whatsapp_avatar_normalize_numbers,R.string.guide_format,true),
    SettingExplanation(R.string.managing_contact_sources,R.string.guide_contacts,true),
    SettingExplanation(R.string.sort_by,R.string.guide_names),
    SettingExplanation(R.string.name_format,R.string.guide_names),
    SettingExplanation(R.string.show_recents_favorites,R.string.guide_history),
    SettingExplanation(R.string.period,R.string.guide_history),
    SettingExplanation(R.string.recent_history_maximum,R.string.guide_history),
    SettingExplanation(R.string.clear_search_after_call,R.string.guide_search_clear),
    SettingExplanation(R.string.tap_a_recent,R.string.guide_gesture),
    SettingExplanation(R.string.swipe_right_action,R.string.guide_delete,true),
    SettingExplanation(R.string.swipe_left_action,R.string.guide_delete,true),
    SettingExplanation(R.string.ignore_system_font_scale,R.string.guide_accessibility),
    SettingExplanation(R.string.interface_scale,R.string.guide_scale),
    SettingExplanation(R.string.contact_name_size,R.string.guide_scale),
    SettingExplanation(R.string.subtle_animations,R.string.guide_visual),
    SettingExplanation(R.string.default_sim,R.string.guide_sim),
    SettingExplanation(R.string.voice_search_title,R.string.guide_voice,true),
    SettingExplanation(R.string.authentication_method,R.string.guide_auth,true),
    SettingExplanation(R.string.authentication,R.string.guide_auth,true),
    SettingExplanation(R.string.standardize_phone_numbers,R.string.guide_format,true),
    SettingExplanation(R.string.lock_app_on_open,R.string.guide_auth,true),
    SettingExplanation(R.string.lock_call_actions,R.string.guide_lock,true),
    SettingExplanation(R.string.lock_scope,R.string.guide_lock,true),
    SettingExplanation(R.string.recordings_auto_transcribe,R.string.guide_recording,true),
    SettingExplanation(R.string.recordings_auto_translate,R.string.guide_recording,true),
    SettingExplanation(R.string.capture_output_language,R.string.guide_recording,true),
    SettingExplanation(R.string.settings_call_default_background,R.string.guide_background),
    SettingExplanation(R.string.keypad_effect_strength,R.string.guide_visual)
)

@Composable
internal fun SettingsExplanation(title: String) {
    if (!LocalSettingsExplanations.current) return
    val resources=androidx.compose.ui.platform.LocalResources.current
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales.toLanguageTags()
    val index=remember(resources,locale) { settingExplanations.associateBy { resources.getString(it.label) } }
    val explanation=index[title] ?: return
    if(explanation.detailed) SettingsHelpText(explanation.text)
    else Text(stringResource(explanation.text),modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp),
        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
