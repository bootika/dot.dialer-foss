package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.telecom.Call
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ACTION_VIEW_CONTACT
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.view.components.CallingAccountIdentityIcon
import io.github.bootika.dotdialer.core.license.PlanFeature
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun SimRedialActions(call: Call, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accounts = getCallingAccounts(context).filter { it.isSim }
    if (accounts.size < 2) return
    val redial = koinInject<SimRedialCoordinator>()
    val busy by redial.busy.collectAsStateWithLifecycle()
    val enabled = !busy && redial.canSwitch(call)
    accounts.forEach { account ->
        val selected = account.handle == call.details?.accountHandle
        ListItem(
            headlineContent = { Text(account.label, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(stringResource(if (selected) R.string.sim_in_use else R.string.end_and_redial)) },
            leadingContent = {
                if (selected) Icon(DotDialerHandsetIcon, null, tint = MaterialTheme.colorScheme.primary)
                else CallingAccountIdentityIcon(account, MaterialTheme.colorScheme.onSurface, Modifier.size(24.dp))
            },
            modifier = Modifier.fillMaxWidth().then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))).alpha(if (selected || enabled) 1f else .45f)
                .clickable(enabled = !selected && enabled) { if (redial.request(call, account.handle)) onDismiss() },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
    Text(stringResource(if (enabled) R.string.end_and_redial_summary else R.string.sim_redial_unavailable),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallTimerReminderSheet(call: Call, onDismiss: () -> Unit) {
    val prefs = koinInject<PreferenceManager>()
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val allowed = remember(version) { prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY) }
    val reminders = koinInject<CallSessionReminders>()
    val entries by reminders.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.call_reminder_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            listOf(2, 5, 10, 15).forEach { minutes ->
                TextButton(modifier = Modifier.fillMaxWidth().alpha(if (allowed) 1f else .45f), onClick = {
                    if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) && reminders.schedule(call, minutes)) onDismiss()
                }) { Text(pluralStringResource(R.plurals.call_reminder_minutes, minutes, minutes)) }
            }
            if (entries.containsKey(call)) TextButton(onClick = { reminders.cancel(call); onDismiss() }) {
                Text(stringResource(R.string.cancel_call_reminder))
            }
        }
    }
}

internal fun openColleague(context: android.content.Context, id: String) {
    context.startActivity(Intent(context, MainActivity::class.java).setAction(ACTION_VIEW_CONTACT)
        .putExtra("contact_id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
}
