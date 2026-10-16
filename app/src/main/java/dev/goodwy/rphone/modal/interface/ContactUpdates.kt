package dev.goodwy.rphone.modal.`interface`

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Process-local contact refresh contract, independent of a particular repository implementation.
 * Repositories without an observable cache retain empty-flow/no-op defaults.
 */
interface ContactUpdates {
    /** Non-replayed changes; cached snapshots must be invalidated before an event is published. */
    val changes: Flow<Unit> get() = emptyFlow()

    /** Silent invalidation: the caller owns the reload, so observers must not reload it again. */
    fun invalidate() = Unit

    /** Invalidate snapshots before notifying subscribers, including for app-owned photo changes. */
    fun invalidateAndNotify() = Unit
}
