package io.github.bootika.dotdialer.core.recordings

import io.github.bootika.dotdialer.core.license.AppPlan

/** Local playback/export are deliberately independent from processing rights. */
enum class RecordingAccess { READY, PLAN_REQUIRED, DISCONNECTED, LICENSE_REQUIRED, LICENSE_UNAVAILABLE, KEY_REQUIRED, UNSUPPORTED }
data class DictateCapabilities(
    val connected: Boolean = false, val licenseState: String = "unavailable",
    val apiKeyConfigured: Boolean = false, val transcription: Boolean = false,
    val translation: Boolean = false, val maxInputBytes: Long = 0, val maxInputSeconds: Long = 0, val targetLanguages: List<String> = emptyList(),
    val maxChunkBytes: Long = 0, val maxChunkSeconds: Long = 0, val segmentedAudioMimeTypes: List<String> = emptyList(),
)
object RecordingPolicy {
    fun processing(plan: AppPlan, capabilities: DictateCapabilities, translate: Boolean = false): RecordingAccess = when {
        plan < AppPlan.PLUS -> RecordingAccess.PLAN_REQUIRED
        !capabilities.connected -> RecordingAccess.DISCONNECTED
        capabilities.licenseState == "required" -> RecordingAccess.LICENSE_REQUIRED
        capabilities.licenseState != "valid" -> RecordingAccess.LICENSE_UNAVAILABLE
        !capabilities.apiKeyConfigured -> RecordingAccess.KEY_REQUIRED
        (if (translate) (!capabilities.translation || capabilities.targetLanguages.isEmpty()) else !capabilities.transcription) -> RecordingAccess.UNSUPPORTED
        else -> RecordingAccess.READY
    }
    fun automatic(plan: AppPlan, optedIn: Boolean, capabilities: DictateCapabilities): Boolean =
        plan == AppPlan.PREMIUM && optedIn && processing(plan, capabilities) == RecordingAccess.READY
    fun acceptedInput(bytes: Long, durationMs: Long, capabilities: DictateCapabilities): Boolean =
        bytes > 0 && durationMs > 0 && capabilities.maxInputBytes > 0 && capabilities.maxInputSeconds > 0 &&
            bytes <= capabilities.maxInputBytes && durationMs / 1000.0 <= capabilities.maxInputSeconds
    fun acceptedAudio(bytes: Long,durationMs: Long,codec: String,capabilities: DictateCapabilities): Boolean =
        acceptedInput(bytes,durationMs,capabilities) && capabilities.maxChunkBytes>0 && capabilities.maxChunkSeconds>0 &&
            (bytes<=capabilities.maxChunkBytes && durationMs<=capabilities.maxChunkSeconds*1000 || codec in capabilities.segmentedAudioMimeTypes)
    fun progress(completed: Int, total: Int): Pair<Int, Int>? =
        if (total > 0 && completed in 0..total) completed to total else null
}
