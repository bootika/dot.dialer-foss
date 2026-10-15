package dev.goodwy.rphone.controller.util

import android.content.Context
import android.annotation.SuppressLint
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal enum class PhotoScanFailureKind {
    Network, RateLimit, GoogleService, InvalidImage, ContactChanged, Permission, ContactWrite, Other,
}

/** Only stable categories are persisted or displayed; raw exceptions may contain private URLs. */
internal fun photoScanFailureKind(reason: String): PhotoScanFailureKind = when {
    reason.contains("429") -> PhotoScanFailureKind.RateLimit
    Regex("HTTP 5\\d\\d").containsMatchIn(reason) -> PhotoScanFailureKind.GoogleService
    listOf("network", "offline", "timeout", "timed out", "resolve host", "connection", "socket").any { reason.contains(it, true) } -> PhotoScanFailureKind.Network
    reason.contains("invalid image", true) || reason.contains("HTTP 404") || reason.contains("HTTP 410") -> PhotoScanFailureKind.InvalidImage
    reason.contains("permission", true) -> PhotoScanFailureKind.Permission
    listOf("Changed", "Ambiguous", "Mismatch", "NotGoogle", "NotFound").any { reason.contains(it, true) } -> PhotoScanFailureKind.ContactChanged
    reason.contains("write", true) || reason.contains("photo", true) -> PhotoScanFailureKind.ContactWrite
    else -> PhotoScanFailureKind.Other
}

internal data class PhotoScanCheckpoint(
    val accountHash: String,
    val state: PhotoScanState,
    val completedIds: Set<Long> = emptySet(),
    val failures: Map<Long, PhotoScanFailureKind> = emptyMap(),
)

/** Private progress only: no account names, phone numbers, OAuth tokens, URLs or photos. */
internal class PhotoScanCheckpointStore(context: Context) {
    private val prefs = context.getSharedPreferences("google_photo_scan_progress", Context.MODE_PRIVATE)

    fun read(): PhotoScanCheckpoint? = runCatching {
        val data = JSONObject(prefs.getString("checkpoint", null) ?: return null)
        val failures = buildMap {
            val entries = data.getJSONArray("failures")
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                put(entry.getLong("id"), PhotoScanFailureKind.valueOf(entry.getString("kind")))
            }
        }
        val ids = data.getJSONArray("done")
        val completed = (0 until ids.length()).map { ids.getLong(it) }.toSet()
        val status = PhotoScanStatus.valueOf(data.getString("status"))
        PhotoScanCheckpoint(data.getString("account"), PhotoScanState(
            status = if (status == PhotoScanStatus.Running || status == PhotoScanStatus.Preparing) PhotoScanStatus.Stopped else status,
            total = data.getInt("total"), checked = data.getInt("checked"), updated = data.getInt("updated"),
            skipped = data.getInt("skipped"), failed = data.getInt("failed"),
            failureCounts = failures.values.groupingBy { it }.eachCount(),
            lastFailure = data.optString("lastFailure").takeIf { it.isNotEmpty() }?.let(PhotoScanFailureKind::valueOf),
        ), completed, failures)
    }.getOrNull()

    // KTX edit(commit = true) discards commit's Boolean; checkpoint failures must be detected.
    @SuppressLint("UseKtx")
    fun save(checkpoint: PhotoScanCheckpoint) {
        val state = checkpoint.state
        val data = JSONObject().put("account", checkpoint.accountHash).put("status", state.status.name)
            .put("total", state.total).put("checked", state.checked).put("updated", state.updated)
            .put("skipped", state.skipped).put("failed", state.failed)
            .put("lastFailure", state.lastFailure?.name.orEmpty())
            .put("done", JSONArray(checkpoint.completedIds.toList()))
            .put("failures", JSONArray(checkpoint.failures.map { (id, kind) -> JSONObject().put("id", id).put("kind", kind.name) }))
        check(prefs.edit().putString("checkpoint", data.toString()).commit()) { "Progress could not be saved" }
    }

    fun clear() { prefs.edit { clear() } }
}

internal fun photoScanAccountHash(account: String): String = MessageDigest.getInstance("SHA-256")
    .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
