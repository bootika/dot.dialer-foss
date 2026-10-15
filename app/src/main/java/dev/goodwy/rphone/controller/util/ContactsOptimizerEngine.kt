package dev.goodwy.rphone.controller.util

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** All automatic sources use the same evaluator and verified provider writer. Caller owns the mutex. */
internal class ContactsOptimizerEngine(
    private val context: Context,
    private val profiles: ContactOptimizationProfiles,
    private val contacts: WhatsAppAvatarContactStore,
    private val reviews: WhatsAppAvatarReviewStore,
    private val allowed: (String, String) -> Boolean,
    private val googleSource: suspend (String) -> GooglePeoplePhotoSource?,
    private val gravatar: ContactAvatarSource,
    private val companies: CompanyLogoSource,
    private val write: suspend (WhatsAppAvatarContactTarget, CapturedWhatsAppAvatar, () -> Boolean, () -> Boolean) -> Boolean,
    private val queue: (WhatsAppAvatarContactTarget, CapturedWhatsAppAvatar) -> Unit,
    private val canNormalize: (WhatsAppAvatarContactTarget) -> Boolean = { it.storedNumber?.trim()?.startsWith("+") == true },
    companyNames: CompanyNameSearch = HttpCompanyNameSearch(context),
) {
    private val logos=CompanyLogoDiscovery(context,contacts,companies,companyNames)
    suspend fun consider(target: WhatsAppAvatarContactTarget, candidates: List<CapturedWhatsAppAvatar>,
        firstObservationIsNew: Boolean = true, fieldsUnchanged: () -> Boolean = { true }): Boolean {
        val account = target.accountName
        val state = ContactsOptimizerState(context, account)
        val policy = profiles.settings(account).effectivePhotoPolicy
        val previous = target.photo?.toContactPhotoImageInfo()
        if (target.photo != null && previous == null) {
            candidates.forEach { queue(target, it) }; return false
        }
        val acquiredAt = maxOf(System.currentTimeMillis(), state.consumed(target.rawContactId) + 1)
        val observed = candidates.filter { allowed(account, it.source) }.mapNotNull { candidate ->
            val info = candidate.bytes.toContactPhotoImageInfo() ?: return@mapNotNull null
            Triple(candidate, info, state.observe(target.rawContactId, candidate.source, candidate.bytes,
                if (!firstObservationIsNew && previous != null && !state.observed(target.rawContactId,candidate.source)) 0 else acquiredAt))
        }
        val hasNonCompany=observed.any { it.first.source!="Company logo" }
        val eligible = observed.filter { (candidate, info, at) ->
            (candidate.source!="Company logo" || (!hasNonCompany && logos.mayUpdate(target,state) &&
                (!candidate.companyDiscovery || profiles.settings(account).businessDiscoveryDisclosed))) &&
            optimizerPhotoChanged(target.photo, candidate.bytes, contacts.maximumDisplayPhotoDimension()) &&
                optimizerAccepts(policy, previous, info, contacts.maximumDisplayPhotoDimension()) &&
                (previous == null || policy == ContactPhotoAutomaticPolicy.Optimal || at > state.consumed(target.rawContactId) ||
                    ((previous.width < 512 || previous.height < 512) && optimizerEffectiveSize(info, contacts.maximumDisplayPhotoDimension()).let { (width, height) ->
                        width >= previous.width && height >= previous.height &&
                            width.toLong() * height > previous.width.toLong() * previous.height }))
        }
        val chosen = if (policy in setOf(ContactPhotoAutomaticPolicy.Latest, ContactPhotoAutomaticPolicy.Newest) && previous != null)
            eligible.maxWithOrNull(compareBy<Triple<CapturedWhatsAppAvatar, ContactPhotoImageInfo, Long>> { it.third }
                .thenBy { it.first.source == state.source(target.rawContactId) }
                .thenBy { minOf(it.second.width, it.second.height) }
                .thenBy { it.second.width.toLong() * it.second.height }
                .thenBy { priority(it.first.source) })
        else eligible.maxWithOrNull(compareBy<Triple<CapturedWhatsAppAvatar, ContactPhotoImageInfo, Long>> {
            minOf(it.second.width, it.second.height)
        }.thenBy { it.second.width.toLong() * it.second.height }.thenBy { it.third }.thenBy { priority(it.first.source) })
        if (chosen != null) {
            val candidate = chosen.first
            fun stillAllowed(): Boolean {
                val latest = profiles.settings(account).effectivePhotoPolicy
                return fieldsUnchanged() && allowed(account, candidate.source) && !state.pinned(target.rawContactId) &&
                    (candidate.source!="Company logo" || (logos.mayUpdate(target,state) &&
                        (!candidate.companyDiscovery || profiles.settings(account).businessDiscoveryDisclosed))) &&
                    contacts.selectedPhotoAllowsAutomaticUpdate(target) &&
                    optimizerAccepts(latest, previous, chosen.second, contacts.maximumDisplayPhotoDimension())
            }
            if (stillAllowed() && write(target, candidate,
                    { profiles.settings(account).effectivePhotoPolicy == ContactPhotoAutomaticPolicy.Latest }, ::stillAllowed)) {
                state.consume(target.rawContactId, observed.maxOfOrNull { it.third } ?: chosen.third, candidate.source)
                val stored=if(candidate.source=="Company logo") contacts.revalidateTarget(target)?.photo
                    ?.takeIf { contactPhotoReadbackMatches(candidate.bytes,it) } else null
                state.rememberLogo(target.rawContactId,stored)
                reviews.forAccount(account).filter { it.rawContactId == target.rawContactId && it.kind == "photo" }
                    .forEach { reviews.remove(it.id, account) }
                return true
            }
        }
        candidates.filter { allowed(account, it.source) && !sameNullablePhoto(target.photo, it.bytes) }
            .forEach { queue(target, it) }
        return false
    }

    suspend fun runPass(account: String, limit: Int = 32): Boolean {
        if (listOf("WhatsApp", "Google", "Gravatar", "Company logo", "Phone number").none { allowed(account, it) }) return false
        if (!ContactOptimizationAccess.contactsGranted(context)) return false
        val state = ContactsOptimizerState(context, account)
        // Read only identities for pagination; do not decode the entire agenda's photos into RAM.
        val identities = contacts.accountIdentities(account).sortedBy { it.first }
        val cursor = state.cursor()
        val ordered = identities.filter { it.first > cursor } + identities.filter { it.first <= cursor }
        var google: GooglePeoplePhotoSource? = null
        var googleRequested = false
        for ((rawId, contactId) in ordered.take(limit)) {
            currentCoroutineContext().ensureActive()
            // Cursor means attempted identity, not a successful write. A slow/broken source
            // must not starve every later contact; interrupted identities return next sweep.
            state.cursor(rawId)
            val target = contacts.chooseContact(contactId, account)?.takeIf { it.rawContactId == rawId }
            if (target == null) { state.cursor(rawId); continue }
            if (allowed(account, "Phone number")) {
                for (number in contacts.exactNumbers(target)) {
                    if (canNormalize(number)) contacts.normalizeMatchedNumber(number) { allowed(account, "Phone number") && canNormalize(number) }
                }
            }
            val fresh = contacts.revalidateTarget(target) ?: continue
            val policy = profiles.settings(account).effectivePhotoPolicy
            val oldInfo = fresh.photo?.toContactPhotoImageInfo()
            if (policy == ContactPhotoAutomaticPolicy.Manual || state.pinned(fresh.rawContactId) ||
                (policy == ContactPhotoAutomaticPolicy.FillMissing && fresh.photo != null) ||
                (policy == ContactPhotoAutomaticPolicy.Optimal && oldInfo != null && oldInfo.width >= 512 && oldInfo.height >= 512)) {
                state.cursor(fresh.rawContactId); continue
            }
            val candidates = mutableListOf<CapturedWhatsAppAvatar>()
            val fields = contacts.automaticFields(fresh)
            fun add(bytes: ByteArray?, source: String) {
                val info = bytes?.toContactPhotoImageInfo() ?: return
                candidates += CapturedWhatsAppAvatar(bytes, info.width, info.height, fresh.matchedNumber, fresh.displayName, source = source)
            }
            val whatsappReviews = reviews.forAccount(account).filter { it.rawContactId == fresh.rawContactId &&
                it.source in setOf("WhatsApp", "WhatsApp Business") &&
                it.matchStrength in setOf(WhatsAppAvatarMatchStrength.ExactPhone, WhatsAppAvatarMatchStrength.UniqueName) &&
                contacts.revalidate(it) != null }
            for (item in whatsappReviews) add(reviews.candidateBytes(item), item.source)
            try {
                if (allowed(account, "Google")) {
                    if (!googleRequested) {
                        googleRequested = true; google = googleSource(account)
                        if (google == null && allowed(account, "Google")) {
                            profiles.update(account) { it.copy(googleAuthorized = false) }
                            profiles.record(account, ContactOptimizationResult(System.currentTimeMillis(), "consent_required"))
                        }
                    }
                    contacts.exactNumbers(fresh).firstOrNull()?.matchedNumber?.let { number ->
                        val result = google?.loadPhoto("", number)
                        if (result is GooglePeoplePhotoResult.Found) add(result.bytes, "Google")
                        if (result == GooglePeoplePhotoResult.AuthorizationExpired) {
                            google = null
                            profiles.update(account) { it.copy(googleAuthorized = false) }
                            profiles.record(account, ContactOptimizationResult(System.currentTimeMillis(), "consent_required"))
                        }
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { sourceFailure(account, "Google", error) }
            currentCoroutineContext().ensureActive()
            try {
                if (allowed(account, "Gravatar")) contacts.emails(fresh).singleOrNull()?.let { email ->
                    if (contacts.uniqueEmail(fresh, email)) add(gravatar.load(email), "Gravatar")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { sourceFailure(account, "Gravatar", error) }
            currentCoroutineContext().ensureActive()
            try {
                if (allowed(account, "Company logo")) {
                    val result=logos.find(fresh,profiles.settings(account).businessDiscoveryDisclosed)
                    for(logo in result.candidates) {
                        val info=logo.bytes.toContactPhotoImageInfo() ?: continue
                        val image=CapturedWhatsAppAvatar(logo.bytes,info.width,info.height,fresh.matchedNumber,fresh.displayName,
                            source="Company logo",companyDomain=logo.domain,companyName=logo.company,companyDiscovery=logo.requiresDiscovery)
                        if(logo.clearMatch) candidates+=image else queue(fresh,image)
                    }
                    if(result.searchUnavailable) profiles.record(account,ContactOptimizationResult(System.currentTimeMillis(),"company_search_unavailable",source="Company logo"))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { sourceFailure(account, "Company logo", error) }
            currentCoroutineContext().ensureActive()
            if (candidates.isNotEmpty()) consider(fresh, candidates, firstObservationIsNew = false) {
                contacts.automaticFields(fresh) == fields && whatsappReviews.all { contacts.revalidate(it) != null }
            }
            state.cursor(fresh.rawContactId)
        }
        ContactOptimizationNotifications.cancelLegacy(context)
        return identities.any { it.first > state.cursor() }
    }
    private fun priority(source: String) = when (source) {
        "WhatsApp", "WhatsApp Business" -> 4
        "Google" -> 3
        "Gravatar" -> 2
        else -> 1
    }
    private fun sourceFailure(account: String, source: String, error: Exception) {
        android.util.Log.w("ContactsOptimizer", source + ": " + error.javaClass.simpleName)
        profiles.record(account, ContactOptimizationResult(System.currentTimeMillis(), "processing_failed", source = source))
    }
}
