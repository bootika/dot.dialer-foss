package dev.goodwy.rphone.view.screen.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.DotJade

@Composable
internal fun EssentialOnboardingPage(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showDataDetails by rememberSaveable { mutableStateOf(false) }
    val reportingDetails = stringResource(
        if (BuildConfig.FLAVOR == "gplay") R.string.onboarding_essential_body_gplay else R.string.onboarding_essential_body,
        stringResource(R.string.privacy_and_blocking),
        stringResource(R.string.settings_reporting),
    ).substringAfter("\n\n")
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 580.dp
            val dense = LocalDensity.current.fontScale >= 1.3f
            Column(Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = if (dense) 12.dp else 16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().testTag("welcome-body")
                        .verticalScroll(rememberScrollState()).padding(vertical = if (dense) 4.dp else if (compact) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        if (dense) 4.dp else if (compact) 8.dp else 12.dp, Alignment.CenterVertically,
                    ),
                ) {
                    if (dense) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OnboardingPhoneArt(keypad = true, size = if (compact) 40.dp else 56.dp)
                            OnboardingWordmark()
                        }
                    } else {
                        OnboardingPhoneArt(keypad = true, size = if (compact) 50.dp else 112.dp)
                        OnboardingWordmark()
                    }
                    Text(stringResource(R.string.onboarding_essential_title),
                        Modifier.semantics { heading() },
                        style = if (compact || dense) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.welcome_privacy_summary),
                        style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerLowest,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(if (dense) 10.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 10.dp)) {
                            WelcomeInformationRow(Icons.Rounded.Call, R.string.welcome_phone, R.string.welcome_phone_summary, dense)
                            HorizontalDivider(color = colors.outlineVariant)
                            WelcomeInformationRow(
                                Icons.Rounded.Description, R.string.welcome_reporting, R.string.welcome_reporting_summary, dense,
                            )
                        }
                    }
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { showDataDetails = !showDataDetails },
                            modifier = Modifier.testTag("welcome-disclosure"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
                        ) {
                            Text(stringResource(R.string.welcome_data_details), textDecoration = TextDecoration.Underline)
                        }
                        if (showDataDetails) {
                            Text(reportingDetails, Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Button(onClick = onNext, shape = CircleShape, colors = onboardingPrimaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = if (dense) 6.dp else 8.dp, bottom = if (dense) 8.dp else 12.dp)
                        .heightIn(min = if (dense) 52.dp else 56.dp)
                        .testTag("plan-guide-next")) {
                    Text(stringResource(R.string.setup_call_style_continue), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WelcomeInformationRow(
    icon: ImageVector,
    title: Int,
    summary: Int,
    dense: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val tile: @Composable () -> Unit = {
        Box(
            Modifier.size(if (dense) 28.dp else 40.dp).background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (dense) 17.dp else 22.dp), tint = colors.primary)
        }
    }
    val heading: @Composable () -> Unit = {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    val description: @Composable () -> Unit = {
        Text(stringResource(summary),
            style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant)
    }
    if (dense) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                tile()
                heading()
            }
            description()
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tile()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                heading()
                description()
            }
        }
    }
}

@Composable
internal fun onboardingPrimaryButtonColors(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return if (colors.primary == DotJade && colors.background.luminance() > 0.5f) {
        ButtonDefaults.buttonColors(containerColor = Color(0xFF008057), contentColor = Color.White)
    } else {
        ButtonDefaults.buttonColors()
    }
}

@Composable
internal fun OnboardingWordmark() {
    Text("dot.dialer", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun OnboardingPhoneArt(keypad: Boolean, size: Dp, roleCheck: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = this.size.minDimension
            val mint = colors.primary.copy(alpha = 0.10f)
            drawCircle(mint, unit * 0.42f, Offset(unit * 0.5f, unit * 0.45f))
            drawOval(colors.primary.copy(alpha = 0.14f), Offset(unit * 0.3f, unit * 0.93f),
                Size(unit * 0.4f, unit * 0.035f))
            val phoneTop = Offset(unit * 0.29f, unit * 0.15f)
            val phoneSize = Size(unit * 0.43f, unit * 0.68f)
            val corner = CornerRadius(unit * 0.10f)
            rotate(-12f) {
                drawRoundRect(colors.surface, phoneTop, phoneSize, corner)
                drawRoundRect(colors.primary, phoneTop, phoneSize, corner, style = Stroke(unit * 0.025f))
                drawLine(colors.primary.copy(alpha = 0.7f), Offset(unit * 0.44f, unit * 0.22f), Offset(unit * 0.56f, unit * 0.22f),
                    unit * 0.025f, StrokeCap.Round)
                if (roleCheck) {
                    drawCircle(mint, unit * 0.12f)
                    val joint = Offset(unit * 0.48f, unit * 0.54f)
                    drawLine(colors.primary, Offset(unit * 0.44f, unit * 0.5f), joint, unit * 0.028f, StrokeCap.Round)
                    drawLine(colors.primary, joint, Offset(unit * 0.56f, unit * 0.45f), unit * 0.028f, StrokeCap.Round)
                } else if (keypad) {
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
            drawCircle(colors.primary.copy(alpha = 0.42f), unit * 0.019f, Offset(unit * 0.91f, unit * 0.43f))
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
