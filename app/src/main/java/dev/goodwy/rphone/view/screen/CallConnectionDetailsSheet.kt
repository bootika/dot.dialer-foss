package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallConnectionDetailsSheet(
    info: CallConnectionInfo?,
    onDismiss: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val maximumHeight = with(LocalDensity.current) { (windowHeightPx * .8f).toDp() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = maximumHeight)
                .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.call_connection_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            extraContent()
            if (info == null) Text(stringResource(R.string.call_connection_not_reported)) else {
            ConnectionDetailRow(
                icon = info.transport.icon(),
                label = stringResource(R.string.call_transport),
                value = info.transport.label(),
            )
            ConnectionDetailRow(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.call_voice_quality),
                value = when (info.quality) {
                    VoiceQualityTier.HdPlus -> stringResource(R.string.call_voice_quality_hd_plus)
                    VoiceQualityTier.Hd -> stringResource(R.string.call_voice_quality_hd)
                    null -> stringResource(
                        if (info.codecLabel == null) {
                            R.string.call_connection_not_reported
                        } else {
                            R.string.call_voice_quality_standard
                        },
                    )
                },
            )
            HorizontalDivider()
            ConnectionTextDetail(
                label = stringResource(R.string.call_codec),
                value = info.codecLabel ?: stringResource(R.string.call_connection_not_reported),
            )
            info.bandwidthKhz?.let { bandwidth ->
                ConnectionTextDetail(
                    label = stringResource(R.string.call_bandwidth),
                    value = "${formatMetric(bandwidth)} kHz",
                )
            }
            info.bitrateKbps?.let { bitrate ->
                ConnectionTextDetail(
                    label = stringResource(R.string.call_bitrate),
                    value = "${formatMetric(bitrate)} kbps",
                )
            }
            }
        }
    }
}

@Composable
private fun ConnectionDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        ConnectionTextDetail(label, value)
    }
}

@Composable
private fun ConnectionTextDetail(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatMetric(value: Float): String = if (value % 1f == 0f) {
    value.toInt().toString()
} else {
    value.toString()
}
