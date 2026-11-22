package dev.goodwy.rphone.controller

import android.content.Context
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.license.GiftLicenseStorage
import dev.goodwy.rphone.controller.license.PlanProducts
import org.json.JSONArray
import org.json.JSONObject

internal data class PlayStoreOwnership(
    val inApps: Set<String>,
    val subscriptions: Set<String>,
) {
    val plan get() = PlanProducts.purchased(inApps + subscriptions)
    val legacyInApp get() = BuildConfig.PRODUCT_ID_X1 in inApps
    val legacySubscription get() = BuildConfig.SUBSCRIPTION_ID_X1 in subscriptions ||
        BuildConfig.SUBSCRIPTION_YEAR_ID_X1 in subscriptions
    val isPro get() = legacyInApp || legacySubscription ||
        plan == io.github.bootika.dotdialer.core.license.AppPlan.PREMIUM
}

/** Last authoritative Billing ownership, encrypted locally and excluded from backups. */
internal class PlayStoreOwnershipCache(
    private val readRecord: () -> JSONObject?,
    private val writeRecord: (JSONObject) -> Unit,
    private val invalidateRecord: () -> Unit = { error("Cache invalidation unavailable") },
) {
    private val inAppIds = PlanProducts.all.filterNot { it.subscription }.map { it.id }.toSet() +
        listOf(BuildConfig.PRODUCT_ID_X1).filter { it.isNotBlank() }
    private val subscriptionIds = PlanProducts.all.filter { it.subscription }.map { it.id }.toSet() +
        listOf(BuildConfig.SUBSCRIPTION_ID_X1, BuildConfig.SUBSCRIPTION_YEAR_ID_X1).filter { it.isNotBlank() }

    constructor(context: Context) : this(
        GiftLicenseStorage(context, "dot.dialer.play-ownership.v1", "play-ownership-v1.bin"), context,
    )

    private constructor(storage: GiftLicenseStorage, context: Context) : this(storage::read, storage::write, {
        val file = java.io.File(context.noBackupFilesDir, "play-ownership-v1.bin")
        android.util.AtomicFile(file).delete()
        if (listOf(file, java.io.File(file.path + ".bak"), java.io.File(file.path + ".new")).any { it.exists() }) {
            val keys = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keys.deleteEntry("dot.dialer.play-ownership.v1")
            check(!keys.containsAlias("dot.dialer.play-ownership.v1"))
        }
    })

    fun restore(): PlayStoreOwnership? = try {
        readRecord()?.let { record ->
            require(record.keys().asSequence().toSet() == setOf("version", "inApps", "subscriptions"))
            require(record.get("version") == 1)
            PlayStoreOwnership(
                readProducts(record.getJSONArray("inApps"), inAppIds),
                readProducts(record.getJSONArray("subscriptions"), subscriptionIds),
            )
        }
    } catch (error: Exception) {
        android.util.Log.w("PlayOwnership", "restore: ${error.javaClass.simpleName}")
        null
    }

    fun save(ownership: PlayStoreOwnership) {
        val previous = restore()
        if (previous != null && (!(ownership.inApps.containsAll(previous.inApps)) ||
                !(ownership.subscriptions.containsAll(previous.subscriptions)))) {
            // Remove superseded rights before writing; a failed replacement must not resurrect them.
            invalidateRecord()
        }
        writeRecord(
            JSONObject().put("version", 1)
                .put("inApps", JSONArray(ownership.inApps.filter { it in inAppIds }.sorted()))
                .put("subscriptions", JSONArray(ownership.subscriptions.filter { it in subscriptionIds }.sorted())),
        )
    }

    private fun readProducts(values: JSONArray, allowed: Set<String>): Set<String> {
        require(values.length() <= allowed.size)
        return (0 until values.length()).map { index ->
            val product = values.get(index)
            require(product is String && product in allowed)
            product
        }.toSet()
    }
}
