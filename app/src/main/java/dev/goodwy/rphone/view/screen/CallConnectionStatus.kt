package dev.goodwy.rphone.view.screen

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.Connection
import android.telecom.PhoneAccount
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.ripple
import dev.goodwy.rphone.view.components.callPressFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WifiCalling3
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.CallingAccountIdentityContent
import dev.goodwy.rphone.view.theme.DotDialerTypography
import dev.goodwy.rphone.view.theme.DotJade
import dev.goodwy.rphone.view.theme.DotCallQualityCream
import dev.goodwy.rphone.view.theme.DotInk
import kotlin.math.ceil

internal enum class CallTransportKind {
    Cellular,
    WifiCalling,
}

internal enum class VoiceQualityTier {
    Hd,
    HdPlus,
}

internal data class CallConnectionInfo(
    val transport: CallTransportKind,
    val quality: VoiceQualityTier?,
    val codecLabel: String?,
    val bandwidthKhz: Float?,
    val bitrateKbps: Float?,
)

@SuppressLint("NewApi", "InlinedApi") // Every newer field is guarded by sdkInt below; the parameter keeps this testable.
internal fun resolveCallConnectionInfo(
    details: Call.Details,
    sdkInt: Int = Build.VERSION.SDK_INT,
): CallConnectionInfo {
    val extras = details.extras
    val codec = if (sdkInt >= Build.VERSION_CODES.R) readAudioCodec(extras) else null
    val bandwidth = if (sdkInt >= Build.VERSION_CODES.S) readPositiveFloat(
        extras,
        Connection.EXTRA_AUDIO_CODEC_BANDWIDTH_KHZ,
    ) else null
    val bitrate = if (sdkInt >= Build.VERSION_CODES.S) readPositiveFloat(
        extras,
        Connection.EXTRA_AUDIO_CODEC_BITRATE_KBPS,
    ) else null
    return CallConnectionInfo(
        transport = if (details.hasProperty(Call.Details.PROPERTY_WIFI)) {
            CallTransportKind.WifiCalling
        } else {
            CallTransportKind.Cellular
        },
        quality = classifyVoiceQuality(
            codec = codec,
            platformReportsHighDefinition = details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO),
        ),
        codecLabel = codec?.let(::audioCodecLabel),
        bandwidthKhz = bandwidth,
        bitrateKbps = bitrate,
    )
}

internal fun classifyVoiceQuality(
    codec: Int?,
    platformReportsHighDefinition: Boolean,
): VoiceQualityTier? = when (codec) {
    Connection.AUDIO_CODEC_EVS_SWB,
    Connection.AUDIO_CODEC_EVS_FB,
    -> VoiceQualityTier.HdPlus

    Connection.AUDIO_CODEC_AMR_WB,
    Connection.AUDIO_CODEC_EVS_WB,
    Connection.AUDIO_CODEC_EVRC_WB,
    Connection.AUDIO_CODEC_EVRC_NW,
    Connection.AUDIO_CODEC_G722,
    -> VoiceQualityTier.Hd

    else -> if (platformReportsHighDefinition) VoiceQualityTier.Hd else null
}

@RequiresApi(Build.VERSION_CODES.R)
private fun readAudioCodec(extras: Bundle?): Int? {
    if (extras?.containsKey(Connection.EXTRA_AUDIO_CODEC) != true) return null
    return extras.getInt(Connection.EXTRA_AUDIO_CODEC)
        .takeUnless { it == Connection.AUDIO_CODEC_NONE }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun readPositiveFloat(extras: Bundle?, key: String): Float? {
    if (extras?.containsKey(key) != true) return null
    return extras.getFloat(key).takeIf { it > 0f }
}

internal fun audioCodecLabel(codec: Int): String? = when (codec) {
    Connection.AUDIO_CODEC_AMR -> "AMR-NB"
    Connection.AUDIO_CODEC_AMR_WB -> "AMR-WB"
    Connection.AUDIO_CODEC_QCELP13K -> "QCELP-13K"
    Connection.AUDIO_CODEC_EVRC -> "EVRC"
    Connection.AUDIO_CODEC_EVRC_B -> "EVRC-B"
    Connection.AUDIO_CODEC_EVRC_WB -> "EVRC-WB"
    Connection.AUDIO_CODEC_EVRC_NW -> "EVRC-NW"
    Connection.AUDIO_CODEC_GSM_EFR -> "GSM-EFR"
    Connection.AUDIO_CODEC_GSM_FR -> "GSM-FR"
    Connection.AUDIO_CODEC_GSM_HR -> "GSM-HR"
    Connection.AUDIO_CODEC_G711U -> "G.711 μ-law"
    Connection.AUDIO_CODEC_G723 -> "G.723"
    Connection.AUDIO_CODEC_G711A -> "G.711 A-law"
    Connection.AUDIO_CODEC_G722 -> "G.722"
    Connection.AUDIO_CODEC_G711AB -> "G.711"
    Connection.AUDIO_CODEC_G729 -> "G.729"
    Connection.AUDIO_CODEC_EVS_NB -> "EVS-NB"
    Connection.AUDIO_CODEC_EVS_WB -> "EVS-WB"
    Connection.AUDIO_CODEC_EVS_SWB -> "EVS-SWB"
    Connection.AUDIO_CODEC_EVS_FB -> "EVS-FB"
    else -> null
}

internal fun effectiveCallFontScale(androidFontScale: Float, labelSizeSp: Float): Float =
    androidFontScale * (labelSizeSp / DotDialerTypography.labelLarge.fontSize.value)

@Composable
internal fun callMetadataTextScale(): Float = effectiveCallFontScale(
    androidFontScale = LocalDensity.current.fontScale,
    labelSizeSp = MaterialTheme.typography.labelLarge.fontSize.value,
)

/** One typography source for SIM, voice quality and transport, including measurement. */
@Composable
internal fun callConnectionMetadataTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)

internal const val CALL_CONNECTION_TOUCH_HEIGHT_DP = 48
internal const val CALL_CONNECTION_SHIELD_LIGHT_ALPHA = 0.50f
internal const val CALL_CONNECTION_SHIELD_DARK_ALPHA = 0.20f
internal fun callConnectionShieldColor(darkTheme: Boolean): Color = Color.White.copy(
    alpha = if (darkTheme) CALL_CONNECTION_SHIELD_DARK_ALPHA else CALL_CONNECTION_SHIELD_LIGHT_ALPHA,
)
internal const val CALL_CONNECTION_STATUS_HEIGHT_DP = 38
internal const val CALL_CONNECTION_STATUS_INSET_DP = 4
/**
 * The two edge cells deliberately share one width. Besides keeping every quality label stable,
 * this gives the SIM identity and the longest quality label the same visual breathing room at
 * the outside edges of the shield.
 */
internal const val CALL_CONNECTION_CELL_WIDTH_DP = 94
internal const val CALL_CONNECTION_QUALITY_PILL_WIDTH_DP = CALL_CONNECTION_CELL_WIDTH_DP
internal const val CALL_CONNECTION_QUALITY_PILL_HEIGHT_DP =
    CALL_CONNECTION_STATUS_HEIGHT_DP - (CALL_CONNECTION_STATUS_INSET_DP * 2)
internal const val CALL_CONNECTION_ACCOUNT_CELL_WIDTH_DP = CALL_CONNECTION_CELL_WIDTH_DP
internal const val CALL_CONNECTION_TRANSPORT_CELL_WIDTH_DP = CALL_CONNECTION_CELL_WIDTH_DP
internal const val CALL_CONNECTION_CELL_GAP_DP = 8
internal const val CALL_CONNECTION_QUALITY_TEST_TAG = "callQualityPill"
internal const val CALL_CONNECTION_QUALITY_HORIZONTAL_PADDING_DP = 8
internal const val CALL_CONNECTION_QUALITY_ICON_DP = 15
internal const val CALL_CONNECTION_QUALITY_ICON_GAP_DP = 3
internal const val CALL_CONNECTION_QUALITY_ALPHA = 0.23f
internal const val CALL_CONNECTION_HD_ALPHA = 0.50f
internal fun callVoiceQualityHighlightColor(quality: VoiceQualityTier?): Color? = when (quality) {
    VoiceQualityTier.HdPlus -> DotJade.copy(alpha = CALL_CONNECTION_QUALITY_ALPHA)
    VoiceQualityTier.Hd -> DotCallQualityCream.copy(alpha = CALL_CONNECTION_HD_ALPHA)
    null -> null
}

internal fun callConnectionStatusWidthDp(hasAccount: Boolean): Int {
    val gapCount = if (hasAccount) 2 else 1
    return (CALL_CONNECTION_STATUS_INSET_DP * 2) +
        CALL_CONNECTION_TRANSPORT_CELL_WIDTH_DP +
        CALL_CONNECTION_QUALITY_PILL_WIDTH_DP +
        (if (hasAccount) CALL_CONNECTION_ACCOUNT_CELL_WIDTH_DP else 0) +
        (CALL_CONNECTION_CELL_GAP_DP * gapCount)
}

@Composable
internal fun CallConnectionStatusRow(
    info: CallConnectionInfo,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    account: PhoneAccount? = null,
    timerGapDp: Int = 10,
    geometryOverride: CallConnectionStatusGeometry? = null,
    onTimerClick: (() -> Unit)? = null,
) {
    val fontScale = callMetadataTextScale()
    val tap = rememberCallConnectionStatusFeedback(onClick)
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val geometry = geometryOverride ?: measuredCallConnectionStatusGeometry(maxWidth, account, info.transport)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(geometry.widthDp.dp)
                    .height(geometry.touchHeightDp.dp)
                    .clip(CircleShape)
                    .callPressFeedback(tap.pressed, fillAlpha = tap.fill.alpha)
                    .clickable(interactionSource = tap.interaction, indication = ripple(), onClick = tap.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(geometry.statusHeightDp.dp)
                        .testTag("callConnectionShield"),
                    shape = CircleShape,
                    color = tap.fill,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    if (geometry.rows > 1) {
                        AccessibleConnectionStatusContent(info, account, geometry)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(geometry.statusHeightDp.dp)
                                .padding(CALL_CONNECTION_STATUS_INSET_DP.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(geometry.gapDp.dp),
                        ) {
                            if (account != null) {
                                ConnectionStatusCell(widthDp = geometry.edgeWidthDp, heightDp = geometry.cellHeightDp, testTag = "callAccountCell") {
                                    CallingAccountIdentityContent(
                                        account = account,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = callConnectionMetadataTextStyle(),
                                    )
                                }
                            }

                            ConnectionStatusCell(
                                widthDp = geometry.qualityWidthDp,
                                heightDp = geometry.cellHeightDp,
                                highlightColor = callVoiceQualityHighlightColor(info.quality),
                                testTag = CALL_CONNECTION_QUALITY_TEST_TAG,
                            ) {
                                VoiceQualityBadge(info.quality)
                            }

                            ConnectionStatusCell(widthDp = geometry.edgeWidthDp, heightDp = geometry.cellHeightDp, testTag = "callTransportCell") {
                                TransportBadge(info.transport)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(timerGapDp.dp))
            Text(
                text = duration,
                modifier = Modifier.width(callDurationSlotWidthDp(duration, fontScale).dp).testTag("callPrimaryTimer")
                    .then(if (onTimerClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.call_reminder_title), onClick = onTimerClick) else Modifier),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TransportBadge(transport: CallTransportKind) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = transport.icon(),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = transport.label(),
            style = callConnectionMetadataTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ConnectionStatusCell(
    widthDp: Int,
    heightDp: Int = CALL_CONNECTION_QUALITY_PILL_HEIGHT_DP,
    highlightColor: Color? = null,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(CircleShape)
            .then(
                if (highlightColor != null) {
                    Modifier.background(highlightColor)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun VoiceQualityBadge(quality: VoiceQualityTier?) {
    // Cream is a light surface in either theme; its foreground must stay dark.
    val foreground = if (quality == VoiceQualityTier.Hd) DotInk else MaterialTheme.colorScheme.onSurface
    val label = stringResource(
        when (quality) {
            VoiceQualityTier.HdPlus -> R.string.call_voice_quality_hd_plus
            VoiceQualityTier.Hd -> R.string.call_voice_quality_hd
            null -> R.string.call_voice_quality_unreported_short
        },
    )
    val style = callConnectionMetadataTextStyle()
    val measured = rememberTextMeasurer().measure(AnnotatedString(label), style, softWrap = false)
    // Row's intrinsic width can round down a fractional pixel and report text overflow.
    // Match the geometry's rounding reserve instead of relying on the intrinsic width.
    val labelWidthDp = ceil(with(LocalDensity.current) { measured.size.width.toDp().value }).toInt() + 2
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CALL_CONNECTION_QUALITY_HORIZONTAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            CALL_CONNECTION_QUALITY_ICON_GAP_DP.dp,
            Alignment.CenterHorizontally,
        ),
    ) {
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(CALL_CONNECTION_QUALITY_ICON_DP.dp).testTag("callQualityGlyph"),
            tint = foreground,
        )
        Text(
            text = label,
            style = style,
            modifier = Modifier.width(labelWidthDp.dp).testTag("callQualityLabel"),
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CallTransportKind.label(): String = stringResource(
    when (this) {
        CallTransportKind.Cellular -> R.string.call_transport_cellular
        CallTransportKind.WifiCalling -> R.string.call_transport_wifi
    },
)

internal fun CallTransportKind.icon(): ImageVector = when (this) {
    CallTransportKind.Cellular -> Icons.Rounded.SignalCellularAlt
    CallTransportKind.WifiCalling -> Icons.Rounded.WifiCalling3
}
