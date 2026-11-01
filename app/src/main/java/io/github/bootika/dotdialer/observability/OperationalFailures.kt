package io.github.bootika.dotdialer.observability

import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

/** Bounded technical codes only; never accepts content, paths, phone numbers or exception messages. */
internal object OperationalFailures {
    private val stages = setOf("bridge_reply", "bridge_sync", "library", "playback", "capture", "capture_finalize", "capture_recover")
    private val codes = setOf("invalid_response", "storage", "permission", "internal", "provider_response_invalid", "provider_unavailable")
    private var sent = 0
    private val last = mutableMapOf<String, Long>()
    @Synchronized fun event(stage: String, code: String, now: Long): SentryEvent? {
        if (stage !in stages || code !in codes) return null
        val key = "$stage:$code"
        if (last[key]?.let { now - it < 300_000 } == true) return null
        // Hard process budget prevents changing failures from consuming the whole free quota.
        if (sent >= 20) return null
        sent++
        last[key] = now
        return SentryEvent().apply {
            level = SentryLevel.ERROR
            message = Message().apply { message = "dot_dialer_operational_failure" }
            setTag("module", "recordings")
            setTag("stage", stage)
            setTag("error_code", code)
            fingerprints = listOf("dot_dialer_operational_failure", stage, code)
        }
    }
}
