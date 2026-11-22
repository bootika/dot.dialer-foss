package dev.goodwy.rphone.controller.license

import java.security.MessageDigest
import org.json.JSONObject

/** Authenticated local high-water mark, scoped to one signed grant, never a new entitlement. */
internal class ExpiryObservation(
    private val read: () -> JSONObject?,
    private val write: (JSONObject) -> Unit,
) {
    private fun fingerprint(grant: String): String = MessageDigest.getInstance("SHA-256")
        .digest(grant.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun floor(grant: String): Long {
        val record = read() ?: return 0
        return if (record.optString("grant_hash") == fingerprint(grant)) record.getLong("clock_floor") else 0
    }

    fun save(grant: String, floor: Long) = write(JSONObject()
        .put("grant_hash", fingerprint(grant)).put("clock_floor", floor))
}
