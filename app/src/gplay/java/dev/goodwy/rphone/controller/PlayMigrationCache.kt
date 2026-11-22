package dev.goodwy.rphone.controller

/** Never copy a new finite signed grant into the old cache which has no paid-through dates. */
internal object PlayMigrationCache {
    fun after(previous: PlayStoreOwnership?, signed: PlayStoreOwnership, durable: Boolean): PlayStoreOwnership? {
        previous ?: return null
        val next = if(durable) PlayStoreOwnership(emptySet(),emptySet()) else PlayStoreOwnership(
            previous.inApps.intersect(signed.inApps),previous.subscriptions.intersect(signed.subscriptions))
        return next.takeUnless { it == previous }
    }
}
