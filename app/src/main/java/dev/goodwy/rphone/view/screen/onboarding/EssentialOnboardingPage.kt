package dev.goodwy.rphone.view.screen.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R

@Composable
internal fun EssentialOnboardingPage(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val introduction = stringResource(
        if (BuildConfig.FLAVOR == "gplay") R.string.onboarding_essential_body_gplay else R.string.onboarding_essential_body,
        stringResource(R.string.privacy_and_blocking),
        stringResource(R.string.settings_reporting),
    )
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 580.dp
            Column(Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp, Alignment.CenterVertically),
                ) {
                    OnboardingPhoneArt(keypad = true, size = if (compact) 66.dp else 112.dp)
                    Text("dot.dialer", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Text(stringResource(R.string.onboarding_essential_title),
                        Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Surface(shape = RoundedCornerShape(22.dp), color = colors.primaryContainer.copy(alpha = 0.48f)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(introduction.substringBefore("\n\n"), style = MaterialTheme.typography.bodyMedium)
                            val reporting = introduction.substringAfter("\n\n", "")
                            if (reporting.isNotBlank()) {
                                HorizontalDivider(color = colors.outlineVariant)
                                Text(reporting, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Button(onClick = onNext, shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).heightIn(min = 56.dp)
                        .testTag("plan-guide-next")) {
                    Text(stringResource(R.string.setup_call_style_continue))
                }
            }
        }
    }
}

@Composable
internal fun OnboardingPhoneArt(keypad: Boolean, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = this.size.minDimension
            val mint = colors.primary.copy(alpha = 0.14f)
            drawCircle(mint, unit * 0.45f)
            val phoneTop = Offset(unit * 0.29f, unit * 0.15f)
            val phoneSize = Size(unit * 0.43f, unit * 0.68f)
            val corner = CornerRadius(unit * 0.10f)
            rotate(-12f) {
                drawRoundRect(colors.surface, phoneTop, phoneSize, corner)
                drawRoundRect(colors.primary, phoneTop, phoneSize, corner, style = Stroke(unit * 0.025f))
                drawLine(mint, Offset(unit * 0.44f, unit * 0.22f), Offset(unit * 0.56f, unit * 0.22f),
                    unit * 0.025f, StrokeCap.Round)
                if (keypad) {
                    repeat(9) { index ->
                        drawCircle(colors.primary.copy(alpha = 0.56f), unit * 0.028f,
                            Offset(unit * (0.4f + index % 3 * 0.1f), unit * (0.37f + index / 3 * 0.1f)))
                    }
                } else {
                    drawCircle(colors.primary.copy(alpha = 0.40f), unit * 0.12f, style = Stroke(unit * 0.023f))
                    drawCircle(colors.primary, unit * 0.062f)
                }
            }
            val spark = Offset(unit * 0.83f, unit * 0.21f)
            val arm = unit * 0.042f
            drawLine(colors.primary, spark - Offset(arm, 0f), spark + Offset(arm, 0f), unit * 0.018f, StrokeCap.Round)
            drawLine(colors.primary, spark - Offset(0f, arm), spark + Offset(0f, arm), unit * 0.018f, StrokeCap.Round)
            drawCircle(colors.primary.copy(alpha = 0.42f), unit * 0.024f, Offset(unit * 0.17f, unit * 0.37f))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = size * 0.08f, bottom = size * 0.09f)
                .size(size * 0.29f).background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Call, null, Modifier.size(size * 0.16f), tint = colors.onPrimary)
        }
    }
}
