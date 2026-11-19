package dev.goodwy.rphone.controller

/** A failed or superseded query cannot replace the last authoritative ownership snapshot. */
internal class PlayStoreOwnershipQuery(
    private val isCurrent: () -> Boolean,
    private val onComplete: (PlayStoreOwnership?) -> Unit,
) {
    private val replies = mutableMapOf<Boolean, Set<String>?>()

    @Synchronized
    fun accept(subscription: Boolean, products: Set<String>?) {
        if (!isCurrent() || replies.containsKey(subscription)) return
        replies[subscription] = products
        if (replies.size != 2) return
        val inApps = replies[false]
        val subscriptions = replies[true]
        onComplete(if (inApps != null && subscriptions != null) PlayStoreOwnership(inApps, subscriptions) else null)
    }
}
