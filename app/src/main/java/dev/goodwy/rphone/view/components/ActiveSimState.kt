package dev.goodwy.rphone.view.components

import android.telephony.SubscriptionManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.goodwy.rphone.controller.util.activeSimCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** OS-driven UI state; no worker, polling, GPS or new permission request. */
@Composable
internal fun rememberActiveSimCount(): Int {
    val context = LocalContext.current
    var resume by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resume++ }
    val count by produceState(0, context, resume) {
        fun refresh() { launch(Dispatchers.IO) { value = activeSimCount(context) } }
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() { refresh() }
        }
        var registered = false
        try {
            @Suppress("DEPRECATION")
            manager?.addOnSubscriptionsChangedListener(listener)
            registered = manager != null
        } catch (unavailable: RuntimeException) {
            Log.w("ActiveSimUi", "Subscription observer unavailable: ${unavailable.javaClass.simpleName}")
        }
        refresh()
        awaitDispose {
            if (registered) try { manager?.removeOnSubscriptionsChangedListener(listener) }
            catch (unavailable: RuntimeException) {
                Log.w("ActiveSimUi", "Subscription observer cleanup: ${unavailable.javaClass.simpleName}")
            }
        }
    }
    return count
}
