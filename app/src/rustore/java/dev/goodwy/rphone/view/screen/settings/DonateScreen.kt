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
import androidx.activity.ComponentActivity
import dev.goodwy.rphone.controller.RuStoreViewModel

@Destination<RootGraph>
@Composable
fun DonateScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity

    val purchaseHelper: PurchaseHelper = koinInject()
    val viewModel = purchaseHelper as? RuStoreViewModel

    val purchaseSuccess by purchaseHelper.purchaseSuccess.collectAsStateWithLifecycle()
    val isLoading by purchaseHelper.isLoading.collectAsStateWithLifecycle()
    val errorMessage by purchaseHelper.errorMessage.collectAsStateWithLifecycle()
    val isBillingReady by purchaseHelper.isBillingReady.collectAsStateWithLifecycle()
    val iapSkuDetailsInitialized by purchaseHelper.iapSkuDetailsInitialized.collectAsStateWithLifecycle()
    val subSkuDetailsInitialized by purchaseHelper.subSkuDetailsInitialized.collectAsStateWithLifecycle()

    // Initialisation
    LaunchedEffect(activity) {
        activity?.let {
            viewModel?.onScreenActivityAvailable(it)
            purchaseHelper.checkProStatus()
        }
    }

    // Resets `isLoading` if the user has returned from the RuStore login screen
    val latestLoading by rememberUpdatedState(isLoading)
    DisposableEffect(activity) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (latestLoading) {
                    purchaseHelper.clearErrors()
                }
            }
        }
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        lifecycleOwner?.lifecycle?.addObserver(listener)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(listener) }
    }

    val thankText = stringResource(R.string.thank_you_for_your_support)
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            context.toast(thankText)
            purchaseHelper.checkProStatus()
            purchaseHelper.clearPurchaseSuccess()
        }
    }

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
        onRestore = { purchaseHelper.restorePurchases() },
    )
}
