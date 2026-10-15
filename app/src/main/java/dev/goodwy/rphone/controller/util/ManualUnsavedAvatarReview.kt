package dev.goodwy.rphone.controller.util

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager

internal class ManualUnsavedAvatarReview(private val context: Context, private val prefs: PreferenceManager,
    private val profiles: ContactOptimizationProfiles,private val contacts: WhatsAppAvatarContactStore,
    private val reviews: WhatsAppAvatarReviewStore,private val changed: () -> Unit) {
    private fun phone(item: WhatsAppAvatarReviewItem)=item.matchedNumber?.let {
        normalizedContactNumber(it,contactNumberRegion(context,prefs)) }
    fun possible(item: WhatsAppAvatarReviewItem): Boolean {
        if(item.kind!="photo" || item.contactId!=0L || item.source !in setOf("WhatsApp","WhatsApp Business")) return false
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return false
        if(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null)!=item.accountName) return false
        val setting=profiles.settings(item.accountName)
        val number=phone(item) ?: return false
        return setting.enabled && setting.disclosed && ContactOptimizationAccess.supported(context) && !contacts.hasAnyNumber(number)
    }
    fun apply(item: WhatsAppAvatarReviewItem): Boolean {
        if(!possible(item)) return false
        val number=phone(item) ?: return false
        val bytes=reviews.candidateBytes(item) ?: return false
        val stored=UnsavedNumberAvatars(context,item.accountName).store(number,bytes,ContactPhotoAutomaticPolicy.Latest,
            manual=true,valid={ possible(item) })
        if(!stored) return false
        changed();reviews.remove(item.id,item.accountName)
        profiles.record(item.accountName,ContactOptimizationResult(System.currentTimeMillis(),"updated",source=item.source))
        return true
    }
}
