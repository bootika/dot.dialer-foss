package dev.goodwy.rphone.controller.util

import android.telecom.CallAudioState

/**
 * Stable route order used by every repeated tap on the in-call audio button.
 *
 * The common three-route cycle is Bluetooth -> speaker -> handset -> Bluetooth. A wired headset
 * stays reachable as an additional fallback without changing that common order when it is absent.
 */
internal fun supportedCallAudioRoutes(supportedRouteMask: Int): List<Int> = buildList {
    if ((supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
        add(CallAudioState.ROUTE_BLUETOOTH)
    }
    if ((supportedRouteMask and CallAudioState.ROUTE_SPEAKER) != 0) {
        add(CallAudioState.ROUTE_SPEAKER)
    }
    if ((supportedRouteMask and CallAudioState.ROUTE_EARPIECE) != 0) {
        add(CallAudioState.ROUTE_EARPIECE)
    }
    if ((supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
        add(CallAudioState.ROUTE_WIRED_HEADSET)
    }
}

internal fun nextCallAudioRoute(currentRoute: Int, supportedRouteMask: Int): Int? {
    val routes = supportedCallAudioRoutes(supportedRouteMask)
    if (routes.size < 2) return null

    val currentIndex = routes.indexOf(currentRoute)
    return if (currentIndex < 0) routes.first() else routes[(currentIndex + 1) % routes.size]
}
