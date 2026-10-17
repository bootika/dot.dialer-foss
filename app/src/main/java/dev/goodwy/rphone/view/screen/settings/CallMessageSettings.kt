package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.license.PlanFeature
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallMessageChannel
import dev.goodwy.rphone.controller.util.PreferenceManager

@Composable
internal fun CallMessageSettings(prefs: PreferenceManager) {
    val version by prefs.settingsChanged.collectAsStateWithLifecycle()
    val allowed = remember(version) { prefs.allowsFeature(PlanFeature.CALL_MESSAGE_CHANNEL) }
    if (!allowed) {
        DotSettingsNavigationRow(stringResource(R.string.call_message_channel),
            supporting = stringResource(R.string.plan_upgrade_to, AppPlan.PREMIUM.displayName), contentActive = false,
            onClick = { prefs.requireFeature(PlanFeature.CALL_MESSAGE_CHANNEL) })
        return
    }
    val selected = remember(version) {
        CallMessageChannel.fromStored(prefs.getInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, 0)).stored
    }
    val choices = listOf(stringResource(R.string.sms) to 0, "WhatsApp" to 1,
        stringResource(R.string.call_message_ask_each_time) to 2)
    DotSettingsChoiceRow(stringResource(R.string.call_message_channel),
        choices.first { it.second == selected }.first, choices, selected,
        { prefs.setInt(PreferenceManager.KEY_CALL_MESSAGE_CHANNEL, it) })
    Text(stringResource(R.string.call_message_channel_supporting),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp))
}
