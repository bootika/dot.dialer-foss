package dev.goodwy.rphone.recordings

/** Bounded wire codes only; never preserve exception/provider response text. */
internal object RecordingProviderFailure {
    private val codes = setOf("license_required", "api_key_required", "provider_key_rejected",
        "rate_limited", "provider_unavailable", "provider_rejected", "network_unavailable",
        "provider_response_invalid", "empty_transcript", "invalid_request", "internal_error",
        "storage_unavailable", "unsupported_audio", "input_too_large")
    fun code(value: String?): String = value?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it in codes }?.uppercase(java.util.Locale.ROOT) ?: "UNKNOWN_PROVIDER_ERROR"
}
