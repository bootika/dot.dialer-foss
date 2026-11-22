package dev.goodwy.rphone.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI expiry publication is independent of network and cannot retry a financial action. */
internal object PlayAuthorityLifecycle {
    fun observe(scope: CoroutineScope, authority: PlayPurchaseAuthority?, generation: () -> Long,
        busy: () -> Boolean, publish: () -> Unit) = scope.launch {
        var lastRefresh=Long.MIN_VALUE
        while (isActive) {
            delay(30_000)
            if (authority?.ownership() != null) {
                val tick=android.os.SystemClock.elapsedRealtime()
                if(!busy() && (lastRefresh==Long.MIN_VALUE || tick-lastRefresh>=15*60_000L || tick<lastRefresh)) {
                    lastRefresh=tick
                    val current=generation()
                    try { withContext(Dispatchers.IO) { authority.refresh { generation()==current && !busy() } } }
                    catch(error:CancellationException){throw error}
                    catch(_:Exception){ /* An outage neither retries a charge nor removes signed rights. */ }
                }
                publish()
            }
        }
    }
}
