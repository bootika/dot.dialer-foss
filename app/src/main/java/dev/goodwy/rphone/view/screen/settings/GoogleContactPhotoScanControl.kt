package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.GoogleContactPhotoScan
import dev.goodwy.rphone.controller.util.PhotoScanStatus
import dev.goodwy.rphone.controller.util.PhotoScanState
import dev.goodwy.rphone.controller.util.PhotoScanFailureKind
import org.koin.compose.koinInject

@Composable
internal fun GoogleContactPhotoScanControl(enabled: Boolean) {
    val scan = koinInject<GoogleContactPhotoScan>()
    val state by scan.state.collectAsState()
    PhotoScanContent(state, enabled, scan::start, scan::cancel, scan::retryFailed)
}

@Composable
internal fun PhotoScanContent(state: PhotoScanState, enabled: Boolean, onStart: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().settingsSearchTarget(stringResource(R.string.contact_photo_scan_action)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.contact_photo_scan_supporting), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.running) {
            if (state.total > 0) LinearProgressIndicator(
                progress = { state.checked.toFloat() / state.total }, modifier = Modifier.fillMaxWidth(),
            ) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(
                if (state.stopping) R.string.contact_photo_scan_stopping else if (state.status == PhotoScanStatus.Preparing)
                    R.string.contact_photo_scan_preparing else R.string.contact_photo_scan_progress,
                state.checked, state.total,
            ), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.photo_scan_background), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCancel, enabled = !state.stopping, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(android.R.string.cancel)) }
        } else {
            val message = when (state.status) {
                PhotoScanStatus.Completed -> R.string.contact_photo_scan_complete
                PhotoScanStatus.Stopped -> R.string.contact_photo_scan_stopped
                PhotoScanStatus.ConsentRequired -> R.string.contact_photo_scan_consent
                PhotoScanStatus.PermissionMissing -> R.string.contact_photo_scan_permissions
                PhotoScanStatus.Failed -> state.lastFailure?.let(::photoScanFailureText) ?: R.string.contact_photo_scan_error
                else -> null
            }
            if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == PhotoScanStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Button(onClick = onStart, enabled = enabled) {
                Text(stringResource(if (state.canResume) R.string.photo_scan_resume else R.string.contact_photo_scan_action))
            }
            if (state.failed > 0 && !state.canResume) TextButton(onClick = onRetry, enabled = enabled, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                Text(stringResource(R.string.photo_scan_retry))
            }
        }
        if (state.checked > 0) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!state.running) Text(stringResource(R.string.photo_scan_checked, state.checked), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.photo_scan_summary, state.updated, state.skipped, state.failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.failureCounts.forEach { (kind, count) ->
                    val label = when (kind) {
                        PhotoScanFailureKind.Network -> R.string.photo_scan_network_count
                        PhotoScanFailureKind.RateLimit -> R.string.photo_scan_rate_limit_count
                        PhotoScanFailureKind.GoogleService -> R.string.photo_scan_google_service_count
                        else -> photoScanFailureText(kind)
                    }
                    Text(stringResource(R.string.photo_scan_failure_count, stringResource(label), count),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

internal fun photoScanFailureText(kind: PhotoScanFailureKind): Int = when (kind) {
    PhotoScanFailureKind.Network -> R.string.photo_scan_network
    PhotoScanFailureKind.RateLimit -> R.string.photo_scan_rate_limit
    PhotoScanFailureKind.GoogleService -> R.string.photo_scan_google_service
    PhotoScanFailureKind.InvalidImage -> R.string.photo_scan_invalid_image
    PhotoScanFailureKind.ContactChanged -> R.string.photo_scan_contact_changed
    PhotoScanFailureKind.Permission -> R.string.photo_scan_permission
    PhotoScanFailureKind.ContactWrite -> R.string.photo_scan_write
    PhotoScanFailureKind.Other -> R.string.photo_scan_other
}
