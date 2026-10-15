package dev.goodwy.rphone.controller.license

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

internal data class GiftGrant(val issued: Long, val expires: Long)

/** The APK holds only a public verification key, never a shared activation secret. */
internal class GiftGrantVerifier(publicKey: String = PUBLIC_KEY, private val entitlement: String = "pro", private val maxSeconds: Long = 604800) {
    private val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    fun verify(grant: String, audience: String, installation: String, now: Long, nonce: String? = null): GiftGrant {
        require(grant.length in 400..4096)
        val parts = grant.split('.')
        require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initVerify(key); update(parts[0].toByteArray(Charsets.US_ASCII))
        }
        require(signature.verify(Base64.getUrlDecoder().decode(parts[1])))
        val body = JSONObject(String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8))
        require(body.keys().asSequence().toSet() == setOf("v", "aud", "installation", "license", "entitlement", "iat", "exp", "nonce"))
        require(body.get("v") == 1 && body.get("aud") == audience && body.get("installation") == installation)
        require(body.get("entitlement") == entitlement && body.getString("license").matches(Regex("[a-f0-9]{32}")))
        require(body.getString("nonce").matches(Regex("[a-f0-9]{32}")))
        require(nonce == null || body.getString("nonce") == nonce)
        require((body.get("iat") is Int || body.get("iat") is Long) && (body.get("exp") is Int || body.get("exp") is Long))
        val issued = body.getLong("iat"); val expires = body.getLong("exp")
        require(issued > 0 && issued <= now + 300 && expires > now && expires > issued && expires - issued <= maxSeconds)
        return GiftGrant(issued, expires)
    }
    companion object {
        private const val PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoLL6d0YKFTntoxJAeEkI/bdgJinGutwkMNLtJnuJZOAhwDyh9PBETmGfNjxjpr1/eul2MeST1FrryZKcdaJyYTtBZ2sLGhl3EEXbbb6N8wuDkp85Hw7szh+u9v5SuwA4hSw4KSZ6E1WBPEVfWsKYQUgk4/lzVrLCwEeIUmRVuSb+bPhIuafFX/hbrgSNmI53IiQCzsFodhmCeUdMdAMYBq3wsv2JtyPSJTyqalSyM5ccKmmgaGmR5nSjl5kkhvGEp7TDm1vVi68e8aZWm3b8ypdGh87jpJ6KNac5/5A2Dhx4c5GjJ6asJsbFu5l8isa76mLj0cEEUZJQBCePAx0w10D+fk+NUUgK0nBzMdiur0Az9lEadPHRfWeGLT6a5S3dPdJIrL9YvHe+TNAyM7LQZnQK5DFuSaf7S+nTsXeofayF0i96AAuh7l8Zn0mzHdjh/qA0ruztEI+5YsqRKdeulSgS+LpCpaZBpvUkUBeS6VGei+L+8KAntKghE1THUgB5AgMBAAE="
    }
}
