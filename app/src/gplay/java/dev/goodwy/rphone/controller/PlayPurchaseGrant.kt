package dev.goodwy.rphone.controller

import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.license.GiftGrantVerifier
import dev.goodwy.rphone.controller.license.PlanProducts
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

/** Server authority over the Play source only. A refresh lease is never a paid-through date. */
internal class PlayPurchaseGrant(
    val issued: Long,
    val leaseUntil: Long,
    val principal: String,
    val purchaseAvailable: Boolean,
    val operation: String,
    val authoritative: Boolean,
    val ownershipComplete: Boolean,
    val sequence: Long,
    private val products: List<Product>,
) {
    internal data class Product(val id: String, val subscription: Boolean, val until: Long?)

    fun ownership(time: Long): PlayStoreOwnership {
        val active = products.filter { it.until == null || time < it.until }
        return PlayStoreOwnership(active.filterNot { it.subscription }.map { it.id }.toSet(),
            active.filter { it.subscription }.map { it.id }.toSet())
    }

    fun observedBoundary(time: Long): Long = products.mapNotNull { it.until }.filter { it <= time }.maxOrNull() ?: 0
    fun reduces(previous: PlayPurchaseGrant): Boolean = previous.products.any { old ->
        products.none { next -> next.id == old.id && (next.until == null || old.until != null && next.until >= old.until) }
    }
}

internal class PlayPurchaseGrantVerifier(publicKey: String = GiftGrantVerifier.PUBLIC_KEY) {
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    private val catalogue = PlanProducts.all.associate { it.id to (it.subscription to it.tier.name.lowercase(java.util.Locale.ROOT)) } +
        listOf(BuildConfig.PRODUCT_ID_X1 to false, BuildConfig.SUBSCRIPTION_ID_X1 to true,
            BuildConfig.SUBSCRIPTION_YEAR_ID_X1 to true).filter { it.first.isNotBlank() }
            .associate { it.first to (it.second to "premium") }

    fun verify(envelope: JSONObject, audience: String, installation: String, nonce: String? = null,
        sequence: Long? = null,
        cached: Boolean = false): PlayPurchaseGrant {
        require(envelope.keys().asSequence().toSet() == setOf("payload", "signature"))
        val encoded = envelope.getString("payload")
        val signature = envelope.getString("signature")
        require(encoded.length in 40..16000 && signature.length in 200..2000)
        require(listOf(encoded, signature).all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(key); update(encoded.toByteArray(Charsets.US_ASCII))
        }
        require(verifier.verify(Base64.getUrlDecoder().decode(signature)))
        val body = JSONObject(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
        require(body.keys().asSequence().toSet() == setOf("v", "source", "aud", "installation", "nonce", "iat", "exp", "products", "principal", "purchase_available", "operation", "authoritative", "ownership_complete", "sequence"))
        require(body.get("v") == 1 && body.get("source") == "google_play")
        require(body.get("aud") == audience && body.get("installation") == installation)
        val principal = body.getString("principal")
        require(principal.matches(Regex("[a-f0-9]{64}")))
        require(listOf("purchase_available", "authoritative", "ownership_complete").all {body.get(it) is Boolean})
        val operation=body.getString("operation")
        require(operation in setOf("register", "verify", "refresh"))
        require(body.getBoolean("authoritative") == (operation != "register"))
        require(!body.getBoolean("ownership_complete") || operation == "verify")
        require(body.getString("nonce").matches(Regex("[a-f0-9]{64}")))
        require(nonce == null || body.getString("nonce") == nonce)
        require(body.get("sequence") is Int || body.get("sequence") is Long)
        val signedSequence=body.getLong("sequence")
        require(signedSequence in 1..9_007_199_254_740_991L && (sequence == null || sequence == signedSequence))
        require(listOf("iat", "exp").all { body.get(it) is Int || body.get(it) is Long })
        val issued = body.getLong("iat"); val expires = body.getLong("exp")
        require(issued > 0 && expires > issued && expires - issued <= 7 * 86400L)
        // The live request nonce and sequence prove freshness even when the phone wall clock is wrong.
        if (!cached) require(nonce != null && sequence != null)
        val items = body.getJSONArray("products")
        require(items.length() <= catalogue.size)
        val seen = mutableSetOf<String>()
        val products = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("id", "type", "entitlement", "access_until"))
            val id = item.getString("id")
            val expected = requireNotNull(catalogue[id])
            require(seen.add(id))
            require(item.get("type") == if (expected.first) "subs" else "inapp")
            require(item.get("entitlement") == expected.second)
            val end = when (val value = item.get("access_until")) {
                JSONObject.NULL -> null
                is Int, is Long -> (value as Number).toLong().also { require(it > 0) }
                else -> error("Invalid paid-through date")
            }
            require(!expected.first || end != null)
            PlayPurchaseGrant.Product(id, expected.first, end)
        }
        return PlayPurchaseGrant(issued, expires, principal, body.getBoolean("purchase_available"), operation,
            body.getBoolean("authoritative"), body.getBoolean("ownership_complete"), signedSequence, products)
    }
}
