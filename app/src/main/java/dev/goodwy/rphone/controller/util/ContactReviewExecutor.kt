package dev.goodwy.rphone.controller.util

import android.content.Context
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyResult
import io.github.bootika.dotdialer.core.diagnostics.ContactPhotoApplyStage
import kotlinx.coroutines.CancellationException

internal class ContactReviewExecutor(private val context: Context,private val preferences: PreferenceManager,
    private val profiles: ContactOptimizationProfiles,private val contacts: WhatsAppAvatarContactStore,
    private val reviews: WhatsAppAvatarReviewStore,private val currentAccount: () -> String?,
    private val applyRejection: (WhatsAppAvatarReviewItem) -> ContactPhotoApplyResult?,
    private val unknown: ManualUnsavedAvatarReview,
    private val applyPhoto: suspend (String,WhatsAppAvatarContactTarget,CapturedWhatsAppAvatar) -> Boolean,
    private val queueReview: (WhatsAppAvatarContactTarget,CapturedWhatsAppAvatar,String) -> Unit,
    private val report: (String,String,Long?) -> Unit,private val updateReviewNotification: () -> Unit) {
    private fun record(account: String,outcome: String,id: Long?=null)=report(account,outcome,id)
    suspend fun resolveReview(id: String,accept: Boolean,rememberChoice: Boolean,onPhotoChanged: () -> Unit): Boolean {
            ContactPhotoApplyTrace.record(ContactPhotoApplyStage.REVIEW, ContactPhotoApplyResult.STARTED)
            val account = currentAccount() ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.REVIEW, ContactPhotoApplyResult.NO_ACCOUNT)
            val item = reviews.forAccount(account).firstOrNull { it.id == id }
                ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.REVIEW, ContactPhotoApplyResult.QUEUE_MISSING)
            if (!accept) {
                if (rememberChoice) profiles.update(account) { it.copy(policy = ExistingPhotoPolicy.NeverReplace) }
                reviews.remove(id, account); updateReviewNotification(); return true
            }
            applyRejection(item)?.let { return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.REVIEW, it) }
            if(unknown.possible(item)) return unknown.apply(item)
            val target = try { contacts.revalidate(item) } catch (error: Exception) {
                if (error is CancellationException) throw error
                ContactPhotoApplyTrace.record(ContactPhotoApplyStage.TARGET, when (error) {
                    is SecurityException -> ContactPhotoApplyResult.PERMISSION_DENIED
                    is java.io.IOException -> ContactPhotoApplyResult.IO_FAILED
                    else -> ContactPhotoApplyResult.EXCEPTION
                })
                return false
            } ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.TARGET, ContactPhotoApplyResult.IDENTITY_CHANGED)
            ContactPhotoApplyTrace.record(ContactPhotoApplyStage.TARGET, ContactPhotoApplyResult.SUCCESS)
            if (item.kind == "number") {
                if (!profiles.settings(account).normalize || target.storedNumber != item.originalNumber ||
                    item.proposedNumber != safeE164Replacement(context, target.storedNumber.orEmpty(), item.matchedNumber.orEmpty(),
                        contactNumberRegion(context, preferences)))
                    return false
                if (contacts.normalizeMatchedNumber(target) {
                    applyRejection(item)==null && currentAccount() == account && profiles.settings(account).normalize
                } != item.proposedNumber) return false
                reviews.remove(id, account); record(account, "normalized", target.contactId); return true
            }
            if (item.kind != "photo") return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.SNAPSHOT, ContactPhotoApplyResult.INELIGIBLE)
            val bytes = reviews.candidateBytes(item) ?: return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.SNAPSHOT, ContactPhotoApplyResult.CANDIDATE_INVALID)
            val legacyAlreadyStored = sameNullablePhoto(target.photo, bytes) &&
                (target.photo?.toContactPhotoImageInfo()?.width ?: 0) >= item.candidateWidth &&
                (target.photo?.toContactPhotoImageInfo()?.height ?: 0) >= item.candidateHeight
            val alreadyStored = legacyAlreadyStored || (item.source != "Google" &&
                target.photo?.let { contactPhotoReadbackMatches(bytes,it) } == true)
            if (alreadyStored) {
                ContactsOptimizerState(context, account).pin(target.rawContactId, true)
                reviews.remove(id, account); record(account, "unchanged", target.contactId); updateReviewNotification()
                ContactPhotoApplyTrace.record(ContactPhotoApplyStage.COMPLETE, ContactPhotoApplyResult.SUCCESS)
                return true
            }
            if (!sameNullablePhoto(target.photo, reviews.previousBytes(item))) {
                queueReview(target, CapturedWhatsAppAvatar(bytes, item.candidateWidth, item.candidateHeight,
                    item.matchedNumber, item.displayName, item.originalWidth, item.originalHeight, item.source), item.sourcePackage)
                reviews.remove(id, account); record(account, "contact_changed")
                onPhotoChanged()
                return ContactPhotoApplyTrace.failed(ContactPhotoApplyStage.SNAPSHOT, ContactPhotoApplyResult.PHOTO_CHANGED)
            }
            val candidate = CapturedWhatsAppAvatar(bytes, item.candidateWidth, item.candidateHeight, item.matchedNumber,
                item.displayName, item.originalWidth, item.originalHeight, item.source)
            ContactsOptimizerState(context, account).pin(target.rawContactId, true)
            if (!applyPhoto(account, target, candidate)) return false
            if (rememberChoice) profiles.update(account) { it.copy(policy = ExistingPhotoPolicy.AlwaysReplace) }
            reviews.remove(id, account); updateReviewNotification()
            ContactPhotoApplyTrace.record(ContactPhotoApplyStage.COMPLETE, ContactPhotoApplyResult.SUCCESS)
            return true
    }
}
