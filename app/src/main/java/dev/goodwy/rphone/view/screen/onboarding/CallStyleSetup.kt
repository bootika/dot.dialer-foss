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
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            val shortScreen = maxHeight < 580.dp
            Column(Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).widthIn(max = 460.dp).fillMaxWidth().testTag("call-style-body")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("dot.dialer", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.setup_call_style_title), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() })
                        OnboardingPhoneArt(keypad = false, size = if (shortScreen) 60.dp else 96.dp)
                    }
                    CallStyleCard(false, !swipe, !saving, onSelect)
                    CallStyleCard(true, swipe, !saving, onSelect)
                    Text(stringResource(R.string.setup_call_style_later), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    if (failed) Text(stringResource(R.string.setup_call_style_save_failed),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("call-style-save-error"))
                }
                val footer = Modifier.widthIn(max = 460.dp).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                if (saving) LinearProgressIndicator(footer.testTag("call-style-saving"))
                else Row(footer, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = onBack, modifier = Modifier.size(56.dp).testTag("plan-guide-back"),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                    Button(onClick = onNext, shape = RoundedCornerShape(percent = 100),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("plan-guide-next")) {
                        Text(stringResource(R.string.setup_call_style_continue), textAlign = TextAlign.Center)
                    }
                }
            }
    }
}
}

@Composable
private fun CallStyleCard(swipe: Boolean, chosen: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = { onSelect(swipe) }, enabled = enabled, shape = RoundedCornerShape(22.dp),
        color = if (chosen) colors.primaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (chosen) colors.primary else colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(if (swipe) "call-style-swipe" else "call-style-tap")
            .semantics { selected = chosen; role = Role.Button }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.primary.copy(alpha = 0.10f)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (swipe) Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = colors.primary)
                    else androidx.compose.foundation.Canvas(Modifier.size(23.dp)) {
                        drawCircle(colors.primary.copy(alpha = 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        drawCircle(colors.primary, radius = size.minDimension * 0.27f)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (swipe) R.string.setup_call_style_swipe else R.string.setup_call_style_tap),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (swipe) CallStyleMapping(0, R.string.contact_action, stringResource(R.string.setup_call_style_tap_gesture))
                    CallStyleMapping(1, if (swipe) R.string.call else R.string.sms, stringResource(R.string.setup_call_style_right_gesture))
                    CallStyleMapping(2, R.string.setup_call_style_whatsapp, stringResource(R.string.setup_call_style_left_gesture))
                }
            }
            if (chosen) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = colors.primary)
        }
    }
}
@Composable
private fun CallStyleMapping(direction: Int, action: Int, gesture: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(when (direction) { 0 -> Icons.Rounded.TouchApp; 1 -> Icons.AutoMirrored.Rounded.ArrowForward;
                else -> Icons.AutoMirrored.Rounded.ArrowBack }, gesture, Modifier.size(14.dp),
                tint = colors.onSurfaceVariant)
            Text(stringResource(action), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}
