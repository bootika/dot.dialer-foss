package dev.goodwy.rphone.controller.util

import android.app.Notification
import android.content.Context
import android.util.Log
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyResult
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyStage

internal data class PendingContactAvatar(
    val account: String, val packageName: String, val identity: WhatsAppSenderIdentity, val avatar: CapturedWhatsAppAvatar?,
)

internal class WhatsAppAvatarSyncProcessor(
    private val context: Context,
    private val preferences: PreferenceManager,
    private val reviews: WhatsAppAvatarReviewStore,
    private val contacts: WhatsAppAvatarContactStore,
    private val googleHydration: CallPhotoHydrationManager,
    val profiles: ContactOptimizationProfiles = ContactOptimizationProfiles(context),
    private val gravatar: ContactAvatarSource = GravatarPhotoSource(),
    private val companies: CompanyLogoSource = OfficialCompanyLogoSource(),
    private val featureAllowed: () -> Boolean = { true },
    private val notificationAccess: () -> Boolean = { ContactOptimizationAccess.granted(context) },
    private val onUnknownPhoto: () -> Unit = {},
    private val companyNames: CompanyNameSearch = HttpCompanyNameSearch(context),
) {
    private val logos=CompanyLogoDiscovery(context,contacts,companies,companyNames)
    private val unknownReview=ManualUnsavedAvatarReview(context,preferences,profiles,contacts,reviews,onUnknownPhoto)
    private val mutableApplying=MutableStateFlow(false)
    val applying=mutableApplying.asStateFlow()
    private val lock = Mutex()
    private val mutableScanProgress = MutableStateFlow<ContactPhotoScanProgress?>(null)
    val scanProgress = mutableScanProgress.asStateFlow()
    @Volatile private var activeScanJob: Job? = null
    private fun sourceAllowed(account: String, source: String): Boolean {
        if (!featureAllowed() || currentAccount() != account || !ContactOptimizationAccess.contactsGranted(context)) return false
        val settings = profiles.settings(account)
        return when (source) {
            "WhatsApp", "WhatsApp Business" -> settings.enabled && settings.disclosed && notificationAccess() && ContactOptimizationAccess.supported(context)
            "Google" -> profiles.googleAllowed(account) && preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
            "Gravatar" -> profiles.gravatarAllowed(account)
            "Company logo" -> profiles.businessAllowed(account)
            "Phone number" -> settings.normalize
            else -> false
        }
    }
    private fun optimizer() = ContactsOptimizerEngine(context, profiles, contacts, reviews, ::sourceAllowed,
        googleHydration::previewSource, gravatar, companies,
        { target, image, smaller, valid -> applyPhoto(target.accountName, target, image, smaller, valid, hydrate = false) },
        { target, image -> queueReview(target, image, image.source) },
        { target -> target.storedNumber?.trim()?.startsWith("+") == true ||
            preferences.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO, null)?.length == 2 },companyNames)
    suspend fun runAutomaticPass() = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = currentAccount() ?: return@withLock false
            try { reconcileAppliedPhotosLocked(account); optimizer().runPass(account) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { reportFailure(account, "processing_failed", error); throw error }
        }
    }
    /** Read-only provider reconciliation: remove only the exact proposal already represented in storage. */
    suspend fun reconcileAppliedPhotos(): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            val account=currentAccount() ?: return@withLock 0
            try { reconcileAppliedPhotosLocked(account) }
            catch(error:CancellationException) { throw error }
            catch(error:Exception) { reportFailure(account,"processing_failed",error);0 }
        }
    }
    private suspend fun reconcileAppliedPhotosLocked(account:String, rawId:Long?=null):Int {
        if(!featureAllowed() || currentAccount()!=account || !ContactOptimizationAccess.contactsGranted(context)) return 0
        var removed=0
        for(item in reviews.forAccount(account).filter { it.kind=="photo" && (rawId==null || it.rawContactId==rawId) }) {
            currentCoroutineContext().ensureActive()
            if(!sourceAllowed(account,item.source) || applyRejection(item)!=null) continue
            val target=contacts.revalidate(item) ?: continue
            val stored=target.photo ?: continue
            val candidate=reviews.candidateBytes(item) ?: continue
            if(!contactPhotoReadbackMatches(candidate,stored)) continue
            // Account, target and photo can change during decoding. Recheck before removing any evidence.
            val latest=contacts.revalidate(item) ?: continue
            if(currentAccount()!=account || !sourceAllowed(account,item.source) || applyRejection(item)!=null ||
                latest.photo?.contentEquals(stored)!=true) continue
            currentCoroutineContext().ensureActive()
            CallPhotoOrigins(context).rememberApplied(latest.rawContactId,candidate,item.source)
            reviews.remove(item.id,account);removed++
            record(account,"unchanged",target.contactId)
        }
        return removed
    }
    fun manualPhotoPinned(account: String, rawId: Long) = runCatching { ContactsOptimizerState(context, account).pinned(rawId) }
        .getOrElse { profiles.reportStorageFailure(); true }
    fun hasManualPhotos(account: String) = runCatching { ContactsOptimizerState(context, account).pinnedIds().isNotEmpty() }
        .getOrElse { profiles.reportStorageFailure(); true }
    suspend fun manualPhotoTargets(account: String) = withContext(Dispatchers.IO) {
        val pinned = ContactsOptimizerState(context, account).pinnedIds()
        contacts.accountIdentities(account).filter { it.first in pinned }.mapNotNull { (rawId, contactId) ->
            contacts.chooseContact(contactId, account)?.takeIf { it.rawContactId == rawId }
        }
    }
    fun protectPhoto(account: String, rawId: Long) {
        runCatching { ContactsOptimizerState(context, account).pin(rawId, true) }
            .onSuccess { profiles.update(account) { it } }.onFailure { profiles.reportStorageFailure() }
    }
    fun resumeAutomaticPhoto(account: String, rawId: Long) {
        runCatching { ContactsOptimizerState(context, account).pin(rawId, false) }
            .onSuccess { profiles.update(account) { it } }.onFailure { profiles.reportStorageFailure() }
    }
    suspend fun resetResults(account: String): Boolean = withContext(Dispatchers.IO) {
        if (mutableScanProgress.value?.account == account) activeScanJob?.cancelAndJoin()
        lock.withLock {
            if (currentAccount() != account) return@withLock false
            reviews.clear(account)
            profiles.record(account, ContactOptimizationResult())
            if (mutableScanProgress.value?.account == account) mutableScanProgress.value = null
            updateReviewNotification()
            true
        }
    }
    @Volatile private var selectedOverride: Pair<String?, String>? = null
    fun currentAccount(): String? {
        val saved = preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null)
        val pending = selectedOverride ?: return saved
        if (saved == pending.second || saved != pending.first) { selectedOverride = null; return saved }
        return pending.second
    }
    fun selectAccount(account: String) {
        profiles.pruneExpired()
        selectedOverride = preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null) to account
        preferences.setBoolean(GoogleContactPhotoPreferences.ENABLED, profiles.googleAllowed(account))
        preferences.setString(GoogleContactPhotoPreferences.ACCOUNT, account)
        reviews.selectAccount(account)
    }
    fun canApply(item: WhatsAppAvatarReviewItem): Boolean = applyRejection(item) == null
    private fun applyRejection(item: WhatsAppAvatarReviewItem): ContactPhotoApplyResult? {
        if (!featureAllowed()) return ContactPhotoApplyResult.INELIGIBLE
        if (System.currentTimeMillis() - item.createdAtMillis !in 0..WhatsAppAvatarReviewStore.RETENTION_MILLIS)
            return ContactPhotoApplyResult.EXPIRED
        if (currentAccount() != item.accountName) return ContactPhotoApplyResult.ACCOUNT_CHANGED
        if (unknownReview.possible(item)) return null
        if (!ContactOptimizationAccess.contactsGranted(context)) return ContactPhotoApplyResult.PERMISSION_DENIED
        val allowed = when {
            item.kind == "number" -> profiles.settings(item.accountName).normalize
            item.kind != "photo" || item.matchStrength == WhatsAppAvatarMatchStrength.Ambiguous -> false
            item.source == "Gravatar" -> profiles.gravatarAllowed(item.accountName)
            item.source == "Company logo" -> profiles.businessAllowed(item.accountName) && contacts.revalidate(item)?.let {
                logos.reviewStillMatches(it,item,profiles.settings(item.accountName).businessDiscoveryDisclosed) } == true
            item.source == "Google" -> profiles.googleAllowed(item.accountName) &&
                preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
            item.source in setOf("WhatsApp", "WhatsApp Business") -> ContactOptimizationAccess.supported(context) &&
                profiles.settings(item.accountName).let { it.enabled && it.disclosed }
            else -> false
        }
        return if (allowed) null else ContactPhotoApplyResult.SOURCE_DISABLED
    }
    private fun active(account: String, photos: Boolean = false): Boolean {
        val settings = profiles.settings(account)
        return featureAllowed() && currentAccount() == account &&
            if (photos) settings.enabled || settings.gravatar || settings.business ||
                preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
            else settings.enabled || settings.normalize || settings.gravatar || settings.business
    }
    fun capture(packageName: String, notification: Notification, tag: String?): PendingContactAvatar? {
        val account = currentAccount()?.takeIf(String::isNotBlank) ?: return null
        val settings = profiles.settings(account)
        if (!ContactOptimizationAccess.supported(context) || !featureAllowed() || !settings.enabled || !settings.disclosed || !notificationAccess()) return null
        val identity = WhatsAppNotificationAvatarExtractor.identity(packageName, notification, tag) ?: return null
        val avatar = WhatsAppNotificationAvatarExtractor.extract(context, packageName, notification, tag)
        return PendingContactAvatar(account, packageName, identity, avatar)
    }
    suspend fun process(packageName: String, notification: Notification, tag: String?) {
        capture(packageName, notification, tag)?.let { processCaptured(it) }
    }
    suspend fun processCaptured(event: PendingContactAvatar) = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = event.account
            val settings = profiles.settings(account)
            if (!ContactOptimizationAccess.supported(context) || currentAccount() != account || !settings.enabled || !active(account) ||
                !settings.disclosed || !notificationAccess()) return@withLock
            try {
                val identity = event.identity
                val captured = event.avatar
                val packageName = event.packageName
                // An explicit phone never falls back to a name after an unsuccessful/ambiguous lookup.
                val target = if (identity.phoneNumber != null) contacts.resolveByPhone(identity.phoneNumber, account)
                    else if (identity.senderIdentity) identity.displayName?.let { contacts.resolveByUniqueName(it, account) }
                    else null
                if (target == null) {
                    val exact=identity.phoneNumber?.let { normalizedContactNumber(it,contactNumberRegion(context,preferences)) }
                    if (captured != null && exact != null && !contacts.hasAnyNumber(exact)) {
                        if (UnsavedNumberAvatars(context,account).store(exact,captured.bytes,settings.effectivePhotoPolicy) {
                                sourceAllowed(account,captured.source) && !contacts.hasAnyNumber(exact) &&
                                    profiles.settings(account).effectivePhotoPolicy==settings.effectivePhotoPolicy
                            }) { onUnknownPhoto(); record(account,"updated",image=captured); return@withLock }
                    }
                    if (captured != null) queueAmbiguous(account, identity, captured, packageName)
                    record(account, "ambiguous")
                    return@withLock
                }
                if (target.matchStrength == WhatsAppAvatarMatchStrength.ExactPhone && settings.normalize && active(account)) {
                    contacts.normalizeMatchedNumber(target) { sourceAllowed(account, "Phone number") }
                }
                val refreshed = contacts.revalidateTarget(target) ?: return@withLock
                if (captured != null) consider(account, refreshed, captured, packageName)
                else if (profiles.gravatarAllowed(account) && active(account, photos = true)) {
                    val emails = contacts.emails(refreshed)
                    if (emails.size == 1) {
                        val bytes = gravatar.load(emails.single())
                        if (!active(account, photos = true) || !profiles.gravatarAllowed(account)) return@withLock
                        val info = bytes?.toContactPhotoImageInfo()
                        if (info != null) {
                            val candidate = CapturedWhatsAppAvatar(bytes, info.width, info.height,
                                refreshed.matchedNumber, refreshed.displayName, source = gravatar.sourceName)
                            if (contacts.uniqueEmail(refreshed, emails.single())) optimizer().consider(refreshed, listOf(candidate)) {
                                contacts.emails(refreshed) == emails && contacts.uniqueEmail(refreshed, emails.single())
                            }
                            else queueReview(refreshed, candidate, packageName)
                        }
                    }
                } else record(account, "checked", target.contactId)
            } catch (e: CancellationException) { throw e }
            catch (error: SecurityException) { reportFailure(account, "permission_missing", error) }
            catch (error: Exception) { reportFailure(account, "processing_failed", error) }
        }
    }
    /** Read-only acquisition scan. Every mutation is deferred to explicit selected Apply. */
    suspend fun scanAccount(source: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = currentAccount() ?: return@withLock
            fun enabled() = featureAllowed() && currentAccount() == account && when (source) {
                "numbers" -> profiles.settings(account).normalize
                "gravatar" -> profiles.gravatarAllowed(account)
                "business" -> profiles.businessAllowed(account)
                "google" -> profiles.googleAllowed(account) && preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
                else -> false
            }
            if (source in setOf("gravatar", "business") && when (source) {
                "gravatar" -> profiles.settings(account).gravatar && !profiles.gravatarAllowed(account)
                else -> profiles.settings(account).business && !profiles.businessAllowed(account)
            }) { record(account, "source_consent_required"); return@withLock }
            if (!enabled()) return@withLock
            if(source=="business") companyNames.refreshUnavailable(account)
            mutableScanProgress.value = ContactPhotoScanProgress(account, source)
            activeScanJob = currentCoroutineContext().job
            fun phase(value: ContactPhotoScanPhase) {
                mutableScanProgress.value = mutableScanProgress.value?.copy(phase = value)
            }
            try {
                record(account, "scanning")
                val google = if (source == "google") googleHydration.previewSource(account) else null
                if (source == "google" && google == null) {
                    profiles.update(account) { it.copy(googleAuthorized = false) }
                    phase(ContactPhotoScanPhase.Failed); record(account, "consent_required"); return@withLock
                }
                reviews.beginSearch(account,source)
                val targets = contacts.accountTargets(account)
                var stopped = false
                var failed = false
                var companyUnavailable = false
                mutableScanProgress.value = ContactPhotoScanProgress(account, source, ContactPhotoScanPhase.Searching, total = targets.size)
                for ((index, target) in targets.withIndex()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (!enabled()) { stopped = true; break }
                    try {
                    val fresh = contacts.revalidateTarget(target) ?: continue
                    when (source) {
                        "numbers" -> contacts.exactNumbers(fresh).forEach { queueNumber(it) }
                        "business" -> {
                            val fields=contacts.automaticFields(fresh)
                            val result=logos.find(fresh,profiles.settings(account).businessDiscoveryDisclosed)
                            companyUnavailable=companyUnavailable || result.searchUnavailable
                            if (enabled() && fields==contacts.automaticFields(fresh)) for(logo in result.candidates) {
                                val info=logo.bytes.toContactPhotoImageInfo() ?: continue
                                queueReview(fresh,CapturedWhatsAppAvatar(logo.bytes,info.width,info.height,fresh.matchedNumber,fresh.displayName,
                                    source="Company logo",companyDomain=logo.domain,companyName=logo.company,companyDiscovery=logo.requiresDiscovery),"Company logo")
                            }
                        }
                        "gravatar" -> {
                            val emails = contacts.emails(fresh)
                            if (emails.size == 1) {
                                val bytes = gravatar.load(emails.single())
                                if (bytes != null && enabled()) queueSourcePhoto(fresh, bytes, gravatar.sourceName)
                            }
                        }
                        "google" -> {
                            val exact = contacts.exactNumbers(fresh).firstOrNull() ?: continue
                            val result = google?.loadPhoto("", exact.matchedNumber ?: continue)
                                ?: GooglePeoplePhotoResult.AuthorizationExpired
                            if (result is GooglePeoplePhotoResult.Found && enabled()) {
                                val old = fresh.photo?.toContactPhotoImageInfo()
                                val info = result.bytes.toContactPhotoImageInfo() ?: continue
                                if (minOf(info.width, info.height) >= 48 &&
                                    (old == null || isMeaningfulPhotoUpgrade(fresh.photo, result.bytes)))
                                    queueSourcePhoto(exact, result.bytes, "Google")
                            } else if (result == GooglePeoplePhotoResult.AuthorizationExpired) {
                                profiles.update(account) { it.copy(googleAuthorized = false) }
                                record(account, "consent_required"); failed = true; break
                            } else if (result is GooglePeoplePhotoResult.Failed) {
                                record(account, "processing_failed"); failed = true; break
                            }
                        }
                    }
                    } finally {
                        mutableScanProgress.value = mutableScanProgress.value?.copy(checked = index + 1)
                    }
                }
                phase(when { failed -> ContactPhotoScanPhase.Failed; stopped -> ContactPhotoScanPhase.Stopped; else -> ContactPhotoScanPhase.Complete })
                if (!failed) record(account, if (stopped) "interrupted" else if(companyUnavailable) "company_search_unavailable"
                    else if (reviews.forAccount(account).isEmpty()) "unchanged" else "review_required")
            } catch (e: CancellationException) { phase(ContactPhotoScanPhase.Stopped); record(account, "interrupted"); throw e }
            catch (error: SecurityException) { phase(ContactPhotoScanPhase.Failed); reportFailure(account, "permission_missing", error) }
            catch (error: Exception) { phase(ContactPhotoScanPhase.Failed); reportFailure(account, "processing_failed", error) }
            finally {
                if(!reviews.finishBatch(account)) { phase(ContactPhotoScanPhase.Failed);record(account,"processing_failed") }
                activeScanJob = null
            }
        }
    }
    private fun queueSourcePhoto(target: WhatsAppAvatarContactTarget, bytes: ByteArray, source: String,
        label: String = target.displayName) {
        val info = bytes.toContactPhotoImageInfo() ?: return
        if (source != "Google" && (sameNullablePhoto(target.photo, bytes) ||
            target.photo?.let { contactPhotoReadbackMatches(bytes,it) } == true)) return
        queueReview(target, CapturedWhatsAppAvatar(bytes, info.width, info.height, target.matchedNumber,
            label, source = source), source)
    }
    private fun queueNumber(target: WhatsAppAvatarContactTarget) {
        if (!profiles.settings(target.accountName).normalize || currentAccount() != target.accountName) return
        val stored = target.storedNumber ?: return
        val proposed = safeE164Replacement(context, stored, target.matchedNumber ?: return, contactNumberRegion(context, preferences)) ?: return
        if (stored == proposed) return
        val id = UUID.randomUUID().toString()
        reviews.enqueue(WhatsAppAvatarReviewItem(id, target.contactId, target.rawContactId, target.phoneDataId,
            target.displayName, target.matchedNumber, target.accountName,
            WhatsAppAvatarReviewStore.newFileName(id, "candidate"),
            target.photo?.let { WhatsAppAvatarReviewStore.newFileName(id, "previous") }, 0, 0,
            target.photo?.toContactPhotoImageInfo()?.width, target.photo?.toContactPhotoImageInfo()?.height, "local",
            WhatsAppAvatarMatchStrength.ExactPhone, System.currentTimeMillis(), source = "Phone number",
            kind = "number", originalNumber = stored, proposedNumber = proposed), null, target.photo)
    }
    private suspend fun consider(account: String, target: WhatsAppAvatarContactTarget,
        captured: CapturedWhatsAppAvatar, packageName: String) {
        if (!active(account, photos = true)) return
        if (!optimizerPhotoChanged(target.photo, captured.bytes, contacts.maximumDisplayPhotoDimension())) {
            CallPhotoOrigins(context).rememberApplied(target.rawContactId,captured.bytes,captured.source)
            reconcileAppliedPhotosLocked(account,target.rawContactId)
            record(account, "unchanged", target.contactId); return
        }
        if (target.matchStrength in setOf(WhatsAppAvatarMatchStrength.ExactPhone, WhatsAppAvatarMatchStrength.UniqueName) &&
            packageName in WhatsAppNotificationAvatarExtractor.SUPPORTED_PACKAGES) {
            optimizer().consider(target, listOf(captured)) {
                contacts.revalidateTarget(target) != null
            }; return
        }
        queueReview(target, captured, packageName)
    }
    private suspend fun applyPhoto(account: String, target: WhatsAppAvatarContactTarget, candidate: CapturedWhatsAppAvatar,
        allowSmaller: () -> Boolean = { false }, stillAllowed: () -> Boolean = { true }, hydrate: Boolean = true): Boolean {
        if (!active(account, photos = true)) {
            record(account, "contact_changed", target.contactId)
            return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.PREFLIGHT, ContactPhotoApplyResult.INELIGIBLE)
        }
        if (!contacts.writePhoto(target, candidate.bytes, allowSmaller = allowSmaller) {
            stillAllowed() && featureAllowed() && currentAccount() == account && when (candidate.source) {
                "Gravatar" -> profiles.gravatarAllowed(account)
                "Company logo" -> profiles.businessAllowed(account)
                "Google" -> profiles.googleAllowed(account) && preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
                "WhatsApp", "WhatsApp Business" -> profiles.settings(account).let { it.enabled && it.disclosed }
                else -> false
            }
        }) {
            record(account, "contact_changed", target.contactId); return false
        }
        record(account, "updated", target.contactId, candidate)
        // Independent from lazy-call cooldown; user-granted Google consent is still required.
        val number = target.matchedNumber ?: target.storedNumber ?: contacts.exactNumbers(target).firstOrNull()?.matchedNumber
        val hydrated = hydrate && (number ?: "").let {
            runCatching { googleHydration.hydrateForContactUpdate(it, account, target.contactId, target.rawContactId,
                    stillAllowed = { featureAllowed() && currentAccount() == account && profiles.googleAllowed(account) }) }
                .getOrNull() == CallPhotoHydrationOutcome.Updated
        }
        if (!hydrated) CallPhotoOrigins(context).rememberApplied(target.rawContactId,candidate.bytes,candidate.source)
        val latest = contacts.revalidateTarget(target)?.photo?.toContactPhotoImageInfo()
        profiles.record(account, ContactOptimizationResult(System.currentTimeMillis(), "updated", target.contactId,
            candidate.source, candidate.originalWidth, candidate.originalHeight, latest?.width ?: candidate.width,
            latest?.height ?: candidate.height, hydrated))
        return true
    }
    suspend fun reviewCandidates(id: String): List<WhatsAppAvatarContactTarget> = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext emptyList()
        val item = reviews.forAccount(account).firstOrNull { it.id == id } ?: return@withContext emptyList()
        runCatching { contacts.candidates(item.matchedNumber, item.displayName, account) }.getOrDefault(emptyList())
    }
    suspend fun chooseReviewTarget(id: String, contactId: Long): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = currentAccount() ?: return@withLock false
            val item = reviews.forAccount(account).firstOrNull { it.id == id } ?: return@withLock false
            val choices = contacts.candidates(item.matchedNumber, item.displayName, account)
            val target = choices.firstOrNull { it.contactId == contactId }
                ?: contacts.chooseContact(contactId, account) ?: return@withLock false
            val candidate = reviews.candidateBytes(item) ?: return@withLock false
            if (currentAccount() != account) return@withLock false
            // User picked one candidate: photo-only confirmation, no normalization on ambiguous identity.
            queueReview(target.copy(matchStrength = WhatsAppAvatarMatchStrength.UserSelected,
                matchedNumber = null, phoneDataId = null, storedNumber = null),
                CapturedWhatsAppAvatar(candidate, item.candidateWidth, item.candidateHeight, null,
                    target.displayName, item.originalWidth, item.originalHeight, item.source), item.sourcePackage)
            reviews.remove(id, account); true
        }
    }
    private fun reviewExecutor()=ContactReviewExecutor(context,preferences,profiles,contacts,reviews,::currentAccount,
        ::applyRejection,unknownReview,{ account,target,image -> applyPhoto(account,target,image,allowSmaller={true}) },
        ::queueReview,{ account,outcome,id -> record(account,outcome,id) },::updateReviewNotification)
    suspend fun resolveReview(id: String,accept: Boolean,rememberChoice: Boolean,onPhotoChanged: () -> Unit={}): Boolean =
        withContext(Dispatchers.IO) { lock.withLock { reviewExecutor().resolveReview(id,accept,rememberChoice,onPhotoChanged) } }
    suspend fun resolveSelection(ids: List<String>): ContactReviewApplySummary = withContext(Dispatchers.IO) {
        lock.withLock {
            val account=currentAccount() ?: return@withLock ContactReviewApplySummary(0,ids.size,false)
            var failed=0;var changed=false;var attempted=0
            mutableApplying.value=true
            try {
                reviews.beginBatch(account)
                val executor=reviewExecutor()
                for(id in ids) {
                    currentCoroutineContext().ensureActive();attempted++
                    try { if(!executor.resolveReview(id,true,false) { changed=true }) failed++ }
                    catch(error:Exception) { if(error is CancellationException)throw error;failed++;reportFailure(account,"processing_failed",error) }
                }
            } finally { if(!reviews.finishBatch(account))failed++;mutableApplying.value=false }
            ContactReviewApplySummary(attempted,failed,changed)
        }
    }
    private fun queueAmbiguous(account: String, identity: WhatsAppSenderIdentity, captured: CapturedWhatsAppAvatar, packageName: String) {
        queueReview(WhatsAppAvatarContactTarget(0, 0, null, identity.displayName.orEmpty(), null,
            identity.phoneNumber, account, null, WhatsAppAvatarMatchStrength.Ambiguous), captured, packageName)
    }
    private fun queueReview(target: WhatsAppAvatarContactTarget, captured: CapturedWhatsAppAvatar, packageName: String) {
        if (!active(target.accountName, photos = true)) return
        val id = UUID.randomUUID().toString(); val previous = target.photo?.toContactPhotoImageInfo()
        reviews.enqueue(WhatsAppAvatarReviewItem(id, target.contactId, target.rawContactId, target.phoneDataId,
            target.displayName, target.matchedNumber, target.accountName,
            WhatsAppAvatarReviewStore.newFileName(id, "candidate"),
            target.photo?.let { WhatsAppAvatarReviewStore.newFileName(id, "previous") },
            captured.width, captured.height, previous?.width, previous?.height, packageName, target.matchStrength,
            System.currentTimeMillis(), captured.originalWidth, captured.originalHeight, captured.source,
            displayNumber = target.displayNumber,companyDomain=captured.companyDomain,companyName=captured.companyName,
            companyDiscovery=captured.companyDiscovery),
            captured.bytes, target.photo)
        record(target.accountName, "review_required", target.contactId, captured)
        updateReviewNotification()
    }
    fun reportFailure(account: String, category: String, error: Exception) {
        // Fixed category and type only; exception messages can contain contact data or URLs.
        Log.w("ContactPhotos", category + ": " + error.javaClass.simpleName)
        record(account, category)
    }
    private fun record(account: String, outcome: String, contactId: Long? = null, image: CapturedWhatsAppAvatar? = null) {
        runCatching { profiles.record(account, ContactOptimizationResult(System.currentTimeMillis(), outcome, contactId,
            image?.source.orEmpty(), image?.originalWidth ?: 0, image?.originalHeight ?: 0,
            image?.width ?: 0, image?.height ?: 0)) }
    }
    fun updateReviewNotification() {
        val account = currentAccount() ?: return
        ContactOptimizationNotifications.update(context, account, reviews.forAccount(account).count { it.kind == "photo" })
    }
    companion object {
        const val ACTION_REVIEW_WHATSAPP_AVATARS = "dev.goodwy.rphone.action.REVIEW_WHATSAPP_AVATARS"
    }
}
