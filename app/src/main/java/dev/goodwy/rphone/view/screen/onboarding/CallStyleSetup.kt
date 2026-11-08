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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("dot.dialer", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.setup_call_style_title), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.setup_call_style_subtitle), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                CallStyleCard(false, !swipe, !saving, onSelect)
                CallStyleCard(true, swipe, !saving, onSelect)
                Text(stringResource(R.string.setup_call_style_later), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                if (failed) Text(stringResource(R.string.setup_call_style_save_failed),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("call-style-save-error"))
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("call-style-saving"))
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onBack, modifier = Modifier.size(56.dp).testTag("plan-guide-back"),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("plan-guide-next")) {
                    Text(stringResource(R.string.setup_call_style_continue), modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CallStyleCard(swipe: Boolean, chosen: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = { onSelect(swipe) }, enabled = enabled, shape = RoundedCornerShape(26.dp),
        color = if (chosen) colors.primaryContainer else colors.surfaceBright,
        border = BorderStroke(2.dp, if (chosen) colors.primary else colors.surfaceBright),
        modifier = Modifier.fillMaxWidth().testTag(if (swipe) "call-style-swipe" else "call-style-tap")
            .semantics { selected = chosen; role = Role.Button }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) colors.primary else colors.surfaceContainerHigh) {
                    Icon(if (swipe) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.TouchApp,
                        null, Modifier.padding(10.dp).size(24.dp), tint = if (chosen) colors.onPrimary else colors.onSurface)
                }
                if (chosen) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = colors.primary)
                    Text(stringResource(R.string.setup_call_style_selected), style = MaterialTheme.typography.labelMedium,
                        color = colors.primary)
                }
            }
            Text(stringResource(if (swipe) R.string.setup_call_style_swipe else R.string.setup_call_style_tap),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (swipe) R.string.setup_call_style_swipe_body else R.string.setup_call_style_tap_body),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CallStyleMapping(0, if (swipe) R.string.contact_action else R.string.call,
                    stringResource(R.string.setup_call_style_tap_gesture))
                CallStyleMapping(1, if (swipe) R.string.call else R.string.sms,
                    stringResource(R.string.setup_call_style_right_gesture))
                CallStyleMapping(2, R.string.setup_call_style_whatsapp, stringResource(R.string.setup_call_style_left_gesture))
            }
        }
    }
}

@Composable
private fun CallStyleMapping(direction: Int, action: Int, gesture: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(when (direction) { 0 -> Icons.Rounded.TouchApp; 1 -> Icons.AutoMirrored.Rounded.ArrowForward;
            else -> Icons.AutoMirrored.Rounded.ArrowBack }, gesture, Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(action), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
