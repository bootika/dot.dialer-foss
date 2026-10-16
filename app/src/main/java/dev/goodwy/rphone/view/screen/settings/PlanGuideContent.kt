package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.PlanAccessManager
import dev.goodwy.rphone.controller.util.*
import io.github.bootika.dotdialer.core.license.*
import org.koin.compose.koinInject

internal const val PLAN_GUIDE_ACK = "plan_guide_acknowledged_tier"

@Composable
internal fun PlanGuideContent(tier: AppPlan, onNext: () -> Unit, onMore: () -> Unit) {
    val prefs = koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    val context = LocalContext.current
    val profiles = remember { ContactOptimizationProfiles(context) }
    val profileRevision by profiles.changes.collectAsState()
    val account = remember(revision) { prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null) }
    val settings = remember(account, profileRevision) { profiles.settings(account) }
    val premium = tier == AppPlan.PREMIUM
    Surface(Modifier.fillMaxSize().testTag("plan-guide-${tier.name}")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if(premium) "Premium" else "Plus", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
                Text(if(premium) stringResource(R.string.plan_interface_magic) else "Contacts Optimizer", style=MaterialTheme.typography.headlineMedium)
                Text(stringResource(if(premium) R.string.plan_premium_summary else R.string.plan_plus_summary), style=MaterialTheme.typography.bodyLarge)
                if(premium) {
                    DotSettingsCard {
                        val colors=listOf(R.string.accent_jade to "jade",R.string.accent_blue to "blue",R.string.accent_violet to "violet",R.string.accent_coral to "coral")
                        DotAccentColorChoiceRow(stringResource(R.string.accent_color),colors.map { stringResource(it.first) to it.second },
                            prefs.getString(PreferenceManager.KEY_ACCENT_COLOR,"jade") ?: "jade", {prefs.setString(PreferenceManager.KEY_ACCENT_COLOR,it)})
                        DotSettingsDivider()
                        DotSettingsSwitchRow(stringResource(R.string.liquid_glass_elements),checked=prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS,false),
                            onCheckedChange={prefs.setBoolean(PreferenceManager.KEY_LIQUID_GLASS,it)})
                        DotSettingsDivider()
                        DotSettingsSwitchRow(stringResource(R.string.ambient_droplets),checked=prefs.getBoolean("ambient_droplets_enabled",false),
                            onCheckedChange={prefs.setBoolean("ambient_droplets_enabled",it)})
                    }
                } else {
                    DotSettingsCard {
                        if(account != null) DotSettingsSwitchRow(stringResource(R.string.standardize_phone_numbers),checked=settings.normalize,
                            onCheckedChange={enabled -> profiles.update(account) {it.copy(normalize=enabled)}})
                        DotSettingsNavigationRow(stringResource(R.string.plan_photo_sources),onClick=onMore)
                    }
                    if(account == null) Text(stringResource(R.string.plan_choose_account),style=MaterialTheme.typography.bodyMedium)
                }
                if(premium && account != null) DotSettingsCard {
                    ContactsOptimizerPolicyControl(settings) {policy -> profiles.update(account) {it.copy(automaticPhotoPolicy=policy,autoUpdateExisting=policy!=ContactPhotoAutomaticPolicy.FillMissing)}}
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick=onMore,
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("plan-guide-more")) {
                Text(stringResource(R.string.plan_more_settings))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick=onNext,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("plan-guide-next")) {
                Text(stringResource(R.string.plan_guide_continue))
            }
        }
    }
}

/** Render only inside the unlocked main surface, never over an ongoing call. */
@Composable
internal fun PlanGuideHost(onMore: (AppPlan) -> Unit) {
    var deferred by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val manager=rememberPlanAccessManager()
    val state by manager.state.collectAsState()
    val prefs=koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    val acknowledged=remember(revision) { AppPlan.entries.getOrElse(prefs.getInt(PLAN_GUIDE_ACK,0)) {AppPlan.FREE} }
    val pending=remember(state,acknowledged) {PlanGuidePolicy.pending(manager.effective,acknowledged)}
    val tier=pending.firstOrNull()?.takeUnless {deferred} ?: return
    fun acknowledge() {prefs.setInt(PLAN_GUIDE_ACK,maxOf(acknowledged,tier).ordinal)}
    androidx.compose.ui.window.Dialog(onDismissRequest=::acknowledge,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        key(tier) { PlanGuideContent(tier,onNext=::acknowledge,onMore={acknowledge();deferred=true;onMore(tier)}) }
    }
}
