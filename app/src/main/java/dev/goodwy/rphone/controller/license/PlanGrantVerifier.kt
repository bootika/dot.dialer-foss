package dev.goodwy.rphone.controller.license

import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.license.PlanEntitlements
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

internal data class PlanGrant(
    val entitlements: PlanEntitlements,
    val expires: Long,
    val paidTerms: Map<AppPlan, Long?>? = null,
) {
    /** Compare the whole remaining rights envelope, including a lower tier surviving a higher one. */
    fun reduces(previous: PlanGrant): Boolean {
        fun rights(grant: PlanGrant): List<Pair<AppPlan, Long?>> = buildList {
            val terms = grant.paidTerms ?: mapOf(grant.entitlements.paid to grant.expires)
            terms.filterKeys { it != AppPlan.FREE }.forEach { (plan, end) -> add(plan to end) }
            if (grant.entitlements.trialEnds > 0) add(AppPlan.PREMIUM to grant.entitlements.trialEnds)
        }
        val next = rights(this)
        return rights(previous).any { (oldPlan, oldEnd) ->
            next.none { (plan, end) -> plan >= oldPlan && (end == null || oldEnd != null && end >= oldEnd) }
        }
    }

    fun entitlementsAt(time: Long, leaseValid: Boolean, clockTrusted: Boolean): PlanEntitlements {
        val terms = paidTerms ?: return if (leaseValid) entitlements else PlanEntitlements()
        val paid = terms.filterValues { end -> end == null || (clockTrusted && time < end) }
            .keys.maxOrNull() ?: AppPlan.FREE
        return entitlements.copy(paid = paid, trialEnds = if (clockTrusted) entitlements.trialEnds else 0)
    }
}

internal class PlanGrantVerifier(publicKey: String = GiftGrantVerifier.PUBLIC_KEY) {
    companion object { const val MAX_OFFLINE_SECONDS = 10 * 86400L }
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    fun verify(grant: String, audience: String, installation: String, now: Long, nonce: String? = null): PlanGrant =
        verify(grant, audience, installation, now, nonce, cached = false)

    fun verifyCached(grant: String, audience: String, installation: String, now: Long): PlanGrant =
        verify(grant, audience, installation, now, nonce = null, cached = true)

    private fun verify(
        grant: String, audience: String, installation: String, now: Long, nonce: String?, cached: Boolean,
    ): PlanGrant {
        require(grant.length in 400..4096)
        val parts = grant.split('.')
        require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val signature = Signature.getInstance("SHA256withRSA").apply { initVerify(key); update(parts[0].toByteArray(Charsets.US_ASCII)) }
        require(signature.verify(Base64.getUrlDecoder().decode(parts[1])))
        val body = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8))
        val version = body.get("v")
        require(version == 1 || version == 2)
        val baseFields = setOf("v", "aud", "installation", "license", "entitlement", "tier", "trial_started", "trial_ends", "iat", "exp", "nonce")
        require(body.keys().asSequence().toSet() == if (version == 2) baseFields + "paid_terms" else baseFields)
        require(body.get("aud") == audience && body.get("installation") == installation && body.get("entitlement") == "plan_access")
        require(body.getString("license").matches(Regex("[a-f0-9]{32}")) && body.getString("nonce").matches(Regex("[a-f0-9]{32}")))
        require(nonce == null || body.getString("nonce") == nonce)
        for (field in listOf("iat", "exp", "trial_started", "trial_ends")) require(body.get(field) is Int || body.get(field) is Long)
        val issued = body.getLong("iat"); val expires = body.getLong("exp")
        require(issued > 0 && expires > issued && expires - issued <= MAX_OFFLINE_SECONDS)
        if (!cached || version == 1) require(issued <= now + 300 && expires > now)
        val trial = body.getLong("trial_started"); val end = body.getLong("trial_ends")
        require((trial == 0L && end == 0L) || (trial > 0 && trial <= issued && end - trial == 30 * 86400L))
        val tier = when (body.getString("tier")) { "free" -> AppPlan.FREE; "plus" -> AppPlan.PLUS; "premium" -> AppPlan.PREMIUM; else -> error("Invalid tier") }
        val paidTerms = if (version == 2) {
            val terms = body.getJSONObject("paid_terms")
            terms.keys().asSequence().associate { name ->
                val plan = when (name) {
                    "plus" -> AppPlan.PLUS
                    "premium" -> AppPlan.PREMIUM
                    else -> error("Invalid paid plan")
                }
                plan to when (val value = terms.get(name)) {
                    JSONObject.NULL -> null
                    is Int, is Long -> (value as Number).toLong().also { require(it > issued) }
                    else -> error("Invalid paid term")
                }
            }.also { require((it.keys.maxOrNull() ?: AppPlan.FREE) == tier) }
        } else null
        return PlanGrant(PlanEntitlements(tier, trial, end), expires, paidTerms)
    }
}
