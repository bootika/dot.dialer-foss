package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import org.koin.core.context.GlobalContext

internal enum class GuideSourceState(val label: Int) {
    Configured(R.string.plan_source_configured), Off(R.string.off),
    Setup(R.string.plan_source_setup), Unavailable(R.string.plan_source_unavailable)
}

/** Local configuration only; live Google authorization is validated by the existing editor/fetch flow. */
internal fun guideSourceStates(settings: ContactOptimizationSettings, accountSelected: Boolean,
    contactsAccess: Boolean, notificationSupported: Boolean, notificationAccess: Boolean,
    googleAvailable: Boolean, googleEnabled: Boolean): List<GuideSourceState> {
    fun state(enabled: Boolean, consent: Boolean, available: Boolean = true): GuideSourceState = when {
        !available -> GuideSourceState.Unavailable
        !enabled -> GuideSourceState.Off
        !accountSelected || !contactsAccess || !consent -> GuideSourceState.Setup
        else -> GuideSourceState.Configured
    }
    return listOf(
        state(settings.enabled, settings.disclosed && notificationAccess, notificationSupported),
        state(settings.google, settings.googleAuthorized && googleEnabled, googleAvailable),
        state(settings.gravatar, settings.gravatarDisclosed),
        state(settings.business, settings.businessDisclosed))
}

@Composable
internal fun PlanGuideOptimizerContent(account: String?, settings: ContactOptimizationSettings,
    refreshKey: Int, googleEnabled: Boolean, onNormalize: (Boolean) -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    val states = remember(account, settings, refreshKey, googleEnabled) {
        guideSourceStates(settings, !account.isNullOrBlank(), ContactOptimizationAccess.contactsGranted(context),
            ContactOptimizationAccess.supported(context), ContactOptimizationAccess.granted(context),
            GlobalContext.getOrNull()?.getOrNull<GoogleContactsAuthorization>()?.isAvailable == true, googleEnabled)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(account ?: stringResource(R.string.plan_choose_account), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("plan-guide-account"))
        }
        Surface(onClick = onMore, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier.fillMaxWidth().testTag("plan-guide-sources")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideCardHeading(Icons.Rounded.PhotoLibrary, stringResource(R.string.plan_photo_sources), true)
                GuideSourceRow(Icons.AutoMirrored.Rounded.Chat, stringResource(R.string.whatsapp_avatar_sync_title), states[0], "whatsapp")
                GuideSourceRow(Icons.Rounded.Cloud, stringResource(R.string.plan_source_google), states[1], "google")
                GuideSourceRow(Icons.Rounded.AlternateEmail, stringResource(R.string.contact_photos_gravatar), states[2], "gravatar")
                GuideSourceRow(Icons.Rounded.Business, stringResource(R.string.contact_photos_business), states[3], "company")
            }
        }
        DotSettingsCard {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                GuideCardHeading(Icons.Rounded.Dialpad, stringResource(R.string.plan_guide_phone_numbers))
            }
            DotSettingsSwitchRow(stringResource(R.string.standardize_phone_numbers),
                supporting = stringResource(R.string.plan_guide_phone_format),
                checked = account != null && settings.normalize, enabled = account != null,
                onCheckedChange = onNormalize)
        }
    }
}

@Composable
private fun GuideCardHeading(icon: ImageVector, title: String, navigation: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
            Icon(icon, null, Modifier.padding(8.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (navigation) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuideSourceRow(icon: ImageVector, title: String, state: GuideSourceState, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Surface(shape = RoundedCornerShape(8.dp), color = if (state == GuideSourceState.Configured)
                MaterialTheme.colorScheme.primary.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(stringResource(state.label), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp).testTag("plan-source-$tag"))
            }
        }
    }
}
