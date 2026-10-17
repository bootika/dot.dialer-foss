package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import dev.goodwy.rphone.view.theme.accentText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.license.*
import io.github.bootika.dotdialer.core.license.*
import org.koin.compose.koinInject

@Composable
internal fun PlanAccessContent(onClose: () -> Unit, onGuides: (() -> Unit)? = null) {
    val manager=rememberPlanAccessManager()
    val state by manager.state.collectAsState()
    val helper=koinInject<PurchaseHelper>()
    val activity=rememberPlanBillingActivity(helper)
    val store by helper.storePlan.collectAsState()
    val pro by helper.isPro.collectAsState()
    val ready by helper.isBillingReady.collectAsState()
    val iaps by helper.iapSkuDetailsInitialized.collectAsState()
    val subs by helper.subSkuDetailsInitialized.collectAsState()
    val loading by helper.isLoading.collectAsState()
    val error by helper.errorMessage.collectAsState()
    var tier by remember { mutableStateOf(AppPlan.PREMIUM) }
    var period by remember { mutableStateOf(PlanPeriod.YEARLY) }
    var invitation by remember {mutableStateOf(false)}
    if(invitation)PlanInvitationDialog(onClose={invitation=false})
    val effective=remember(state,store,pro) { manager.effective }
    val initialPlan=remember {manager.effective}
    LaunchedEffect(effective) {if(effective>initialPlan) onClose()}
    LaunchedEffect(helper) { manager.attach(helper) }
    val product=PlanProducts.all.first { it.tier==tier && it.period==period }
    val loaded=if(product.subscription)subs else iaps
    val available=ready && loaded && helper.productAvailable(product.id,product.subscription)
    val price=if(available) {
        if(product.subscription)helper.getPriceSubscription(product.id) else helper.getPriceDonation(product.id)
    } else stringResource(R.string.plan_store_unavailable)
    Scaffold(topBar={DotSettingsTopBar(stringResource(R.string.plans_title),onClose)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("plans-content"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            val trialEnd = state.entitlements.trialEnds
            val now = System.currentTimeMillis() / 1000
            val trialActive = trialEnd > now && effective == AppPlan.PREMIUM && manager.paid < AppPlan.PREMIUM
            Surface(modifier = Modifier.testTag("plan-current-card"), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.plan_current, planName(effective)),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("plan-current"))
                    Text(stringResource(planSummary(effective)), style = MaterialTheme.typography.bodyMedium)
                    if (effective > AppPlan.FREE && onGuides != null) {
                        TextButton(onClick = onGuides, modifier = Modifier.testTag("plan-guide-replay")) {
                            Text(stringResource(R.string.plan_guide_replay))
                        }
                    }
                    if (trialActive) {
                        val days = ((trialEnd - now + 86399) / 86400).toInt()
                        Text(pluralStringResource(R.plurals.plan_trial_remaining, days, days),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("plan-trial-status"))
                        Text(stringResource(R.string.plan_base, planName(manager.paid)), style = MaterialTheme.typography.bodySmall)
                    } else if (state.ready && state.entitlements.trialStarted == 0L && effective != AppPlan.PREMIUM) {
                        Button(onClick = manager::startTrial, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("start-premium-trial")) {
                            Text(stringResource(R.string.plan_start_trial))
                        }
                    }
                    if (trialActive || (state.entitlements.trialStarted == 0L && effective != AppPlan.PREMIUM)) {
                        Text(stringResource(R.string.plan_trial_terms), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(state.unavailable) {
                Text(stringResource(R.string.plan_connection_required),color=MaterialTheme.colorScheme.error)
                TextButton(onClick=manager::retry,enabled=!state.busy) {Text(stringResource(R.string.plan_refresh))}
            }
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if (manager.paid == AppPlan.PREMIUM) R.string.plan_included else R.string.plan_choose), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            for (value in listOf(AppPlan.PLUS, AppPlan.PREMIUM)) {
                PlanOptionCard(value, tier == value, manager.paid >= value) { tier = value }
            }
            if (manager.paid < AppPlan.PREMIUM) DotSettingsCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.plan_period), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (value in PlanPeriod.entries) FilterChip(
                            selected = period == value, onClick = { period = value },
                            label = { Text(stringResource(periodLabel(value))) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("plan-period-$value"))
                    }
                    Text(planName(tier) + " · " + stringResource(periodLabel(period)),
                        style = MaterialTheme.typography.titleMedium)
                    Text(price, style = if (available) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                        color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("plan-real-store-price"))
                    Button(
                        onClick = {
                            val checkoutActivity = activity
                            if (checkoutActivity != null && !checkoutActivity.isFinishing && !checkoutActivity.isDestroyed) {
                                if (product.subscription) helper.purchaseSubscription(product.id, checkoutActivity)
                                else helper.purchaseDonation(product.id, checkoutActivity)
                            }
                        },
                        enabled = available && activity != null && !loading && manager.paid < tier,
                        modifier = Modifier.fillMaxWidth().testTag("plan-buy"),
                    ) {
                        Text(stringResource(
                            if (manager.paid >= tier) R.string.plan_current else R.string.plan_upgrade_to,
                            planName(tier),
                        ))
                    }
                }
            }
            Text(stringResource(R.string.plan_access_options), style = MaterialTheme.typography.titleMedium)
            DotSettingsCard {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = { invitation = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.invitation_title))
                    }
                    DotSettingsDivider()
                    TextButton(onClick = helper::restorePurchases, enabled = !loading,
                        modifier = Modifier.fillMaxWidth().testTag("plan-restore")) {
                        Text(stringResource(R.string.plan_restore))
                    }
                }
            }
            if (error != null) Text(stringResource(R.string.operation_failed), color = MaterialTheme.colorScheme.error)
            helper.giftLicenses?.let { gift ->
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gift_activate))
                }
                if (expanded) GiftLicenseControl(gift)
            }
            if(BuildConfig.FLAVOR=="foss") FossPlanSupport()
            Spacer(Modifier.height(24.dp))
        }
    }
}


private fun planName(plan: AppPlan): String = when (plan) {
    AppPlan.FREE -> AppPlan.FREE.displayName
    AppPlan.PLUS -> AppPlan.PLUS.displayName
    AppPlan.PREMIUM -> AppPlan.PREMIUM.displayName
}

private fun planSummary(plan: AppPlan): Int = when (plan) {
    AppPlan.FREE -> R.string.plan_free_summary
    AppPlan.PLUS -> R.string.plan_plus_summary
    AppPlan.PREMIUM -> R.string.plan_premium_summary
}

private fun periodLabel(period: PlanPeriod): Int = when (period) {
    PlanPeriod.MONTHLY -> R.string.monthly
    PlanPeriod.YEARLY -> R.string.yearly
    PlanPeriod.LIFETIME -> R.string.plan_lifetime
}

@Composable
private fun PlanOptionCard(plan: AppPlan, selected: Boolean, included: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .testTag("plan-tier-$plan"),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(planName(plan), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (plan == AppPlan.PLUS) R.string.contact_photos_title else R.string.plan_interface_magic),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.accentText)
                Text(stringResource(planSummary(plan)), style = MaterialTheme.typography.bodyMedium)
                if (included) Text(stringResource(R.string.plan_included), style = MaterialTheme.typography.labelMedium)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}


@Composable
internal fun planSettingLocked(feature: PlanFeature,onClose: () -> Unit): Boolean {
    val manager=rememberPlanAccessManager()
    val state by manager.state.collectAsState()
    if(remember(state) {manager.allows(feature)}) return false
    PlanAccessContent(onClose)
    return true
}

@Composable
internal fun PlanUpgradeHost() {
    val manager=rememberPlanAccessManager()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(manager) {manager.upgradeRequests.collect { visible=true }}
    if(visible)Dialog(onDismissRequest={visible=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {PlanAccessContent(onClose={visible=false})}
    }
}
