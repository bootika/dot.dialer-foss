package dev.goodwy.rphone.controller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlayServerSnapshot {
    suspend fun reconcile(snapshot: PlayPurchaseSnapshot?, required: Boolean, authority: PlayPurchaseAuthority?,
        restore: Boolean, current: () -> Boolean): PlayPurchaseSnapshot? {
        if (snapshot == null || !required) return snapshot
        // Pending payment is not proof that an earlier, still-paid receipt was revoked.
        if (snapshot.pending) return null
        return try {
            val verified = withContext(Dispatchers.IO) { checkNotNull(authority).reconcile(snapshot.receipts, restore, current) }
            snapshot.copy(ownership = verified, subscriptions = snapshot.subscriptions.filter {
                active -> active.products.any { it in verified.subscriptions }
            })
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    }
}
