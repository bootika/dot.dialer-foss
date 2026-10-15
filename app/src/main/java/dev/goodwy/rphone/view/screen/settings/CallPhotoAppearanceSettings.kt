package dev.goodwy.rphone.view.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SmallCallPhotoPreference

@Composable
internal fun CallPhotoAppearanceSettings(
    prefs: PreferenceManager, appearance: String, choices: List<Pair<String, String>>, onAppearanceChange: (String) -> Unit,
) {
    DotSettingsCard {
        DotSettingsStringChoiceRow(
            title = stringResource(R.string.call_appearance),
            value = choices.firstOrNull { it.second == appearance }?.first
                ?: stringResource(R.string.call_appearance_circular_avatar),
            choices = choices, selectedValue = appearance, onValueChange = onAppearanceChange,
        )
        DotSettingsDivider()
        SettingsPreferenceSwitch(prefs, SmallCallPhotoPreference.KEY, SmallCallPhotoPreference.DEFAULT,
            R.string.small_call_photos_in_circle, stringResource(R.string.small_call_photos_in_circle_summary))
    }
}
