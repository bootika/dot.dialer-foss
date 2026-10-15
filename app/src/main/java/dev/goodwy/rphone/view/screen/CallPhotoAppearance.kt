package dev.goodwy.rphone.view.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.CallPhotoOrigins
import dev.goodwy.rphone.controller.util.CallPhotoDimensions
import dev.goodwy.rphone.controller.util.CallPhotoSizeResolver
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SmallCallPhotoPreference

@Composable
internal fun rememberCallScreenAppearance(prefs: PreferenceManager, version: Int): MutableState<String> {
    val state = remember { mutableStateOf(prefs.savedCallAppearance()) }
    LaunchedEffect(version) { state.value = prefs.savedCallAppearance() }
    return state
}

private fun PreferenceManager.savedCallAppearance() = getString(
    PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
) ?: PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE

internal fun useFullCallPhotoAppearance(style: String, smallPhotosInCircle: Boolean, dimensions: CallPhotoDimensions?): Boolean =
    style == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO && dimensions?.requiresCircle != true && !(smallPhotosInCircle && dimensions?.isSmall == true)

@Composable
internal fun rememberUseFullCallPhotoAppearance(
    style: String, prefs: PreferenceManager, settingsVersion: Int, photoUri: String?, photoRevision: Int?,
): Boolean {
    val context = LocalContext.current
    val originRevision by CallPhotoOrigins.revision.collectAsStateWithLifecycle()
    val resolver = remember(context) { CallPhotoSizeResolver(context) }
    val enabled = remember(settingsVersion) { prefs.getBoolean(SmallCallPhotoPreference.KEY, SmallCallPhotoPreference.DEFAULT) }
    // Keep a known measurement during same-URI refresh; a different identity/URI starts unknown.
    var dimensions by remember(photoUri) { mutableStateOf<CallPhotoDimensions?>(null) }
    LaunchedEffect(photoUri, photoRevision, originRevision, enabled, style) {
        if (style == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO) {
            dimensions = resolver.resolve(photoUri)
        }
    }
    return useFullCallPhotoAppearance(style, enabled, dimensions)
}
