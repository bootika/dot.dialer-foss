package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextAlign
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper

/** Presentation shared by the stores; product IDs and checkout remain flavor-owned. */
@Composable
internal fun SupportScaffold(
    title: String,
    onBack: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { DotSettingsTopBar(title, onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag("support-footer"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = footer,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("support-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun SupportIntroduction(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null,
                modifier = Modifier.padding(12.dp).size(28.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun storeSupportPlans(
    helper: PurchaseHelper,
    subscriptionsLoaded: Boolean,
    purchasesLoaded: Boolean,
    billingReady: Boolean,
): List<SupportPlan> {
    val missing = stringResource(R.string.no_connection)
    val loading = stringResource(R.string.loading_price)
    val unavailable = stringResource(R.string.support_unavailable)
    fun plan(id: String, productId: String, title: String, description: String, label: String, subscription: Boolean): SupportPlan {
        val loaded = if (subscription) subscriptionsLoaded else purchasesLoaded
        val rawPrice = if (productId.isBlank()) unavailable else if (!billingReady) missing else if (!loaded) loading else
            if (subscription) helper.getPriceSubscription(productId) else helper.getPriceDonation(productId)
        val price = if (rawPrice.isBlank() || rawPrice == "...") unavailable else rawPrice
        return SupportPlan(id, title, description, price, label, subscription,
            owned = productId.isNotBlank() && if (subscription) helper.isSubPurchased(productId) else helper.isIapPurchased(productId),
            available = billingReady && loaded && productId.isNotBlank()
                && price != missing && price != unavailable && price != loading,
            productId = productId)
    }
    // Restore can change ownership without changing product-loading flags.
    return key(helper.isPro.collectAsStateWithLifecycle().value) { listOf(
        plan("monthly", dev.goodwy.rphone.BuildConfig.SUBSCRIPTION_ID_X1, stringResource(R.string.monthly),
            stringResource(R.string.plan_monthly_description), stringResource(R.string.most_flexible), true),
        plan("yearly", dev.goodwy.rphone.BuildConfig.SUBSCRIPTION_YEAR_ID_X1, stringResource(R.string.yearly),
            stringResource(R.string.plan_yearly_description), stringResource(R.string.best_value), true),
        plan("lifetime", dev.goodwy.rphone.BuildConfig.PRODUCT_ID_X1, stringResource(R.string.lifetime),
            stringResource(R.string.plan_lifetime_description), stringResource(R.string.pay_once), false),
    ) }
}

@Composable
internal fun StoreSupportContent(
    plans: List<SupportPlan>,
    selectedId: String,
    loading: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onContinue: (SupportPlan) -> Unit,
    onRestore: () -> Unit,
    licenseContent: @Composable () -> Unit = {},
) {
    val selected = plans.firstOrNull { it.id == selectedId }
    val canContinue = !loading && selected?.let { it.available && !it.owned } == true
    SupportScaffold(stringResource(R.string.plans_title), onBack, footer = {
        Button(
            onClick = { selected?.takeIf { !loading && it.available && !it.owned }?.let(onContinue) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("support-continue"),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(if (selected != null) stringResource(R.string.support_continue_with, selected.title)
                else stringResource(R.string.continue_support), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = { if (!loading) onRestore() }, enabled = !loading,
            modifier = Modifier.testTag("support-restore"), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
            Text(stringResource(R.string.restore_purchase))
        }
    }) {
        Text(stringResource(R.string.support_personalize_title),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("support-heading"))
        SupportIntroduction(stringResource(R.string.plans_description))
        DotSettingsCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Icons.Outlined.Palette to R.string.support_benefit_themes,
                    Icons.Outlined.TextFields to R.string.support_benefit_fonts,
                    Icons.Outlined.Wallpaper to R.string.support_benefit_backgrounds).forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            plans.forEach { plan ->
                SupportPlanCard(plan, selected = plan.id == selectedId && !plan.owned,
                    enabled = !loading && plan.available && !plan.owned,
                    onSelect = { onSelect(plan.id) })
            }
        }
        licenseContent()
    }
}
