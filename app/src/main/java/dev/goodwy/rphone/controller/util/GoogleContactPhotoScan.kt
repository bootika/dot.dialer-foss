package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

internal data class PhotoScanCandidate(val contactId: Long, val phoneNumber: String)

internal enum class PhotoScanStatus {
    Idle, Preparing, Running, Completed, Stopped, ConsentRequired, PermissionMissing, Failed,
}

internal data class PhotoScanState(
    val status: PhotoScanStatus = PhotoScanStatus.Idle,
    val total: Int = 0,
    val checked: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val stopping: Boolean = false,
    val failureCounts: Map<PhotoScanFailureKind, Int> = emptyMap(),
    val lastFailure: PhotoScanFailureKind? = null,
) {
    val running: Boolean get() = status == PhotoScanStatus.Preparing || status == PhotoScanStatus.Running
    val canResume: Boolean get() = checked < total && status in setOf(PhotoScanStatus.Stopped, PhotoScanStatus.Failed, PhotoScanStatus.PermissionMissing, PhotoScanStatus.ConsentRequired)
}

/** A sequential, cancellable scan. No names, numbers, tokens or photos are logged or cached on disk. */
internal suspend fun runPhotoScan(
    candidates: List<PhotoScanCandidate>,
    shouldStop: () -> Boolean,
    canWrite: () -> Boolean,
    hydrate: suspend (PhotoScanCandidate) -> CallPhotoHydrationOutcome,
    publish: (PhotoScanState) -> Unit,
    initial: PhotoScanState = PhotoScanState(),
    retryTransient: Boolean = false,
    retryPause: suspend (Long) -> Unit = { delay(it) },
    onOutcome: (PhotoScanCandidate, CallPhotoHydrationOutcome) -> Unit = { _, _ -> },
): PhotoScanState {
    var state = initial.copy(status = PhotoScanStatus.Running, total = initial.checked + candidates.size, stopping = false, lastFailure = null)
    publish(state)
    for (candidate in candidates) {
        if (shouldStop()) return state.copy(status = PhotoScanStatus.Stopped).also(publish)
        if (!canWrite()) return state.copy(status = PhotoScanStatus.PermissionMissing).also(publish)
        var outcome = hydrate(candidate)
        if (retryTransient) {
            var attempts = 0
            while (outcome is CallPhotoHydrationOutcome.Failed && outcome.abortScan &&
                photoScanFailureKind(outcome.reason) in setOf(PhotoScanFailureKind.Network, PhotoScanFailureKind.RateLimit, PhotoScanFailureKind.GoogleService) &&
                attempts < 2 && !shouldStop()) {
                retryPause(if (attempts++ == 0) 1_000L else 3_000L)
                if (shouldStop()) return state.copy(status = PhotoScanStatus.Stopped).also(publish)
                if (!canWrite()) return state.copy(status = PhotoScanStatus.PermissionMissing).also(publish)
                outcome = hydrate(candidate)
            }
        }
        if (outcome == CallPhotoHydrationOutcome.ConsentRequired || outcome == CallPhotoHydrationOutcome.Disabled) {
            return state.copy(status = PhotoScanStatus.ConsentRequired).also(publish)
        }
        onOutcome(candidate, outcome)
        state = when (outcome) {
            CallPhotoHydrationOutcome.Updated -> state.copy(checked = state.checked + 1, updated = state.updated + 1)
            is CallPhotoHydrationOutcome.Failed -> {
                val kind = photoScanFailureKind(outcome.reason)
                state.copy(checked = state.checked + 1, failed = state.failed + 1,
                    lastFailure = kind, failureCounts = state.failureCounts + (kind to ((state.failureCounts[kind] ?: 0) + 1)))
            }
            else -> state.copy(checked = state.checked + 1, skipped = state.skipped + 1)
        }
        publish(state)
        if (shouldStop()) return state.copy(status = PhotoScanStatus.Stopped).also(publish)
        if (outcome is CallPhotoHydrationOutcome.Failed && outcome.abortScan) {
            return state.copy(status = PhotoScanStatus.Failed).also(publish)
        }
    }
    return state.copy(status = PhotoScanStatus.Completed).also(publish)
}

/** User-initiated foreground service owns the run; checkpoints support explicit resumption. */
internal class GoogleContactPhotoScan(
    context: Context,
    private val preferences: PreferenceManager,
    private val authorization: GoogleContactsAuthorization,
    private val hydrationManager: CallPhotoHydrationManager,
    private val candidateLoader: (suspend (String) -> List<PhotoScanCandidate>)? = null,
    private val featureAllowed: () -> Boolean = { true },
) {
    private val appContext = context.applicationContext
    private val checkpoints = PhotoScanCheckpointStore(appContext)
    private fun accountHash() = preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null)?.let(::photoScanAccountHash)
    private fun currentCheckpoint() = checkpoints.read()?.takeIf { it.accountHash == accountHash() && preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false) }
    private val mutableState = MutableStateFlow(currentCheckpoint()?.state ?: PhotoScanState())
    val state: StateFlow<PhotoScanState> = mutableState.asStateFlow()
    private val stop = AtomicBoolean(false)

    @Synchronized
    fun start() = requestStart(retryOnly = false)

    fun retryFailed() = requestStart(retryOnly = true)

    @Synchronized
    private fun requestStart(retryOnly: Boolean) {
        if (!featureAllowed() || mutableState.value.running) return
        stop.set(false)
        mutableState.value = mutableState.value.copy(status = PhotoScanStatus.Preparing, stopping = false)
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, GoogleContactPhotoScanService::class.java)
                .putExtra(GoogleContactPhotoScanService.RETRY_ONLY, retryOnly))
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(status = PhotoScanStatus.Failed, lastFailure = PhotoScanFailureKind.Other)
        }
    }

    /** Also used by approved one-contact instrumentation tests, without starting a second scan. */
    internal suspend fun executeFromService(retryOnly: Boolean = false) {
        if(!featureAllowed()) return
        var activeCheckpoint: PhotoScanCheckpoint? = null
        fun publish(next: PhotoScanState) {
            val state = next.copy(stopping = stop.get() && next.running)
            mutableState.value = state
            activeCheckpoint = activeCheckpoint?.copy(state = state)
            if (activeCheckpoint?.accountHash == accountHash() && preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)) {
                activeCheckpoint?.let(checkpoints::save)
            }
        }
            try {
                val account = preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null)
                if (!preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false) || account == null) {
                    mutableState.value = mutableState.value.copy(status = PhotoScanStatus.ConsentRequired)
                    return
                }
                if (!hasPermissions()) {
                    mutableState.value = mutableState.value.copy(status = PhotoScanStatus.PermissionMissing)
                    return
                }
                val allCandidates = candidateLoader?.invoke(account) ?: loadCandidates(account)
                val saved = currentCheckpoint()
                val resume = saved != null && (saved.state.canResume || retryOnly)
                val completed = if (resume) saved.completedIds - saved.failures.keys else emptySet()
                val candidates = allCandidates.filter { it.contactId !in completed && (!retryOnly || it.contactId in saved?.failures.orEmpty()) }
                val initial = if (resume) saved.state.copy(checked = saved.state.checked - saved.state.failed, failed = 0, failureCounts = emptyMap()) else PhotoScanState()
                activeCheckpoint = PhotoScanCheckpoint(photoScanAccountHash(account), initial, completed)
                publish(initial.copy(status = PhotoScanStatus.Preparing, total = initial.checked + candidates.size))
                if (stop.get()) {
                    publish(mutableState.value.copy(status = PhotoScanStatus.Stopped))
                    return
                }
                val auth = authorization.authorize(account)
                if (auth !is GoogleContactsAuthorizationResult.Granted || auth.accountName != account) {
                    publish(mutableState.value.copy(status =
                        if (auth is GoogleContactsAuthorizationResult.Failed) PhotoScanStatus.Failed
                        else PhotoScanStatus.ConsentRequired,
                    ))
                    return
                }
                val source = GooglePeoplePhotoClient(cacheConnections = true)
                runPhotoScan(
                    candidates = candidates,
                    shouldStop = {stop.get() || !featureAllowed()},
                    canWrite = {featureAllowed() && hasPermissions()},
                    initial = initial,
                    retryTransient = true,
                    hydrate = { candidate ->
                        hydrationManager.hydrateForScan(
                            candidate.phoneNumber, account, candidate.contactId, auth.accessToken, source,
                        )
                    },
                    publish = ::publish,
                    onOutcome = { candidate, outcome ->
                        activeCheckpoint = activeCheckpoint?.let { checkpoint -> checkpoint.copy(
                            completedIds = checkpoint.completedIds + candidate.contactId,
                            failures = if (outcome is CallPhotoHydrationOutcome.Failed) checkpoint.failures + (candidate.contactId to photoScanFailureKind(outcome.reason)) else checkpoint.failures - candidate.contactId,
                        ) }
                    },
                )
            } catch (error: SecurityException) {
                publish(mutableState.value.copy(status = PhotoScanStatus.PermissionMissing, stopping = false))
            } catch (error: CancellationException) {
                publish(mutableState.value.copy(status = PhotoScanStatus.Stopped, stopping = false))
                throw error
            } catch (error: Exception) {
                val next = mutableState.value.copy(status = PhotoScanStatus.Failed, stopping = false, lastFailure = photoScanFailureKind(error.message.orEmpty()))
                mutableState.value = next
                runCatching { publish(next) }
            }
    }

    fun clearProgress() { checkpoints.clear(); if (!state.value.running) mutableState.value = PhotoScanState() }

    internal fun serviceInterrupted() {
        stop.set(true)
        if (state.value.running) mutableState.value = state.value.copy(status = PhotoScanStatus.Stopped, stopping = false)
    }

    internal fun serviceStartFailed() {
        mutableState.value = mutableState.value.copy(status = PhotoScanStatus.Failed, lastFailure = PhotoScanFailureKind.Other)
    }

    fun cancel() {
        // Finish and verify a photo already being written; stop before touching the next contact.
        if (mutableState.value.running) {
            stop.set(true)
            mutableState.value = mutableState.value.copy(stopping = true)
        }
    }

    private fun hasPermissions(): Boolean = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        .all { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }

    private fun loadCandidates(account: String): List<PhotoScanCandidate> {
        val resolver = appContext.contentResolver
        val rawIds = mutableSetOf<Long>()
        requireNotNull(resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf("com.google", account), null,
        )).use { cursor -> while (cursor.moveToNext()) rawIds += cursor.getLong(0) }
        val candidates = linkedMapOf<Long, PhotoScanCandidate>()
        requireNotNull(resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC",
        )).use { cursor ->
            while (cursor.moveToNext() && !stop.get()) {
                if (cursor.getLong(0) !in rawIds) continue
                val number = cursor.getString(2)?.takeIf { it.count(Char::isDigit) >= 9 } ?: continue
                val id = cursor.getLong(1)
                candidates.putIfAbsent(id, PhotoScanCandidate(id, number))
            }
        }
        return candidates.values.toList()
    }
}
