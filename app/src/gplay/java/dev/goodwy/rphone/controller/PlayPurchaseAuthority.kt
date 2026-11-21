package dev.goodwy.rphone.controller

import android.content.Context
import android.os.SystemClock
import dev.goodwy.rphone.controller.license.ExpiryObservation
import dev.goodwy.rphone.controller.license.GiftLicenseStorage
import org.json.JSONObject

/** Durable authority for one source; it never writes Plan/Gift/trial or companion credentials. */
internal class PlayPurchaseAuthority(
    val service: PlayPurchaseService,
    private val audience: String,
    private val installation: String,
    private val verifier: PlayPurchaseGrantVerifier,
    private val read: () -> JSONObject?,
    private val write: (JSONObject) -> Unit,
    private val invalidate: () -> Unit,
    private val journal: ExpiryObservation,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val boot: () -> Int = { -1 },
) {
    private var grant: PlayPurchaseGrant? = null
    private var envelope: JSONObject? = null
    private var wallAtLoad = now()
    private var elapsedAtLoad = elapsed()
    private var bootAtLoad = boot()
    private var observed = 0L
    private var persistedBoundary = 0L
    private var attemptedBoundary = 0L
    private var retryAt = Long.MIN_VALUE
    private var dirty: JSONObject? = null
    var durable: Boolean = false
        private set

    init {
        try {
            read()?.let { record ->
                require(record.keys().asSequence().toSet() == setOf("grant", "clock_floor", "wall", "elapsed", "boot"))
                val signed = record.getJSONObject("grant")
                val restored = verifier.verify(signed, audience, installation, cached = true)
                require(restored.authoritative && restored.operation != "register")
                wallAtLoad=record.getLong("wall");elapsedAtLoad=record.getLong("elapsed");bootAtLoad=record.getInt("boot")
                val floor = maxOf(restored.issued, record.getLong("clock_floor"),
                    runCatching { journal.floor(signed.toString()) }.getOrDefault(0))
                grant = restored; envelope = signed; observed = maxOf(observed, floor)
                persistedBoundary = floor
                durable = true
            }
        } catch (error: Exception) {
            // Corruption is not a new signed revocation and must not clear legacy migration rights.
            android.util.Log.w("PlayAuthority", "restore: ${error.javaClass.simpleName}")
        }
    }

    @Synchronized fun ownership(): PlayStoreOwnership? {
        val current = grant ?: return null
        val sameBoot=bootAtLoad>=0 && bootAtLoad==boot() && elapsed()>=elapsedAtLoad
        val age=if(sameBoot)(elapsed()-elapsedAtLoad)/1000 else (now()-wallAtLoad).coerceAtLeast(0)
        observed = maxOf(observed, current.issued + age)
        val boundary = current.observedBoundary(observed)
        val tick = elapsed()
        val ready = retryAt == Long.MIN_VALUE || tick - retryAt >= 30_000 || tick < retryAt
        if (boundary > persistedBoundary && (boundary != attemptedBoundary || ready)) {
            attemptedBoundary = boundary
            retryAt = tick
            val signed = requireNotNull(envelope)
            try {
                journal.save(signed.toString(), boundary)
                persistedBoundary = boundary
                persist(signed, boundary)
            } catch (error: Exception) {
                if (persist(signed, boundary)) persistedBoundary = boundary
            }
        } else if (dirty != null && ready) {
            retryAt = tick
            val pending = dirty!!
            try { write(pending); dirty = null; durable = true } catch (_: Exception) { /* Retry bounded local writes. */ }
        }
        return current.ownership(observed)
    }

    /** Register is not restoration: its empty initial snapshot cannot revoke a legacy purchase. */
    fun prepareCheckout(): String {
        register(true)
        return requireNotNull(service.principal)
    }

    private fun register(requirePurchase: Boolean) {
        val response = service.register()
        val verified = verifier.verify(response.getJSONObject("grant"), audience, installation,
            response.getString("request_nonce"),response.getLong("request_sequence"))
        require(verified.principal == response.getString("principal") && verified.operation == "register" && !verified.authoritative)
        require(!requirePurchase || verified.purchaseAvailable)
        service.acceptPrincipal(response)
    }

    fun reconcile(receipts: List<PlayPurchaseReceipt>, restore: Boolean = true,
        isCurrent: () -> Boolean = { true }): PlayStoreOwnership {
        if (service.principal == null) register(false)
        return accept(service.verify(receipts, restore), "verify", isCurrent)
    }
    fun refresh(isCurrent: () -> Boolean = { true }): PlayStoreOwnership {
        synchronized(this) { check(grant != null) { "A verified purchase snapshot is required before refresh" } }
        return accept(service.refresh(), "refresh", isCurrent)
    }

    @Synchronized private fun accept(response: JSONObject, operation: String, isCurrent: () -> Boolean): PlayStoreOwnership {
        val signed = response.getJSONObject("grant")
        val next = verifier.verify(signed, audience, installation, response.getString("request_nonce"),response.getLong("request_sequence"))
        require(next.principal == response.getString("principal"))
        require(next.operation == operation && next.authoritative && (operation != "verify" || next.ownershipComplete))
        check(isCurrent()) { "Purchase request superseded" }
        require(next.sequence > (grant?.sequence ?: 0))
        service.acceptPrincipal(response)
        if (grant?.let(next::reduces) == true) {
            try { invalidate() } catch (error: Exception) {
                android.util.Log.w("PlayAuthority", "invalidate: ${error.javaClass.simpleName}")
            }
        }
        // Signed decisions are effective in memory even when replacement IO is unavailable.
        grant = next; envelope = signed
        wallAtLoad = now(); elapsedAtLoad = elapsed();bootAtLoad=boot()
        observed = next.issued; persistedBoundary = 0; attemptedBoundary = 0; retryAt = Long.MIN_VALUE; dirty = null
        persist(signed, next.issued)
        return requireNotNull(ownership())
    }

    private fun persist(signed: JSONObject, floor: Long): Boolean {
        val record = JSONObject().put("grant", signed).put("clock_floor", floor)
            .put("wall",wallAtLoad).put("elapsed",elapsedAtLoad).put("boot",bootAtLoad)
        return try { write(record); dirty = null; durable = true; true }
        catch (_: Exception) { dirty = record; durable = false; false }
    }

    companion object {
        fun create(context: Context): PlayPurchaseAuthority {
            val identity = PlayPurchaseService.identity(context)
            val cache = GiftLicenseStorage(context, "dot.dialer.play-authority.v1", "play-authority-v1.bin")
            val floor = GiftLicenseStorage(context, "dot.dialer.play-expiry.v1", "play-expiry-v1.bin")
            return PlayPurchaseAuthority(PlayPurchaseService(context.packageName, identity, PlayPurchaseService::http,
                reserveSequence={PlayPurchaseService.reserveSequence(context,identity)}),
                context.packageName, identity.installation, PlayPurchaseGrantVerifier(), cache::read, cache::write,
                cache::invalidate, ExpiryObservation(floor::read, floor::write),
                boot={android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)})
        }
    }
}
