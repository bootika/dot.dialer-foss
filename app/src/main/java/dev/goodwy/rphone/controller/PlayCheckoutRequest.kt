package dev.goodwy.rphone.controller

/** Cancellation invalidates only a prepared checkout, never an already launched store transaction. */
internal class PlayCheckoutRequest {
    private var generation=0L
    private var preparing=false
    @Synchronized fun begin():Long { preparing=true;return ++generation }
    @Synchronized fun isCurrent(request:Long)=preparing && request==generation
    @Synchronized fun finish(request:Long) {if(request==generation)preparing=false}
    @Synchronized fun cancel():Boolean {val wasPreparing=preparing;preparing=false;generation++;return wasPreparing}
}
