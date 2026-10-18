package dev.goodwy.rphone.view.screen

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import io.github.bootika.dotdialer.core.license.PlanFeature
import org.koin.compose.koinInject

@Composable
internal fun InCallExtensionButton(call: Call, callState: Int, onDigit: (Char) -> Unit, onBusyChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsStateWithLifecycle()
    val number = call.details?.handle?.schemeSpecificPart.orEmpty()
    val extension = remember(number, revision, call.details?.accountHandle) {
        PhoneExtensions.find(prefs, number, resolveContactLookupRegion(context, call.details?.accountHandle))
    }
    if (extension == null) { LaunchedEffect(Unit) { onBusyChanged(false) }; return }
    val repository = koinInject<ICallRepository>()
    val session by repository.currentCallSession.collectAsStateWithLifecycle()
    val calls by repository.allCalls.collectAsStateWithLifecycle()
    val allowed = prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY)
    val scope = rememberCoroutineScope()
    val sender = remember(call) { ExtensionDtmfSender(scope) { target ->
        repository.currentCallSession.value?.call === target && repository.allCalls.value.any { it === target } &&
            prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY)
    } }
    val busy by sender.busy.collectAsStateWithLifecycle()
    LaunchedEffect(busy) { onBusyChanged(busy) }
    DisposableEffect(sender) { onDispose { sender.cancel() } }
    LaunchedEffect(callState, session?.call, calls, allowed) {
        if (callState != Call.STATE_ACTIVE || session?.call !== call || calls.none { it === call } || !allowed) sender.cancel()
    }
    val live = callState == Call.STATE_ACTIVE && !call.isTelecomConference() && call.parent == null && session?.call === call
    FilledTonalButton(onClick = {
        if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) && sender.send(call, extension.digits, onDigit)) onBusyChanged(true)
    }, enabled = !busy && (live || !allowed), modifier = Modifier.fillMaxWidth().alpha(if (allowed) 1f else .45f)) {
        Text(stringResource(R.string.send_phone_extension, extension.digits) + extension.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(end = 10.dp))
        Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
