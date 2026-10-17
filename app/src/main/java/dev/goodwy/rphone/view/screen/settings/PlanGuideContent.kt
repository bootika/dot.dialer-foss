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
import dev.goodwy.rphone.view.components.GuideNavigationControls
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.goodwy.rphone.controller.util.*
import io.github.bootika.dotdialer.core.license.*
import org.koin.compose.koinInject

internal const val PLAN_GUIDE_ACK = "plan_guide_acknowledged_tier"

@Composable
internal fun PlanGuideContent(tier: AppPlan, onNext: () -> Unit, onMore: () -> Unit,
    isLast: Boolean = true, onBack: (() -> Unit)? = null) {
    val prefs = koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    val profiles = remember { ContactOptimizationProfiles(context) }
    val profileRevision by profiles.changes.collectAsState()
    val account = remember(revision) { prefs.getString(GoogleContactPhotoPreferences.ACCOUNT, null)?.takeIf { it.isNotBlank() } }
    val settings = remember(account, profileRevision, resumed) { profiles.settings(account) }
    val premium = tier == AppPlan.PREMIUM
    Surface(Modifier.fillMaxSize().testTag("plan-guide-${tier.name}")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(tier.displayName, style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
                Text(if(premium) stringResource(R.string.plan_interface_magic) else "Contacts Optimizer", style=MaterialTheme.typography.headlineMedium)
                Text(stringResource(if(premium) R.string.plan_premium_summary else R.string.plan_optimizer_guide_summary), style=MaterialTheme.typography.bodyLarge)
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
                    PlanGuideOptimizerContent(account, settings, resumed,
                        googleEnabled = prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false),
                        onNormalize = { enabled -> account?.let { profiles.update(it) { current -> current.copy(normalize = enabled) } } },
                        onMore = onMore)
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
            GuideNavigationControls(isLast, onNext, onBack)
        }
    }
}

/** Render only inside the unlocked main surface, never over an ongoing call. */
@Composable
internal fun PlanGuideHost(onMore: (AppPlan) -> Unit) {
    val manager=rememberPlanAccessManager()
    val state by manager.state.collectAsState()
    var deferred by androidx.compose.runtime.saveable.rememberSaveable(manager.effective) { mutableStateOf(false) }
    val prefs=koinInject<PreferenceManager>()
    val revision by prefs.settingsChanged.collectAsState()
    val acknowledged=remember(revision) { AppPlan.entries.getOrElse(prefs.getInt(PLAN_GUIDE_ACK,0)) {AppPlan.FREE} }
    val pending=remember(state,acknowledged) {PlanGuidePolicy.pending(manager.effective,acknowledged)}
    if (pending.isEmpty() || deferred) return
    fun acknowledge(value: AppPlan) {prefs.setInt(PLAN_GUIDE_ACK,maxOf(acknowledged,value).ordinal)}
    androidx.compose.ui.window.Dialog(onDismissRequest={deferred=true},
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        PlanGuideSequenceContent(pending, onFinished = { acknowledge(pending.last()) },
            onMore = { current -> acknowledge(current); deferred=true; onMore(current) },
            onBackAtStart = { deferred=true }, welcomePlan = manager.effective)
    }
}
