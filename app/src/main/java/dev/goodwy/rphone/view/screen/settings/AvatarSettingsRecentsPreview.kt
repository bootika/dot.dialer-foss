package dev.goodwy.rphone.view.screen.settings

import android.provider.CallLog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.expandedListDetails
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance
import dev.goodwy.rphone.view.components.contactAvatarSizeDp
import dev.goodwy.rphone.view.components.resolveCallLogVisual
import dev.goodwy.rphone.view.components.rillAvatarAppearance
import dev.goodwy.rphone.view.screen.HomeResultRow
import dev.goodwy.rphone.view.screen.HomeSectionHeader

/** Local example through the production Recents renderer; never reads call/contact providers. */
@Composable
internal fun AvatarSettingsRecentsPreview(prefs: PreferenceManager) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val appearance = remember(version) { prefs.rillAvatarAppearance() }
    val avatarSize = remember(version) { prefs.contactAvatarSizeDp() }
    val nameSize = remember(version) {
        prefs.getInt(PreferenceManager.KEY_CONTACT_NAME_SIZE_SP, 18).coerceIn(16, 22)
    }
    val expanded = remember(version) { prefs.expandedListDetails() }
    val context = LocalContext.current
    val photoUri = "android.resource://${context.packageName}/${R.drawable.avatar}"
    val name = "Laura Pop"
    val number = "+40700000101"
    val time = "10:24"
    CompositionLocalProvider(LocalRillAvatarAppearance provides appearance) {
        Column(Modifier.testTag("avatar-recents-preview")) {
            HomeSectionHeader(stringResource(R.string.recents))
            // Present text, not inert Call/Contact actions, to accessibility services.
            Box(Modifier.testTag("avatar-preview-row-0").clearAndSetSemantics {
                text = AnnotatedString("$name, $number, $time")
            }) {
                HomeResultRow(
                    title = name,
                    subtitle = number,
                    photoUri = photoUri,
                    phoneNumber = number,
                    callVisual = resolveCallLogVisual(
                        CallLog.Calls.INCOMING_TYPE,
                        durationSeconds = 84L, isBlocked = false,
                    ),
                    callTime = time,
                    swipeEnabled = false,
                    swipeRightAction = PreferenceManager.SWIPE_ACTION_MESSAGE,
                    swipeLeftAction = PreferenceManager.SWIPE_ACTION_WHATSAPP,
                    haptics = false, hapticsStrength = "light", hapticsIntensity = 0.5f,
                    onAction = {}, onAvatarClick = {}, onClick = {},
                    isLastInSection = true,
                    contactNameSize = nameSize, avatarSize = avatarSize,
                    expandedListDetails = expanded,
                )
            }
        }
    }
}
