package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SmallCallPhotoPreference
import dev.goodwy.rphone.view.screen.CallAppearanceMenu

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
        DotSettingsDivider()
        SettingsPreferenceSwitch(prefs,
            dev.goodwy.rphone.controller.util.CallSimIdentityPreference.KEY,
            dev.goodwy.rphone.controller.util.CallSimIdentityPreference.DEFAULT,
            R.string.call_screen_always_show_sim, stringResource(R.string.call_screen_always_show_sim_summary))
    }
    CallAvatarGesturePreview(appearance)
}

@Composable
private fun CallAvatarGesturePreview(appearance: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var previewAppearance by rememberSaveable(appearance) { mutableStateOf(appearance) }
    var previewDroplets by rememberSaveable { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    val colors = MaterialTheme.colorScheme
    val fullPhoto = previewAppearance == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
    DotSettingsCard {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(width = if (fullPhoto) 112.dp else 72.dp, height = 72.dp)
                    .background(colors.primaryContainer, if (fullPhoto) RoundedCornerShape(16.dp) else CircleShape)
                    .onGloballyPositioned { anchor = it.boundsInWindow().bottomCenter }
                    .combinedClickable(onClick = { expanded = true }, onLongClick = { expanded = true }),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.avatar_preview_initials), style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
                if (previewDroplets) {
                    Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(12.dp)
                        .background(colors.primary.copy(alpha = 0.35f), CircleShape))
                    Box(Modifier.align(Alignment.BottomStart).padding(5.dp).size(8.dp)
                        .background(colors.primary.copy(alpha = 0.25f), CircleShape))
                }
            }
            Text(stringResource(R.string.feature_tip_avatar_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.feature_tip_avatar_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.feature_tip_try)) }
            CallAppearanceMenu(expanded, previewAppearance, previewDroplets, { previewDroplets = it }, anchor,
                onDismiss = { expanded = false },
                onSelect = { previewAppearance = it; expanded = false })
        }
    }
}
