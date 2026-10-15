package io.github.bootika.dotdialer.core.diagnostics

enum class DiagnosticLevel { NORMAL, DEBUG, VERBOSE;
    companion object {
        const val WINDOW_MILLIS = 24L * 60 * 60 * 1000
        const val UNTIL_MANUAL_STOP = -1L
        fun parse(value: String?): DiagnosticLevel = entries.firstOrNull { it.name == value } ?: NORMAL
        fun effective(testBuild: Boolean, selected: DiagnosticLevel, expiresAt: Long, now: Long): DiagnosticLevel =
            if (testBuild && (expiresAt == UNTIL_MANUAL_STOP ||
                    (expiresAt > now && expiresAt - now <= WINDOW_MILLIS))) selected else NORMAL
    }
}

enum class PhotoDiagnosticStage { UPDATE, METADATA, PROVIDER, HYDRATION, AUDIO }
enum class PhotoDiagnosticResult { OK, NO_MATCH, INELIGIBLE, PERMISSION_DENIED, ERROR }
enum class PhotoHydrationDiagnostic { NONE, UPDATED, AVAILABLE, DISABLED, INELIGIBLE, CONSENT, COOLDOWN, FAILED }
enum class PhotoPipelineStage { METADATA_REQUESTED, METADATA_READY, REQUESTED, DECODE_STARTED, DECODE_COMPLETED,
    NOTIFICATION_BUILT, PUBLISHED, CANCELLED, DISCARDED, FAILED }
enum class ContactPhotoApplyStage { REVIEW, TARGET, SNAPSHOT, PREFLIGHT, WRITE, VERIFY, COMPLETE }
enum class ContactPhotoApplyResult { STARTED, SUCCESS, NO_ACCOUNT, QUEUE_MISSING, INELIGIBLE, EXPIRED,
    ACCOUNT_CHANGED, PERMISSION_DENIED, SOURCE_DISABLED, IDENTITY_CHANGED, PHOTO_CHANGED, CANDIDATE_INVALID,
    SMALLER, PROVIDER_UNAVAILABLE, CONTENT_MISMATCH, FIELDS_CHANGED, TIMEOUT, IO_FAILED, CANCELLED, EXCEPTION }

enum class DiagnosticPresence { PRESENT, ABSENT, UNAVAILABLE }
enum class DiagnosticNumberForm { INTERNATIONAL_PLUS, INTERNATIONAL_PREFIX, NATIONAL, OTHER, HIDDEN, UNKNOWN }
enum class DiagnosticCallService { SYSTEM_TELEPHONY, WHATSAPP, WHATSAPP_BUSINESS, OTHER, UNKNOWN }
enum class DiagnosticBluetoothProfile { ADAPTER, HFP, HFP_AUDIO, A2DP }

/** No fields capable of carrying contact identities, URIs, account names, device names or images. */
sealed interface DiagnosticDetail {
    val level: DiagnosticLevel
    data class CallIdentity(val session: Int, val phase: Int, val direction: Int,
        val numberForm: DiagnosticNumberForm, val addressType: Int, val handlePresentation: Int,
        val callerPresentation: Int, val callerName: DiagnosticPresence, val contactName: DiagnosticPresence,
        val contactPhoto: DiagnosticPresence, val service: DiagnosticCallService,
        val selfManaged: DiagnosticPresence, val creationMillis: Long) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class BluetoothTransition(val profile: DiagnosticBluetoothProfile, val previous: Int, val current: Int) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class ContactPhotoApply(val stage: ContactPhotoApplyStage, val result: ContactPhotoApplyResult) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class PhotoPipeline(val session: Int, val attempt: Int, val stage: PhotoPipelineStage,
        val elapsedMs: Long, val stepMs: Long, val photoUriPresent: Boolean, val decodedPhoto: Boolean) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class PhotoState(
        val session: Int, val stage: PhotoDiagnosticStage, val phase: Int,
        val metadataAvailable: Boolean, val localContact: Boolean, val metadataPhoto: Boolean,
        val telecomPhoto: Boolean, val hydration: PhotoHydrationDiagnostic = PhotoHydrationDiagnostic.NONE,
    ) : DiagnosticDetail { override val level = DiagnosticLevel.DEBUG }
    data class AudioState(val route: Int, val bluetoothAvailable: Boolean, val bluetoothPermission: Boolean,
        val supportedRouteMask: Int = 0) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class ConnectionState(val bluetoothPermission: Boolean, val adapterState: Int,
        val handsFreeState: Int, val mediaState: Int, val projectionType: Int, val uiMode: Int) : DiagnosticDetail {
        override val level = DiagnosticLevel.DEBUG
    }
    data class PhotoProbe(
        val session: Int, val result: PhotoDiagnosticResult, val matches: Int = 0,
        val rawSources: Int = 0, val googleSources: Int = 0, val samsungSources: Int = 0,
        val otherSources: Int = 0, val primaryPhotos: Int = 0,
        val fullPhotos: Int = 0, val thumbnails: Int = 0,
        val readableFull: Int = 0, val readableThumbnails: Int = 0,
        val maxFullWidth: Int = 0, val maxFullHeight: Int = 0,
        val maxThumbnailWidth: Int = 0, val maxThumbnailHeight: Int = 0,
        val readableRawFull: Int = 0, val maxRawFullWidth: Int = 0, val maxRawFullHeight: Int = 0,
    ) : DiagnosticDetail { override val level = DiagnosticLevel.VERBOSE }
}

object DiagnosticDetailFormatter {
    fun format(event: DiagnosticDetail): String = when (event) {
        is DiagnosticDetail.CallIdentity -> "call_identity | session=call-${event.session} | phase=${event.phase}" +
            " | direction=${event.direction} | number_form=${event.numberForm} | address_type=${event.addressType}" +
            " | handle_presentation=${event.handlePresentation} | caller_presentation=${event.callerPresentation}" +
            " | caller_name=${event.callerName} | system_contact_name=${event.contactName} | system_contact_photo=${event.contactPhoto}" +
            " | service=${event.service} | self_managed=${event.selfManaged} | creation_ms=${event.creationMillis}"
        is DiagnosticDetail.BluetoothTransition -> "bluetooth_transition | profile=${event.profile}" +
            " | previous=${event.previous} | current=${event.current} | hci_packets=external_capture_required"
        is DiagnosticDetail.ContactPhotoApply -> "contact_photo_apply | stage=${event.stage} | result=${event.result}"
        is DiagnosticDetail.PhotoPipeline -> "call_photo_pipeline | session=call-${event.session} | attempt=${event.attempt}" +
            " | stage=${event.stage} | elapsed_ms=${event.elapsedMs} | step_ms=${event.stepMs}" +
            " | photo_uri=${event.photoUriPresent} | decoded_photo=${event.decodedPhoto}"
        is DiagnosticDetail.PhotoState -> "call_photo | session=call-${event.session} | stage=${event.stage} | phase=${event.phase}" +
            " | metadata=${event.metadataAvailable} | local_contact=${event.localContact} | metadata_photo=${event.metadataPhoto}" +
            " | telecom_photo=${event.telecomPhoto} | hydration=${event.hydration}"
        is DiagnosticDetail.AudioState -> "call_audio | route=${event.route} | bluetooth_available=${event.bluetoothAvailable}" +
            " | bluetooth_permission=${event.bluetoothPermission} | supported_route_mask=${event.supportedRouteMask}"
        is DiagnosticDetail.ConnectionState -> "car_connection | bluetooth_permission=${event.bluetoothPermission}" +
            " | adapter_state=${event.adapterState} | hfp_state=${event.handsFreeState} | a2dp_state=${event.mediaState}" +
            " | projection_type=${event.projectionType} | ui_mode=${event.uiMode} | pbap_traffic=system_log_required"
        is DiagnosticDetail.PhotoProbe -> "call_photo_probe | session=call-${event.session} | result=${event.result} | matches=${event.matches}" +
            " | raw_sources=${event.rawSources} | google_sources=${event.googleSources} | samsung_sources=${event.samsungSources}" +
            " | other_sources=${event.otherSources} | primary_photos=${event.primaryPhotos} | full_photos=${event.fullPhotos}" +
            " | thumbnails=${event.thumbnails} | readable_full=${event.readableFull} | readable_thumbnails=${event.readableThumbnails}" +
            " | full_max=${event.maxFullWidth}x${event.maxFullHeight} | thumbnail_max=${event.maxThumbnailWidth}x${event.maxThumbnailHeight}" +
            " | readable_raw_full=${event.readableRawFull} | raw_full_max=${event.maxRawFullWidth}x${event.maxRawFullHeight}"
    }
}
