package dev.goodwy.rphone.view.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.launch

@Composable
internal fun CallStyleSetup(prefs: PreferenceManager, swipe: Boolean, onSelect: (Boolean) -> Unit,
    onBack: () -> Unit, onFinished: () -> Unit) {
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler { if (!saving) onBack() }
    CallStyleChoiceContent(swipe, saving, failed,
        onSelect = { onSelect(it); failed = false },
        onBack = { if (!saving) onBack() },
        onNext = {
            if (!saving) {
                saving = true
                failed = false
                scope.launch {
                    try {
                        prefs.saveInitialCallStyle(swipe)
                        onFinished()
                    } catch (_: java.io.IOException) {
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            }
        })
}

@Composable
internal fun CallStyleChoiceContent(swipe: Boolean, saving: Boolean = false, failed: Boolean = false,
    onSelect: (Boolean) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("call-style-setup"), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            val largeText = LocalDensity.current.fontScale >= 1.3f
            val compact = maxHeight < 600.dp || largeText
            val dense = maxHeight < 600.dp && largeText
            Column(Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).widthIn(max = 460.dp).fillMaxWidth().testTag("call-style-body")) {
                    val headerHeight = if (compact) 0.dp else maxHeight * 0.46f
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                        .padding(horizontal = if (dense) 12.dp else 16.dp,
                            vertical = if (dense) 4.dp else if (compact) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            if (dense) 4.dp else if (compact) 8.dp else 14.dp,
                            if (compact) Alignment.CenterVertically else Alignment.Top,
                        )) {
                        Column(Modifier.fillMaxWidth().heightIn(min = headerHeight)
                            .padding(bottom = if (compact) 0.dp else 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (dense) 3.dp else if (compact) 5.dp else 18.dp,
                                Alignment.CenterVertically)) {
                            OnboardingWordmark()
                            Text(stringResource(R.string.setup_call_style_title),
                                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { heading() })
                            OnboardingPhoneArt(keypad = false, size = if (dense) 34.dp else if (compact) 60.dp else 144.dp)
                        }
                        val cardPadding = if (dense) 6.dp else if (compact) 9.dp else 14.dp
                        val tileSize = if (dense) 34.dp else if (compact) 38.dp else 46.dp
                        CallStyleCard(false, !swipe, !saving, compact, cardPadding, tileSize, onSelect)
                        CallStyleCard(true, swipe, !saving, compact, cardPadding, tileSize, onSelect)
                        Text(stringResource(R.string.setup_call_style_later), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 16.dp))
                        if (failed) Text(stringResource(R.string.setup_call_style_save_failed),
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("call-style-save-error"))
                    }
                }
                val footer = Modifier.widthIn(max = 460.dp).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = if (dense) 6.dp else 8.dp, bottom = if (dense) 8.dp else 12.dp)
                val actionSize = if (dense) 52.dp else 56.dp
                if (saving) LinearProgressIndicator(footer.testTag("call-style-saving"))
                else Row(footer, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = onBack, modifier = Modifier.size(actionSize).testTag("plan-guide-back"),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                    Button(onClick = onNext, shape = RoundedCornerShape(percent = 100),
                        colors = onboardingPrimaryButtonColors(),
                        modifier = Modifier.weight(1f).heightIn(min = actionSize).testTag("plan-guide-next")) {
                        Text(stringResource(R.string.setup_call_style_continue), fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallStyleCard(
    swipe: Boolean, chosen: Boolean, enabled: Boolean, compact: Boolean,
    verticalPadding: Dp, tileSize: Dp, onSelect: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = { onSelect(swipe) }, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = if (chosen) androidx.compose.ui.graphics.lerp(colors.surface, colors.primary, 0.09f) else colors.surface,
        border = BorderStroke(1.dp, if (chosen) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(if (swipe) "call-style-swipe" else "call-style-tap")
            .semantics { selected = chosen; role = Role.Button }) {
        val minimumHeight = if (compact) 0.dp else if (swipe) 96.dp else 88.dp
        Row(Modifier.heightIn(min = minimumHeight).padding(horizontal = 12.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = 0.10f)) {
                Box(Modifier.size(tileSize), contentAlignment = Alignment.Center) {
                    if (swipe) Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = colors.primary)
                    else androidx.compose.foundation.Canvas(Modifier.size(23.dp)) {
                        drawCircle(colors.primary.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        drawCircle(colors.primary, radius = size.minDimension * 0.27f)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (swipe) R.string.setup_call_style_swipe else R.string.setup_call_style_tap),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val right = callStyleMapping(R.string.setup_call_style_direction_right,
                    if (swipe) R.string.setup_call_style_call_destination else R.string.sms)
                val left = callStyleMapping(R.string.setup_call_style_direction_left, R.string.setup_call_style_whatsapp)
                val mapping = if (swipe) {
                    callStyleMapping(R.string.setup_call_style_tap_gesture, R.string.setup_call_style_contact_destination) +
                        " · " + right + "\n" + left
                } else {
                    "$right · $left"
                }
                Text(mapping, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (chosen) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = colors.primary)
        }
    }
}
@Composable
private fun callStyleMapping(gesture: Int, action: Int): String =
    stringResource(R.string.feature_tip_gesture_action, stringResource(gesture), stringResource(action))
