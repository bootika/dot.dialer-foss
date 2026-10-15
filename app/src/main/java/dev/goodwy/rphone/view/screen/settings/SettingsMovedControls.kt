package dev.goodwy.rphone.view.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RememberMe
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Dialpad
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors

@Composable
internal fun AvatarListSizeControl(prefs: PreferenceManager) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    var avatarSize by remember(version) {
        mutableStateOf(prefs.getString(PreferenceManager.KEY_CONTACT_AVATAR_SIZE, PreferenceManager.DEFAULT_CONTACT_AVATAR_SIZE)
            ?: PreferenceManager.DEFAULT_CONTACT_AVATAR_SIZE)
    }
    val avatarSizeChoices = listOf(
        stringResource(R.string.avatar_size_small) to PreferenceManager.CONTACT_AVATAR_SIZE_SMALL,
        stringResource(R.string.avatar_size_medium) to PreferenceManager.CONTACT_AVATAR_SIZE_MEDIUM,
        stringResource(R.string.avatar_size_large) to PreferenceManager.CONTACT_AVATAR_SIZE_LARGE,
    )
    DotSettingsStringChoiceRow(
                        title = stringResource(R.string.avatar_size),
                        value = avatarSizeChoices.firstOrNull { it.second == avatarSize }?.first
                            ?: stringResource(R.string.avatar_size_medium),
                        choices = avatarSizeChoices,
                        selectedValue = avatarSize,
                        onValueChange = {
                            avatarSize = it
                            prefs.setString(PreferenceManager.KEY_CONTACT_AVATAR_SIZE, it)
                        },
                    )
}

/** Existing preferences, grouped by the surface they affect, not by rendering technology. */
@Composable
internal fun EffectPreferenceRow(prefs: PreferenceManager, key: String, default: Boolean, label: Int) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    var checked by remember(version) { mutableStateOf(prefs.getBoolean(key, default)) }
    RillSwitchListItem(
        headline = stringResource(label), checked = checked,
        leadingIcon = if (key == PreferenceManager.KEY_LG_DIALPAD || key == PreferenceManager.KEY_BLUR_DIALPAD) Icons.Rounded.Dialpad else DotDialerHangupIcon,
        iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
        iconBgContainerColor = MaterialTheme.colorScheme.primaryContainer,
        onCheckedChange = { checked = it; prefs.setBoolean(key, it) },
    )
}
