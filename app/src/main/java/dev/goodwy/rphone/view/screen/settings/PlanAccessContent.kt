package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
internal fun PlanAccessContent(onClose: () -> Unit) {
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
            Text(stringResource(R.string.plan_current,effective.name.lowercase().replaceFirstChar(Char::uppercase)),style=MaterialTheme.typography.titleLarge)
            for(plan in AppPlan.entries) DotSettingsCard { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(plan.name.lowercase().replaceFirstChar(Char::uppercase),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(when(plan){AppPlan.FREE->R.string.plan_free_summary;AppPlan.PLUS->R.string.plan_plus_summary;AppPlan.PREMIUM->R.string.plan_premium_summary}),style=MaterialTheme.typography.bodyMedium)
            } }
            val trialEnd=state.entitlements.trialEnds
            if(trialEnd>System.currentTimeMillis()/1000 && effective==AppPlan.PREMIUM) {
                val days=((trialEnd-System.currentTimeMillis()/1000+86399)/86400).toInt()
                Text(pluralStringResource(R.plurals.plan_trial_remaining,days,days),style=MaterialTheme.typography.bodyLarge)
            } else if(state.ready && state.entitlements.trialStarted==0L && effective!=AppPlan.PREMIUM) {
                Button(onClick=manager::startTrial,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("start-premium-trial")) {
                    Text(stringResource(R.string.plan_start_trial))
                }
            }
            Text(stringResource(R.string.plan_trial_terms),style=MaterialTheme.typography.bodySmall)
            if(state.unavailable) {
                Text(stringResource(R.string.plan_connection_required),color=MaterialTheme.colorScheme.error)
                TextButton(onClick=manager::retry,enabled=!state.busy) {Text(stringResource(R.string.plan_refresh))}
            }
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                for(value in listOf(AppPlan.PLUS,AppPlan.PREMIUM)) FilterChip(tier==value,{tier=value},label={Text(value.name.lowercase().replaceFirstChar(Char::uppercase))},modifier=Modifier.testTag("plan-tier-$value"))
            }
            Column { for(value in PlanPeriod.entries) FilterChip(period==value,{period=value},label={Text(stringResource(when(value){PlanPeriod.MONTHLY->R.string.monthly;PlanPeriod.YEARLY->R.string.yearly;PlanPeriod.LIFETIME->R.string.plan_lifetime}))},modifier=Modifier.testTag("plan-period-$value")) }
            Text(price,style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("plan-real-store-price"))
            Button(onClick={ val checkoutActivity=activity
                if(checkoutActivity!=null && !checkoutActivity.isFinishing && !checkoutActivity.isDestroyed) {
                    if(product.subscription)helper.purchaseSubscription(product.id,checkoutActivity) else helper.purchaseDonation(product.id,checkoutActivity)
                }
            },enabled=available && activity!=null && !loading && manager.paid<tier,modifier=Modifier.fillMaxWidth().testTag("plan-buy")) {Text(stringResource(R.string.plan_buy))}
            TextButton(onClick=helper::restorePurchases,enabled=!loading,modifier=Modifier.testTag("plan-restore")) {Text(stringResource(R.string.plan_restore))}
            if(error!=null)Text(stringResource(R.string.operation_failed),color=MaterialTheme.colorScheme.error)
            helper.giftLicenses?.let { GiftLicenseControl(it) }
            if(BuildConfig.FLAVOR=="foss") FossPlanSupport()
            HorizontalDivider()
            TextButton(onClick={invitation=true},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.invitation_title))}
            Spacer(Modifier.height(24.dp))
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
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {PlanAccessContent {visible=false}}
    }
}
