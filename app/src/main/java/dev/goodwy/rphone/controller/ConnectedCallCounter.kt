package dev.goodwy.rphone.controller

import android.content.Context
import android.telecom.Call
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isTelecomConference
import io.github.bootika.dotdialer.observability.ProductStatistics
import java.util.Collections
import java.util.IdentityHashMap

internal class ConnectedCallCounter(private val context: Context, private val preferences: PreferenceManager) {
    private val connected = Collections.newSetFromMap(IdentityHashMap<Call, Boolean>())

    fun onAdded(call: Call, initialState: Int) {
        // Telecom can return an already connected call when it rebinds the service.
        if (initialState == Call.STATE_ACTIVE || initialState == Call.STATE_HOLDING) connected.add(call)
    }

    fun onActive(call: Call) {
        if (call.parent != null || call.isTelecomConference() || !connected.add(call)) return
        ProductStatistics.get(context, preferences).record(ProductStatistics.Event.ConnectedCall)
    }

    fun onRemoved(call: Call) {
        connected.remove(call)
    }
}
