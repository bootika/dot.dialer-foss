package dev.goodwy.rphone.modal.repository

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.data.Contact
import io.github.bootika.dotdialer.core.cache.InvalidatableCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-local contact snapshot cache in front of Android's comparatively expensive provider.
 *
 * The provider observer invalidates before publishing [changes], so UI collectors can safely
 * refresh without racing an old cached snapshot. App-initiated mutations use the same path,
 * including private-contact changes which do not reach Android's contacts provider.
 */
class CachingContactsRepository(
    context: Context,
    private val delegate: IContactsRepository,
) : IContactsRepository by delegate {
    private val appContext = context.applicationContext
    private val contactCache = InvalidatableCache<ContactQuery, List<Contact>>()
    private val fullContactCache = InvalidatableCache<ContactQuery, List<Contact>>()
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidateAndNotify()
        }
    }
    @Volatile
    private var observerRegistered = false

    init {
        ensureObserverRegistered()
    }

    override suspend fun getContacts(includePrivate: Boolean, includeHidden: Boolean): List<Contact> {
        ensureObserverRegistered()
        return contactCache.getOrLoad(ContactQuery(includePrivate, includeHidden)) {
            delegate.getContacts(includePrivate, includeHidden)
        }
    }

    override suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact? {
        ensureObserverRegistered()
        return delegate.getContactByNumberInRegion(number, regionIso)
    }

    override suspend fun getContactByNumber(number: String): Contact? {
        ensureObserverRegistered()
        return delegate.getContactByNumber(number)
    }

    override suspend fun getContactsFull(includePrivate: Boolean, includeHidden: Boolean): List<Contact> {
        ensureObserverRegistered()
        return fullContactCache.getOrLoad(ContactQuery(includePrivate, includeHidden)) {
            delegate.getContactsFull(includePrivate, includeHidden)
        }
    }

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        delegate.toggleFavorite(contactId, isFavorite)
        invalidateAndNotify()
    }

    override suspend fun saveContact(contact: Contact) {
        delegate.saveContact(contact)
        invalidateAndNotify()
    }

    override suspend fun saveContactAndRead(contact: Contact): Contact =
        delegate.saveContactAndRead(contact).also { invalidateAndNotify() }

    override suspend fun deleteContact(contactId: String) {
        delegate.deleteContact(contactId)
        invalidateAndNotify()
    }

    override suspend fun deleteContacts(contactIds: List<String>) {
        delegate.deleteContacts(contactIds)
        invalidateAndNotify()
    }

    override suspend fun moveContacts(
        contactIds: List<String>,
        accountName: String?,
        accountType: String?,
    ): Map<String, String> = delegate.moveContacts(contactIds, accountName, accountType).also {
        invalidateAndNotify()
    }

    override suspend fun mergeContacts(targetContactId: String, sourceContactIds: List<String>) {
        delegate.mergeContacts(targetContactId, sourceContactIds)
        invalidateAndNotify()
    }

    override suspend fun unmergeAllSources(contactId: String) {
        delegate.unmergeAllSources(contactId)
        invalidateAndNotify()
    }

    override suspend fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        delegate.setCustomRingtone(contactId, ringtoneUri)
        invalidateAndNotify()
    }

    override suspend fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)?) {
        delegate.formatAllPhoneNumbers(onProgress)
        invalidateAndNotify()
    }

    override suspend fun setDefaultPhoneNumber(
        contactId: String,
        phoneNumber: String,
        isPrimary: Boolean,
    ) {
        delegate.setDefaultPhoneNumber(contactId, phoneNumber, isPrimary)
        invalidateAndNotify()
    }

    override suspend fun setContactGroups(contactId: String, selectedGroupIds: Set<Long>) {
        delegate.setContactGroups(contactId, selectedGroupIds)
        invalidateAndNotify()
    }

    override suspend fun createContactGroup(contactId: String, title: String): Long? =
        delegate.createContactGroup(contactId, title).also { invalidateAndNotify() }

    override suspend fun renameContactGroup(groupId: Long, title: String) {
        delegate.renameContactGroup(groupId, title)
        invalidateAndNotify()
    }

    override suspend fun deleteContactGroup(groupId: Long) {
        delegate.deleteContactGroup(groupId)
        invalidateAndNotify()
    }

    override suspend fun updateRawContact(
        rawContactId: String,
        contact: Contact,
    ) {
        delegate.updateRawContact(rawContactId, contact)
        invalidateAndNotify()
    }

    override suspend fun makeContactPrivate(contactId: String): String? {
        val newContactId = delegate.makeContactPrivate(contactId)
        invalidateAndNotify()
        return newContactId
    }

    override suspend fun makeContactPublic(contactId: String): String? {
        val newContactId = delegate.makeContactPublic(contactId)
        invalidateAndNotify()
        return newContactId
    }

    override suspend fun setContactHidden(contactId: String, isHidden: Boolean) {
        delegate.setContactHidden(contactId, isHidden)
        invalidateAndNotify()
    }

    override suspend fun importPrivateContacts(uri: Uri) {
        delegate.importPrivateContacts(uri)
        invalidateAndNotify()
    }

    fun invalidateAndNotify() {
        invalidate()
        _changes.tryEmit(Unit)
    }

    /**
     * Drops provider snapshots without publishing a provider-change event.
     *
     * Manual refresh owns the subsequent reload itself, so emitting [changes] here would schedule
     * a second, redundant provider query through the UI observer.
     */
    fun invalidate() {
        contactCache.invalidate()
        fullContactCache.invalidate()
    }

    /**
     * Contact permission is granted after this singleton can already have been created. Register
     * lazily as well as at construction time, and tolerate a permission-revocation race instead of
     * crashing the whole process during application startup.
     */
    private fun ensureObserverRegistered() {
        if (observerRegistered || ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        synchronized(this) {
            if (observerRegistered) return
            try {
                appContext.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    observer,
                )
                appContext.contentResolver.registerContentObserver(
                    ContactsContract.Data.CONTENT_URI,
                    true,
                    observer,
                )
                observerRegistered = true
            } catch (error: SecurityException) {
                Log.w("CachingContactsRepo", "Permission denied while observing contacts", error)
                try {
                    appContext.contentResolver.unregisterContentObserver(observer)
                } catch (cleanupError: Exception) {
                    Log.w("CachingContactsRepo", "Unable to roll back contact observer registration", cleanupError)
                }
            }
        }
    }

    private data class ContactQuery(
        val includePrivate: Boolean,
        val includeHidden: Boolean,
    )
}
