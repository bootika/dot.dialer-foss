package dev.goodwy.rphone.view.screen

import android.telecom.PhoneAccount
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import dev.goodwy.rphone.R
import kotlin.math.ceil

internal data class CallConnectionStatusGeometry(
    val edgeWidthDp: Int,
    val qualityWidthDp: Int,
    val cellHeightDp: Int,
    val gapDp: Int,
    val widthDp: Int,
    val rows: Int,
) {
    val statusHeightDp: Int get() = cellHeightDp * rows + (rows - 1) * 6 + 8
    val touchHeightDp: Int get() = maxOf(CALL_CONNECTION_TOUCH_HEIGHT_DP, statusHeightDp)
}

/** Fit actual localized text, not a font-scale threshold. Never shrink accessibility text. */
internal fun callConnectionStatusGeometry(
    availableWidthDp: Int,
    accountContentWidthDp: Int?,
    transportContentWidthDp: Int,
    qualityContentWidthDp: Int,
    textHeightDp: Int,
): CallConnectionStatusGeometry {
    val available = availableWidthDp.coerceAtLeast(9)
    val capacity = available - 8
    val edge = maxOf(94, accountContentWidthDp ?: 0, transportContentWidthDp).coerceAtMost(capacity)
    val quality = maxOf(94, qualityContentWidthDp).coerceAtMost(capacity)
    val height = maxOf(30, textHeightDp + 6)
    val cells = if (accountContentWidthDp != null) 3 else 2
    for (gap in listOf(8, 4, 2)) {
        val width = 8 + edge * (cells - 1) + quality + gap * (cells - 1)
        if (width <= available) return CallConnectionStatusGeometry(edge, quality, height, gap, width, 1)
    }
    val pairWidth = edge + quality + 8
    return if (accountContentWidthDp != null && pairWidth <= capacity) {
        CallConnectionStatusGeometry(edge, quality, height, 8, pairWidth + 8, 2)
    } else {
        CallConnectionStatusGeometry(edge, quality, height, 8, maxOf(edge, quality) + 8, cells)
    }
}

@Composable
internal fun measuredCallConnectionStatusGeometry(
    availableWidth: Dp,
    account: PhoneAccount?,
    transport: CallTransportKind,
): CallConnectionStatusGeometry {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = callConnectionMetadataTextStyle()
    val qualityLabels = listOf(
        stringResource(R.string.call_voice_quality_hd_plus),
        stringResource(R.string.call_voice_quality_hd),
        stringResource(R.string.call_voice_quality_unreported_short),
    )
    val qualitySizes = qualityLabels.map { measurer.measure(AnnotatedString(it), style, softWrap = false).size }
    val connectingSize = measurer.measure(
        AnnotatedString(callConnectingLabel()), style, softWrap = false,
    ).size
    val transportSize = measurer.measure(
        AnnotatedString(stringResource(if (transport == CallTransportKind.Cellular) R.string.call_transport_cellular else R.string.call_transport_wifi)),
        style, softWrap = false,
    ).size
    val accountSize = account?.let {
        measurer.measure(AnnotatedString(it.label?.toString().orEmpty()), style, softWrap = false).size
    }
    fun dpCeil(pixels: Int): Int = ceil(with(density) { pixels.toDp().value }).toInt()
    return callConnectionStatusGeometry(
        availableWidthDp = availableWidth.value.toInt(),
        accountContentWidthDp = accountSize?.let { dpCeil(it.width) + 26 },
        transportContentWidthDp = dpCeil(transportSize.width) + 26,
        qualityContentWidthDp = maxOf(
            dpCeil(qualitySizes.maxOf { it.width }) + CALL_CONNECTION_QUALITY_ICON_DP +
                CALL_CONNECTION_QUALITY_ICON_GAP_DP + 2 * CALL_CONNECTION_QUALITY_HORIZONTAL_PADDING_DP + 2,
            dpCeil(connectingSize.width) + 2 * CALL_CONNECTION_QUALITY_HORIZONTAL_PADDING_DP + 2,
        ), // Both phases reserve the same measured width, including localized Connecting text.
        textHeightDp = dpCeil(maxOf(qualitySizes.maxOf { it.height }, connectingSize.height, transportSize.height, accountSize?.height ?: 0)),
    )
}
