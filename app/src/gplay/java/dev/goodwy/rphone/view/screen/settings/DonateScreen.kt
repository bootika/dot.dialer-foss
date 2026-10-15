package dev.goodwy.rphone.view.screen.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.toast
import org.koin.compose.koinInject
import dev.goodwy.rphone.controller.PlayStoreViewModel

@Destination<RootGraph>
@Composable
fun DonateScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val purchaseHelper: PurchaseHelper = koinInject()

    val purchaseSuccess by purchaseHelper.purchaseSuccess.collectAsStateWithLifecycle()
    val isLoading by purchaseHelper.isLoading.collectAsStateWithLifecycle()
    val errorMessage by purchaseHelper.errorMessage.collectAsStateWithLifecycle()
    val isBillingReady by purchaseHelper.isBillingReady.collectAsStateWithLifecycle()
    val iapSkuDetailsInitialized by purchaseHelper.iapSkuDetailsInitialized.collectAsStateWithLifecycle()
    val subSkuDetailsInitialized by purchaseHelper.subSkuDetailsInitialized.collectAsStateWithLifecycle()

    // Initialization
    LaunchedEffect(Unit) {
        purchaseHelper.initBilling()
        (purchaseHelper as? PlayStoreViewModel)?.retrieveDonation(
            iaps = listOf(
                BuildConfig.PRODUCT_ID_X1
            ),
            subs = listOf(
                BuildConfig.SUBSCRIPTION_ID_X1,
                BuildConfig.SUBSCRIPTION_YEAR_ID_X1,
            )
        )
    }

    // We display a Toast notification only when the user has JUST made a purchase
    val thankText = stringResource(R.string.plan_purchase_complete)
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            context.toast(thankText)
            purchaseHelper.checkProStatus()
            // Clear the flag so that the Toast doesn't appear again
            purchaseHelper.clearPurchaseSuccess()
        }
    }

    // Error Handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.toast(it)
            purchaseHelper.clearErrors()
        }
    }


    var selectedId by remember { mutableStateOf("yearly") }
    val plans = storeSupportPlans(purchaseHelper, subSkuDetailsInitialized, iapSkuDetailsInitialized, isBillingReady)
    StoreSupportContent(
        plans = plans,
        selectedId = selectedId,
        loading = isLoading,
        onBack = { navigator.navigateUp() },
        onSelect = { selectedId = it },
        onContinue = { plan ->
            val checkoutActivity = activity
            if (checkoutActivity != null && !isLoading && isBillingReady && plan.available && !plan.owned && plan.productId.isNotBlank()) {
                if (plan.subscription) purchaseHelper.purchaseSubscription(plan.productId, checkoutActivity)
                else purchaseHelper.purchaseDonation(plan.productId, checkoutActivity)
            }
        },
        onRestore = { purchaseHelper.refreshAllData() },
        licenseContent = { purchaseHelper.giftLicenses?.let { GiftLicenseControl(it) } },
    )
}
