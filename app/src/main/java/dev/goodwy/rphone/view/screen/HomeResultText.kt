package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.view.components.CallLogVisual
import dev.goodwy.rphone.view.components.SamsungStyleCallStatusIcon
import dev.goodwy.rphone.view.components.callStatusLabel

/** Use actual text metrics, including Android's font scaling, rather than a device threshold. */
@Composable
internal fun HomeResultText(
    title: String,
    subtitle: String?,
    callVisual: CallLogVisual?,
    callTime: String?,
    contactNameSize: Int,
    metadataColor: Color?,
    expandedDetails: Boolean,
    modifier: Modifier = Modifier,
    titleStyleOverride: TextStyle? = null,
    timeColor: Color? = null,
    inlineGap: Dp = 12.dp,
) {
    val titleStyle = titleStyleOverride ?: MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = contactNameSize.sp,
        lineHeight = (contactNameSize + 7).sp,
    )
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val timeStyle = bodyStyle.copy(
        fontWeight = if (callVisual?.emphasizeTime == true) FontWeight.SemiBold else FontWeight.Normal,
    )
    val secondaryText = if (callVisual != null) {
        subtitle?.forceLtr() ?: callStatusLabel(callVisual.status)
    } else subtitle
    val secondaryColor = metadataColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val availableWidth = with(density) { maxWidth.roundToPx() }
        val timeWidth = callTime?.let {
            measurer.measure(it, timeStyle, softWrap = false, maxLines = 1).size.width
        } ?: 0
        val textWidth = measurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width
        val subtitleWidth = secondaryText?.let {
            measurer.measure(it, bodyStyle, softWrap = false, maxLines = 1).size.width +
                if (callVisual == null) 0 else with(density) { 23.dp.roundToPx() }
        } ?: 0
        val inlineWidth = availableWidth - timeWidth - with(density) { inlineGap.roundToPx() }
        val stackTime = expandedDetails && callTime != null && maxOf(textWidth, subtitleWidth) > inlineWidth
        if (stackTime) {
            Column(Modifier.fillMaxWidth()) {
                HomeResultPrimaryText(title, titleStyle, secondaryText, bodyStyle, callVisual, secondaryColor, wrap = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = requireNotNull(callTime), style = timeStyle, color = timeColor ?: secondaryColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HomeResultPrimaryText(
                    title, titleStyle, secondaryText, bodyStyle, callVisual, secondaryColor,
                    wrap = expandedDetails && callTime == null && maxOf(textWidth, subtitleWidth) > inlineWidth,
                    modifier = Modifier.weight(1f).padding(end = inlineGap),
                )
                if (callTime != null) Text(text = callTime, style = timeStyle, color = timeColor ?: secondaryColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeResultPrimaryText(
    title: String,
    titleStyle: TextStyle,
    subtitle: String?,
    bodyStyle: TextStyle,
    callVisual: CallLogVisual?,
    metadataColor: Color,
    wrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val maxLines = if (wrap) Int.MAX_VALUE else 1
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (callVisual != null) SamsungStyleCallStatusIcon(
                    status = callVisual.status, modifier = Modifier.padding(top = 2.dp).size(17.dp),
                )
                Text(
                    text = subtitle, style = bodyStyle, color = metadataColor,
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
