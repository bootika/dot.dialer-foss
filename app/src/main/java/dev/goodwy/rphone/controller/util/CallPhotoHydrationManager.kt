package dev.goodwy.rphone.controller.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private const val MIN_USEFUL_PHOTO_EDGE = 512
private const val SUCCESS_REFRESH_MILLIS = 30L * 24 * 60 * 60 * 1_000
private const val NOT_FOUND_RETRY_MILLIS = 24L * 60 * 60 * 1_000
private const val AMBIGUOUS_RETRY_MILLIS = 7L * 24 * 60 * 60 * 1_000
private const val TRANSIENT_RETRY_MILLIS = 60L * 60 * 1_000
private const val COOLDOWN_KEY_PREFIX = "high_res_call_photo_retry_"

internal sealed interface CallPhotoHydrationOutcome {
    data object Updated : CallPhotoHydrationOutcome
    data object AlreadyAvailable : CallPhotoHydrationOutcome
    data object Disabled : CallPhotoHydrationOutcome
    data object NotEligible : CallPhotoHydrationOutcome
    data object ConsentRequired : CallPhotoHydrationOutcome
    data object Cooldown : CallPhotoHydrationOutcome
    data class Failed(val reason: String, val abortScan: Boolean = false) : CallPhotoHydrationOutcome
}

/**
 * Coordinates the lazy call-screen hydration flow. It never starts consent UI and never blocks
 * telecom controls; the call service owns the coroutine independently of any visible Activity.
 */
internal class CallPhotoHydrationManager(
    context: Context,
    private val preferences: PreferenceManager,
    private val authorization: GoogleContactsAuthorization,
    private val peopleClient: GooglePeoplePhotoSource,
    private val hydrator: GoogleContactPhotoStore = GoogleContactPhotoHydrator(context),
    private val cooldownStore: PhotoHydrationCooldownStore = PhotoHydrationCooldownStore(context),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isFeatureEnabled: () -> Boolean = {
        preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
    },
    private val selectedAccountName: () -> String? = {
        preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null)
    },
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val inMemoryRetryAt = ConcurrentHashMap<String, Long>()

    fun clearLocalState() {
        inMemoryRetryAt.clear()
        cooldownStore.clear()
    }

    /** D107: a call/legacy scan is never authority to change a user's selected avatar. */
    suspend fun hydrateForCall(phoneNumber: String): CallPhotoHydrationOutcome =
        if (phoneNumber.isBlank()) CallPhotoHydrationOutcome.NotEligible else CallPhotoHydrationOutcome.Disabled

    @Suppress("UNUSED_PARAMETER") // Compatibility entry point is intentionally read-only after D107.
    suspend fun hydrateForScan(phoneNumber: String, accountName: String, expectedContactId: Long,
        accessToken: String, source: GooglePeoplePhotoSource): CallPhotoHydrationOutcome =
        CallPhotoHydrationOutcome.Disabled

    /** Read-only preview source; one in-memory paged index per user-initiated scan. */
    suspend fun previewSource(account: String): GooglePeoplePhotoSource? {
        if (!isFeatureEnabled() || !authorization.isAvailable || selectedAccountName() != account) return null
        val auth = authorization.authorize(account)
        if (auth !is GoogleContactsAuthorizationResult.Granted || auth.accountName != account) return null
        val source = GooglePeoplePhotoClient(cacheConnections = true)
        return object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult {
                if (!isFeatureEnabled() || selectedAccountName() != account) return GooglePeoplePhotoResult.AuthorizationExpired
                return source.loadPhoto(auth.accessToken, phoneNumber)
            }
        }
    }

    /** Explicit post-avatar refresh: never reuse a call cooldown or silently switch accounts. */
    suspend fun hydrateForContactUpdate(phoneNumber: String, accountName: String,
        contactId: Long, rawContactId: Long, stillAllowed: () -> Boolean = { true }): CallPhotoHydrationOutcome {
        if (!stillAllowed() || !isFeatureEnabled() || !authorization.isAvailable) return CallPhotoHydrationOutcome.Disabled
        if (selectedAccountName() != accountName) return CallPhotoHydrationOutcome.ConsentRequired
        val identityKey = cooldownIdentity(accountName, phoneNumber)
        return locks.getOrPut(identityKey) { Mutex() }.withLock {
            val local = runCatching { hydrator.snapshot(phoneNumber, accountName) }.getOrNull()
                ?: return@withLock CallPhotoHydrationOutcome.NotEligible
            if (local.contactId != contactId || local.rawContactId != rawContactId ||
                local.accountName != accountName || local.accountType != "com.google" ||
                local.matchingContactIds != listOf(contactId) || rawContactId !in local.rawContactIds)
                return@withLock CallPhotoHydrationOutcome.NotEligible
            val auth = authorization.authorize(accountName)
            if (auth !is GoogleContactsAuthorizationResult.Granted || auth.accountName != accountName)
                return@withLock CallPhotoHydrationOutcome.ConsentRequired
            val photo = peopleClient.loadPhoto(auth.accessToken, phoneNumber)
            if (photo !is GooglePeoplePhotoResult.Found) return@withLock CallPhotoHydrationOutcome.NotEligible
            val existing = local.highResolutionPhoto ?: local.thumbnail
            if (existing == null || !sameNullablePhoto(existing, photo.bytes))
                return@withLock CallPhotoHydrationOutcome.NotEligible
            val sourceInfo = photo.bytes.toContactPhotoImageInfo() ?: return@withLock CallPhotoHydrationOutcome.NotEligible
            val oldInfo = existing.toContactPhotoImageInfo() ?: return@withLock CallPhotoHydrationOutcome.NotEligible
            if (sourceInfo.width <= oldInfo.width || sourceInfo.height <= oldInfo.height ||
                !isMeaningfulPhotoUpgrade(existing, photo.bytes)) return@withLock CallPhotoHydrationOutcome.AlreadyAvailable
            if (!stillAllowed() || !isFeatureEnabled() || selectedAccountName() != accountName)
                return@withLock CallPhotoHydrationOutcome.Disabled
            when (val result = hydrator.hydrate(GoogleContactPhotoHydrationRequest(
                phoneNumber, accountName, photo.bytes, expectedContactId = contactId,
                expectedRawContactId = rawContactId, approvedReferencePhoto = existing,
                canWrite = { stillAllowed() && isFeatureEnabled() && selectedAccountName() == accountName }))) {
                is GoogleContactPhotoHydrationResult.Hydrated -> CallPhotoHydrationOutcome.Updated
                is GoogleContactPhotoHydrationResult.AlreadyHydrated -> CallPhotoHydrationOutcome.AlreadyAvailable
                else -> CallPhotoHydrationOutcome.Failed("Photo verification failed")
            }
        }
    }

    private suspend fun hydrateLocked(
        identityKey: String,
        phoneNumber: String,
        accountName: String,
    ): CallPhotoHydrationOutcome {
        val now = nowMillis()
        val retryAt = maxOf(
            inMemoryRetryAt[identityKey] ?: 0L,
            cooldownStore.get(COOLDOWN_KEY_PREFIX + identityKey),
        )
        if (retryAt > now) return CallPhotoHydrationOutcome.Cooldown

        val local = runCatching { hydrator.snapshot(phoneNumber, accountName) }.getOrNull()
            ?: return CallPhotoHydrationOutcome.NotEligible
        // A high-resolution photo which predates this feature is accepted immediately. Once the
        // app has recorded a refresh window, expiry means "check Google again", not "postpone
        // forever because a previous large image still exists".
        if (isUsefulHighResolutionPhoto(local) && retryAt == 0L) {
            markRetry(identityKey, now + SUCCESS_REFRESH_MILLIS)
            return CallPhotoHydrationOutcome.AlreadyAvailable
        }

        return when (val auth = authorization.authorize(accountName)) {
            is GoogleContactsAuthorizationResult.Granted -> loadAndHydrate(
                identityKey, phoneNumber, accountName, auth.accessToken, peopleClient, local, now,
            )
            is GoogleContactsAuthorizationResult.ConsentRequired ->
                CallPhotoHydrationOutcome.ConsentRequired
            GoogleContactsAuthorizationResult.Unavailable -> CallPhotoHydrationOutcome.Disabled
            is GoogleContactsAuthorizationResult.Failed ->
                CallPhotoHydrationOutcome.Failed(auth.reason)
        }
    }

    private suspend fun loadAndHydrate(
        identityKey: String,
        phoneNumber: String,
        accountName: String,
        accessToken: String,
        photoSource: GooglePeoplePhotoSource,
        local: GoogleContactPhotoSnapshot,
        now: Long,
    ): CallPhotoHydrationOutcome = when (
                val source = photoSource.loadPhoto(accessToken, phoneNumber)
            ) {
                is GooglePeoplePhotoResult.Found -> hydrateFromSource(
                    identityKey = identityKey,
                    phoneNumber = phoneNumber,
                    accountName = accountName,
                    photo = source.bytes,
                    local = local,
                    now = now,
                )
                GooglePeoplePhotoResult.NotFound -> cooldown(
                    identityKey,
                    now + NOT_FOUND_RETRY_MILLIS,
                    CallPhotoHydrationOutcome.NotEligible,
                )
                GooglePeoplePhotoResult.Ambiguous -> cooldown(
                    identityKey,
                    now + AMBIGUOUS_RETRY_MILLIS,
                    CallPhotoHydrationOutcome.NotEligible,
                )
                GooglePeoplePhotoResult.AuthorizationExpired -> cooldown(
                    identityKey,
                    now + TRANSIENT_RETRY_MILLIS,
                    CallPhotoHydrationOutcome.ConsentRequired,
                )
                is GooglePeoplePhotoResult.Failed -> cooldown(
                    identityKey,
                    now + TRANSIENT_RETRY_MILLIS,
                    CallPhotoHydrationOutcome.Failed(source.reason, source.abortScan),
                )
    }

    private suspend fun hydrateFromSource(
        identityKey: String,
        phoneNumber: String,
        accountName: String,
        photo: ByteArray,
        local: GoogleContactPhotoSnapshot,
        now: Long,
    ): CallPhotoHydrationOutcome {
        val sourceInfo = photo.toContactPhotoImageInfo()
            ?: return CallPhotoHydrationOutcome.Failed("Google returned an invalid image")
        val localThumbnail = local.thumbnailInfo
        val existing = local.highResolutionInfo
        if (existing != null && existing.width.toLong() * existing.height >
            sourceInfo.width.toLong() * sourceInfo.height
        ) return CallPhotoHydrationOutcome.AlreadyAvailable
        val sourceIsUseful = maxOf(sourceInfo.width, sourceInfo.height) >= MIN_USEFUL_PHOTO_EDGE &&
            (localThumbnail == null || sourceInfo.width.toLong() * sourceInfo.height >
                localThumbnail.width.toLong() * localThumbnail.height)
        if (!sourceIsUseful) {
            return cooldown(
                identityKey,
                now + NOT_FOUND_RETRY_MILLIS,
                CallPhotoHydrationOutcome.NotEligible,
            )
        }
        return when (
            val result = hydrator.hydrate(
                GoogleContactPhotoHydrationRequest(phoneNumber, accountName, photo,
                    expectedContactId = local.contactId, expectedRawContactId = local.rawContactId),
            )
        ) {
            is GoogleContactPhotoHydrationResult.Hydrated -> cooldown(
                identityKey,
                now + SUCCESS_REFRESH_MILLIS,
                CallPhotoHydrationOutcome.Updated,
            )
            is GoogleContactPhotoHydrationResult.AlreadyHydrated -> cooldown(
                identityKey,
                now + SUCCESS_REFRESH_MILLIS,
                CallPhotoHydrationOutcome.AlreadyAvailable,
            )
            is GoogleContactPhotoHydrationResult.PermissionMissing ->
                CallPhotoHydrationOutcome.Failed("Contacts permission is missing")
            is GoogleContactPhotoHydrationResult.Rejected -> cooldown(
                identityKey,
                now + AMBIGUOUS_RETRY_MILLIS,
                CallPhotoHydrationOutcome.Failed(result.reason.name),
            )
            is GoogleContactPhotoHydrationResult.Failed -> cooldown(
                identityKey,
                now + TRANSIENT_RETRY_MILLIS,
                CallPhotoHydrationOutcome.Failed(result.message),
            )
        }
    }

    private fun isUsefulHighResolutionPhoto(snapshot: GoogleContactPhotoSnapshot): Boolean {
        val high = snapshot.highResolutionInfo ?: return false
        val thumbnail = snapshot.thumbnailInfo
        val largerThanThumbnail = thumbnail == null ||
            high.width.toLong() * high.height > thumbnail.width.toLong() * thumbnail.height
        return snapshot.photoFileId != null && largerThanThumbnail &&
            maxOf(high.width, high.height) >= MIN_USEFUL_PHOTO_EDGE
    }

    private fun <T : CallPhotoHydrationOutcome> cooldown(
        identityKey: String,
        retryAt: Long,
        outcome: T,
    ): T {
        markRetry(identityKey, retryAt)
        return outcome
    }

    private fun markRetry(identityKey: String, retryAt: Long) {
        inMemoryRetryAt[identityKey] = retryAt
        cooldownStore.put(COOLDOWN_KEY_PREFIX + identityKey, retryAt)
    }

    private fun cooldownIdentity(accountName: String, phoneNumber: String): String {
        val normalized = phoneNumber.filter(Char::isDigit)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$accountName|$normalized".encodeToByteArray())
        return digest.take(12).joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal class PhotoHydrationCooldownStore(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("call_photo_hydration", Context.MODE_PRIVATE)

    fun get(key: String): Long = preferences.getLong(key, 0L)

    fun put(key: String, value: Long) {
        preferences.edit { putLong(key, value) }
    }

    fun clear() {
        preferences.edit { clear() }
    }
}
