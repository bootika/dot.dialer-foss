package dev.goodwy.rphone.controller.license

import io.github.bootika.dotdialer.core.license.AppPlan
import io.github.bootika.dotdialer.core.license.PlanEntitlements
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

internal data class PlanGrant(val entitlements: PlanEntitlements, val expires: Long)

internal class PlanGrantVerifier(publicKey: String = GiftGrantVerifier.PUBLIC_KEY) {
    companion object { const val MAX_OFFLINE_SECONDS = 10 * 86400L }
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    fun verify(grant: String, audience: String, installation: String, now: Long, nonce: String? = null): PlanGrant {
        require(grant.length in 400..4096)
        val parts = grant.split('.')
        require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val signature = Signature.getInstance("SHA256withRSA").apply { initVerify(key); update(parts[0].toByteArray(Charsets.US_ASCII)) }
        require(signature.verify(Base64.getUrlDecoder().decode(parts[1])))
        val body = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8))
        require(body.keys().asSequence().toSet() == setOf("v", "aud", "installation", "license", "entitlement", "tier", "trial_started", "trial_ends", "iat", "exp", "nonce"))
        require(body.get("v") == 1 && body.get("aud") == audience && body.get("installation") == installation && body.get("entitlement") == "plan_access")
        require(body.getString("license").matches(Regex("[a-f0-9]{32}")) && body.getString("nonce").matches(Regex("[a-f0-9]{32}")))
        require(nonce == null || body.getString("nonce") == nonce)
        for (field in listOf("iat", "exp", "trial_started", "trial_ends")) require(body.get(field) is Int || body.get(field) is Long)
        val issued = body.getLong("iat"); val expires = body.getLong("exp")
        require(issued > 0 && issued <= now + 300 && expires > now && expires > issued && expires - issued <= MAX_OFFLINE_SECONDS)
        val trial = body.getLong("trial_started"); val end = body.getLong("trial_ends")
        require((trial == 0L && end == 0L) || (trial > 0 && trial <= issued && end - trial == 30 * 86400L))
        val tier = when (body.getString("tier")) { "free" -> AppPlan.FREE; "plus" -> AppPlan.PLUS; "premium" -> AppPlan.PREMIUM; else -> error("Invalid tier") }
        return PlanGrant(PlanEntitlements(tier, trial, end), expires)
    }
}
