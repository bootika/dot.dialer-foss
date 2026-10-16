package dev.goodwy.rphone.view.screen.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.license.PlanProducts

/** Store screens keep the current Activity only inside the composition, including RuStore login return. */
@Composable
internal fun rememberPlanBillingActivity(helper: PurchaseHelper): ComponentActivity? {
    val activity=LocalActivity.current as? ComponentActivity
    val loading by helper.isLoading.collectAsState()
    val success by helper.purchaseSuccess.collectAsState()
    val latestLoading by rememberUpdatedState(loading)
    LaunchedEffect(activity,helper) {
        helper.initBilling()
        activity?.let(helper::onScreenActivityAvailable)
        helper.loadProducts(PlanProducts.all.map {it.id})
        helper.checkProStatus()
    }
    DisposableEffect(activity) {
        val listener=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_RESUME && latestLoading)helper.clearErrors()
        }
        activity?.lifecycle?.addObserver(listener)
        onDispose {activity?.lifecycle?.removeObserver(listener)}
    }
    LaunchedEffect(success) {if(success) {helper.checkProStatus();helper.clearPurchaseSuccess()}}
    return activity
}
