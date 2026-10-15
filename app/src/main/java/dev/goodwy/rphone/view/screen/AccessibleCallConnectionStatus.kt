package dev.goodwy.rphone.view.screen

import android.telecom.PhoneAccount
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.CallingAccountIdentityContent

/** Compact fallback only when measured content cannot fit on one row. */
@Composable
internal fun AccessibleConnectionStatusContent(
    info: CallConnectionInfo,
    account: PhoneAccount?,
    geometry: CallConnectionStatusGeometry,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (account != null) {
            ConnectionStatusCell(geometry.edgeWidthDp, geometry.cellHeightDp, testTag = "callAccountCell") {
                CallingAccountIdentityContent(account, textStyle = callConnectionMetadataTextStyle())
            }
        }
        val quality: @Composable () -> Unit = {
            ConnectionStatusCell(geometry.qualityWidthDp, geometry.cellHeightDp,
                highlightColor = callVoiceQualityHighlightColor(info.quality), testTag = CALL_CONNECTION_QUALITY_TEST_TAG) {
            VoiceQualityBadge(info.quality)
            }
        }
        val transport: @Composable () -> Unit = {
            ConnectionStatusCell(geometry.edgeWidthDp, geometry.cellHeightDp, testTag = "callTransportCell") {
                TransportBadge(info.transport)
            }
        }
        if (account != null && geometry.rows == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(geometry.gapDp.dp), verticalAlignment = Alignment.CenterVertically) {
                quality()
                transport()
            }
        } else {
            quality()
            transport()
        }
    }
}
