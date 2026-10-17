package dev.goodwy.rphone.view.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bootika.dotdialer.core.license.PlanFeature
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallMessageChannel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.callMessageChoices
import dev.goodwy.rphone.controller.util.callMessageWhatsAppIntent
import dev.goodwy.rphone.controller.util.callMessageWhatsAppPackage

internal data class CallMessageRequest(val body: String?, val decline: Boolean)

/** Returns false only when the caller should offer SMS instead; never sends a hidden fallback. */
internal fun openCallMessageWhatsApp(
    context: Context, number: String, request: CallMessageRequest, onDecline: () -> Unit,
): Boolean {
    val intent = callMessageWhatsAppIntent(context, number, request.body) ?: return false
    if (request.decline) onDecline()
    return try {
        context.startActivity(intent)
        Toast.makeText(context, R.string.call_message_whatsapp_send_hint, Toast.LENGTH_LONG).show()
        true
    } catch (error: android.content.ActivityNotFoundException) {
        android.util.Log.w("CallScreen", "Unable to open WhatsApp: ${error.javaClass.simpleName}")
        false
    } catch (error: SecurityException) {
        android.util.Log.w("CallScreen", "Unable to open WhatsApp: ${error.javaClass.simpleName}")
        false
    }
}

@Composable
internal fun rememberCallMessageAction(
    prefs: PreferenceManager,
    number: String,
    onSmsQuickResponse: (String) -> Unit,
    onDecline: () -> Unit,
): (String?, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember(number) { mutableStateOf<CallMessageRequest?>(null) }
    var smsOnly by remember(number) { mutableStateOf(false) }
    fun sms(request: CallMessageRequest) {
        if (request.decline && request.body != null) onSmsQuickResponse(request.body)
        else openQuickResponseComposer(context, number, request.body.orEmpty())
    }
    fun whatsapp(request: CallMessageRequest) {
        if (!prefs.allowsFeature(PlanFeature.CALL_MESSAGE_CHANNEL)) {
            pending = request
            smsOnly = true
            return
        }
        var declineAttempted = false
        if (!openCallMessageWhatsApp(context, number, request) {
                declineAttempted = true
                onDecline()
            }) {
            // The app may have disappeared after selection; do not reject/send again on retry.
            pending = request.copy(decline = request.decline && !declineAttempted)
            smsOnly = true
        }
    }
    pending?.let { request ->
        CallMessageChannelDialog(
            whatsAppAvailable = !smsOnly && prefs.allowsFeature(PlanFeature.CALL_MESSAGE_CHANNEL) &&
                callMessageWhatsAppPackage(context) != null,
            onDismiss = { pending = null },
            onSelect = { channel ->
                pending = null
                if (channel == CallMessageChannel.SMS) sms(request) else whatsapp(request)
            },
        )
    }
    return { body, decline ->
        val request = CallMessageRequest(body, decline)
        smsOnly = callMessageWhatsAppPackage(context) == null
        when (CallMessageChannel.fromStored(prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0))) {
            CallMessageChannel.SMS -> sms(request)
            CallMessageChannel.WHATSAPP -> if (smsOnly) pending = request else whatsapp(request)
            CallMessageChannel.ASK -> pending = request
        }
    }
}

@Composable
internal fun CallMessageChannelDialog(
    whatsAppAvailable: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CallMessageChannel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.call_message_channel)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!whatsAppAvailable) Text(stringResource(R.string.call_message_whatsapp_unavailable),
                    style = MaterialTheme.typography.bodyMedium)
                callMessageChoices(whatsAppAvailable).forEach { channel ->
                    TextButton(onClick = { onSelect(channel) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(if (channel == CallMessageChannel.SMS) stringResource(R.string.sms) else "WhatsApp",
                                style = MaterialTheme.typography.titleMedium)
                            if (channel == CallMessageChannel.WHATSAPP) Text(
                                stringResource(R.string.call_message_whatsapp_send_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
