package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.license.PlanFeature

@Composable
internal fun QuickActionGestureSetting(prefs: PreferenceManager) {
    val revision by prefs.settingsChanged.collectAsStateWithLifecycle()
    val enabled = remember(revision) { prefs.getBoolean(dev.goodwy.rphone.controller.util.HOME_QUICK_ACTION_GESTURE, false) }
    val allowed = prefs.allowsFeature(PlanFeature.CALL_PRODUCTIVITY)
    Box(Modifier.alpha(if (allowed) 1f else .45f).clickable(enabled = !allowed) { prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY) }) {
        DotSettingsSwitchRow(title = stringResource(R.string.quick_action_gesture),
            supporting = stringResource(R.string.quick_action_gesture_description), checked = enabled,
            onCheckedChange = { if (prefs.requireFeature(PlanFeature.CALL_PRODUCTIVITY)) prefs.setBoolean(dev.goodwy.rphone.controller.util.HOME_QUICK_ACTION_GESTURE, it) })
    }
}
