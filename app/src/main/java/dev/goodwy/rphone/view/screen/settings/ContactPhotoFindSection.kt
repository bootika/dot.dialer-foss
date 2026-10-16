package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*

@Composable
internal fun ContactPhotoFindSection(settings: ContactOptimizationSettings, googleReady: Boolean,
    busy: Boolean, activeSource: String?, progress: ContactPhotoScanProgress?, results: Int,
    googleAuthorization: (@Composable () -> Unit)? = null, onFind: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.contact_photos_find_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.contact_photos_find_summary), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (settings.enabled) FindCard("whatsapp", Icons.Rounded.AccountCircle,
            stringResource(R.string.whatsapp_avatar_sync_title), stringResource(R.string.plan_whatsapp_manual_summary),
            stringResource(R.string.contact_photos_find_action), busy, settings.disclosed, activeSource, progress, results, onFind)
        if (settings.normalize) FindCard("numbers", Icons.Rounded.FormatListNumbered,
            stringResource(R.string.contact_photos_find_numbers), stringResource(R.string.contact_photos_numbers_scan_summary),
            stringResource(R.string.contact_photos_check_action), busy, true, activeSource, progress, results, onFind)
        if (settings.gravatar) FindCard("gravatar", Icons.Rounded.AccountCircle,
            stringResource(R.string.contact_photos_gravatar), stringResource(R.string.contact_photos_gravatar_summary),
            stringResource(R.string.contact_photos_find_action), busy, true, activeSource, progress, results, onFind)
        if (settings.business) FindCard("business", Icons.Rounded.Business,
            stringResource(R.string.contact_photos_business), stringResource(R.string.contact_photos_business_summary),
            stringResource(R.string.contact_photos_find_action), busy, true, activeSource, progress, results, onFind)
        if (dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay") FindCard("google", Icons.Rounded.CloudDownload,
            stringResource(R.string.contact_photos_google_find_title), stringResource(R.string.contact_photos_google_source_summary),
            stringResource(R.string.contact_photos_find_action), busy, settings.google && googleReady, activeSource, progress,
            results, onFind, if (!settings.google) stringResource(R.string.contact_photos_google_disabled)
                else if (!googleReady) stringResource(R.string.contact_photos_connect_google) else null, googleAuthorization)
    }
}

@Composable
private fun FindCard(source: String, icon: ImageVector, title: String, description: String, action: String,
    busy: Boolean, allowed: Boolean, activeSource: String?, progress: ContactPhotoScanProgress?, results: Int,
    onFind: (String) -> Unit, unavailable: String? = null, authorization: (@Composable () -> Unit)? = null) {
    val active = activeSource == source
    val ownProgress = progress?.takeIf { it.source == source }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    Surface(Modifier.fillMaxWidth().testTag("contactFindCard-" + source).bringIntoViewRequester(requester),
        shape = RoundedCornerShape(22.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            authorization?.invoke()
            if (active || ownProgress != null) {
                FindProgress(source, if (active && ownProgress?.running != true) null else ownProgress, results, active)
            }
            if (unavailable != null) Text(unavailable, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!active) FilledTonalButton(enabled = !busy && allowed, onClick = { onFind(source) },
                modifier = Modifier.align(Alignment.End).testTag("contactFindAction-" + source)
                    .semantics { contentDescription = title + ": " + action }) { Text(action) }
        }
    }
}

@Composable
private fun FindProgress(source: String, progress: ContactPhotoScanProgress?, results: Int, active: Boolean) {
    val phase = progress?.phase ?: ContactPhotoScanPhase.Preparing
    val status = when (phase) {
        ContactPhotoScanPhase.Preparing -> R.string.contact_photos_scan_preparing
        ContactPhotoScanPhase.Searching -> R.string.contact_photos_searching
        ContactPhotoScanPhase.Complete -> R.string.contact_photos_scan_complete
        ContactPhotoScanPhase.Stopped -> R.string.contact_photos_stopped
        ContactPhotoScanPhase.Failed -> R.string.contact_photos_network_error
    }
    Column(Modifier.fillMaxWidth().testTag("contactFindProgress-" + source).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(status), style = MaterialTheme.typography.labelLarge,
            color = when {
                phase == ContactPhotoScanPhase.Failed -> MaterialTheme.colorScheme.error
                active -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.primary
            })
        val total = progress?.total
        val checked = progress?.checked ?: 0
        if (active) {
            if (total != null && total > 0) LinearProgressIndicator(progress = {
                (checked.toFloat() / total).coerceIn(0f,1f)
            }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (total != null) Text(stringResource(R.string.contact_photos_scan_count, checked, total),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.contact_photos_scan_results,results), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
