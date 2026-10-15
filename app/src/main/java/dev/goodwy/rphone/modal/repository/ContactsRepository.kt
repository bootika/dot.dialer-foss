package dev.goodwy.rphone.modal.repository

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactAddress
import dev.goodwy.rphone.modal.data.ContactEmail
import dev.goodwy.rphone.modal.data.ContactEvent
import dev.goodwy.rphone.modal.data.ContactGroupMembership
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import dev.goodwy.rphone.modal.db.PrivateContactDao
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.core.net.toUri
import androidx.core.graphics.scale
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.ContactDumpUtils
import dev.goodwy.rphone.controller.util.areNumbersEqual
import dev.goodwy.rphone.controller.util.deduplicateNumbers
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.private_only
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.distinct
import kotlin.collections.plus
import kotlin.time.Duration.Companion.milliseconds

class ContactsRepository(
    private val context: Context,
    private val privateContactDao: PrivateContactDao
) : IContactsRepository {
    private val contentResolver: ContentResolver = context.contentResolver
    override suspend fun getContacts(includePrivate: Boolean, includeHidden: Boolean): List<Contact> = withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<String, Contact>()
        val systemPhotoUris = contentResolver.getSystemContactPhotoUris()
        val rawContactsMap = mutableMapOf<String, MutableList<String>>()
        val accountMap = mutableMapOf<String, Pair<String?, String?>>()


        // 1. We retrieve RawContacts and immediately build the accountMap (1 pass through the database)
        val rawProjection = arrayOf(
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                rawProjection,
                null, null, null
            )?.use { cursor ->
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val rawIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                val accNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(contactIdIdx)
                    val rawId = cursor.getString(rawIdIdx)
                    if (contactId != null && rawId != null) {
                        rawContactsMap.getOrPut(contactId) { mutableListOf() }.add(rawId)
                        if (accountMap[contactId] == null) {
                            val name = if (accNameIdx != -1) cursor.getString(accNameIdx) else null
                            val type = if (accTypeIdx != -1) cursor.getString(accTypeIdx) else null
                            accountMap[contactId] = Pair(name, type)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read the contact index", e)
        }

        // 2. We request Data.CONTENT_URI, but filter only the MIMETYPE values we need
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            CommonDataKinds.Email.CONTENT_ITEM_TYPE
        )

        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.STARRED,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX
        )

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)

                val prefixIdx = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                val givenNameIdx = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                val middleNameIdx = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                val familyNameIdx = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                val suffixIdx = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx)
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val currentName = cursor.getString(nameIdx) ?: ""
                    val currentPhoto = systemPhotoUris[id] ?: cursor.getString(photoIdx)

                    // We take an existing contact from Map or create a default one
                    val current = (contactsMap[id] ?: run {
                        val accInfo = accountMap[id]
                        Contact(
                            id = id,
                            givenName = currentName,
                            photoUri = currentPhoto,
                            isFavorite = isStarred,
                            accountName = accInfo?.first,
                            accountType = accInfo?.second,
                            rawContactIds = rawContactsMap[id] ?: emptyList(),
                            hasMultipleSources = (rawContactsMap[id]?.size ?: 0) > 1
                        )
                    }).withPhotoIfMissing(currentPhoto)

                    if (data1 == null) {
                        contactsMap[id] = current
                        continue
                    }

                    fun getStringSafely(idx: Int): String = if (idx != -1) cursor.getString(idx) ?: "" else ""

                    // 3. Creating an immutable copy
                    val updatedContact = when (mimeType) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            if (current.phoneNumbers.none { areNumbersEqual(it, data1) }) {
                                current.copy(
                                    phoneNumbers = current.phoneNumbers + data1,
                                    phoneDetails = (current.phoneDetails + ContactPhoneDetail(number = data1)).distinctBy { it.number }
                                )
                            } else current
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val email = ContactEmail(value = data1)
                            if (current.emails.none { it == email }) {
                                current.copy(emails = current.emails + email)
                            } else current
                        }
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefix = getStringSafely(prefixIdx)
                            val givenName = getStringSafely(givenNameIdx)
                            val middleName = getStringSafely(middleNameIdx)
                            val familyName = getStringSafely(familyNameIdx)
                            val suffix = getStringSafely(suffixIdx)

                            if (prefix.isNotBlank() || givenName.isNotBlank() || middleName.isNotBlank() || familyName.isNotBlank() || suffix.isNotBlank()) {
                                current.copy(
                                    givenName = givenName,
                                    namePrefix = prefix,
                                    middleName = middleName,
                                    familyName = familyName,
                                    nameSuffix = suffix
                                )
                            } else {
                                current.copy(givenName = currentName)
                            }
                        }
                        else -> current
                    }

                    // 4. Put the updated copy back into the Map
                    contactsMap[id] = updatedContact
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to read contact details", e)
        }

        return@withContext contactsMap.values.toList().sortedBy { it.displayName.lowercase() }
    }

    override suspend fun getContactsFull(includePrivate: Boolean, includeHidden: Boolean): List<Contact> = withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<String, Contact>()
        val systemPhotoUris = contentResolver.getSystemContactPhotoUris()
        val rawContactsMap = mutableMapOf<String, MutableList<String>>()



        // Get all RawContacts for each contact
        val rawProjection = arrayOf(
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                rawProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val rawIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(contactIdIdx)
                    val rawId = cursor.getString(rawIdIdx)

                    if (contactId != null && rawId != null) {
                        rawContactsMap.getOrPut(contactId) { mutableListOf() }.add(rawId)
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read the full contact index", e)
        }

        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE
        )

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx)
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val currentPhoto = systemPhotoUris[id] ?: cursor.getString(photoIdx)

                    val contact = contactsMap.getOrPut(id) {
                        val (accName, accType) = getAccountInfo(id)
                        Contact(
                            id = id,
                            photoUri = currentPhoto,
                            isFavorite = isStarred,
                            accountName = accName,
                            accountType = accType,
                            rawContactIds = rawContactsMap[id] ?: emptyList(),
                            hasMultipleSources = (rawContactsMap[id]?.size ?: 0) > 1
                        )
                    }.withPhotoIfMissing(currentPhoto)
                    contactsMap[id] = contact

                    if (data1 == null) continue

                    when (mimeType) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                            val givenNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                            val middleNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                            val familyNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                            val suffixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                            val prefix = if (prefixIndex >= 0) cursor.getString(prefixIndex) ?: "" else ""
                            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) ?: "" else ""
                            val middleName = if (middleNameIndex >= 0) cursor.getString(middleNameIndex) ?: "" else ""
                            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) ?: "" else ""
                            val suffix = if (suffixIndex >= 0) cursor.getString(suffixIndex) ?: "" else ""

                            contactsMap[id] = contact.copy(
                                namePrefix = prefix,
                                givenName = givenName,
                                middleName = middleName,
                                familyName = familyName,
                                nameSuffix = suffix
                            )
                        }
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            contactsMap[id] = contact.copy(
                                nickname = data1
                            )
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val isPrimary = cursor.getInt(isPrimaryIdx) == 1

                            val phoneDetail = ContactPhoneDetail(
                                number = data1,
                                type = type,
                                label = label,
                                isPrimary = isPrimary
                            )

                            contactsMap[id] = contact.copy(
                                phoneNumbers = deduplicateNumbers(contact.phoneNumbers + data1),
                                phoneDetails = (contact.phoneDetails + phoneDetail).distinctBy { it.number }
                            )
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val email = ContactEmail(type, label, data1)
                            contactsMap[id] = contact.copy(emails = (contact.emails + email).distinct())
                        }
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val address = ContactAddress(type, label, data1)
                            contactsMap[id] = contact.copy(addresses = (contact.addresses + address).distinct())
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            contactsMap[id] = contact.copy(events = (contact.events + event).distinct())
                        }
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            contactsMap[id] = contact.copy(notes = data1)
                        }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val companyIndex = cursor.getColumnIndex(CommonDataKinds.Organization.COMPANY)
                            val titleIndex = cursor.getColumnIndex(CommonDataKinds.Organization.TITLE)

                            val company = if (companyIndex >= 0) cursor.getString(companyIndex) ?: "" else ""
                            val jobTitle = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""

                            contactsMap[id] = contact.copy(
                                company = company,
                                jobTitle = jobTitle
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to read full contact details", e)
        }

        return@withContext contactsMap.values.toList()
            .sortedBy { it.displayName.lowercase() }
    }

    private fun resolveLookupKey(lookupKey: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                Uri.encode(lookupKey)
            )
            var resolved: String? = null
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) resolved = cursor.getString(0)
            }
            resolved
        } catch (error: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to resolve a contact lookup key", error)
            null
        }
    }
    override suspend fun getContactById(contactId: String): Contact? = withContext(Dispatchers.IO) {
        if (contactId.startsWith("p")) return@withContext null

        val resolvedId = if (contactId.toLongOrNull() != null) {
            contactId
        } else {
            resolveLookupKey(contactId) ?: return@withContext null
        }
        val systemPhotoUri = contentResolver.getSystemContactPhotoUri(resolvedId)
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.CUSTOM_RINGTONE,
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE
        )

        var contact: Contact? = null

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(resolvedId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                val ringtoneIdx = cursor.getColumnIndex(ContactsContract.Data.CUSTOM_RINGTONE)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx)
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val ringtone = cursor.getString(ringtoneIdx)
                    val rowPhotoUri = cursor.getString(photoIdx)

                    val currentContact = (contact ?: run {
                        val (accName, accType) = getAccountInfo(resolvedId)
                        Contact(
                            id = id,
                            photoUri = systemPhotoUri ?: rowPhotoUri,
                            isFavorite = isStarred,
                            customRingtone = ringtone,
                            accountName = accName,
                            accountType = accType
                        )
                    }).withPhotoIfMissing(systemPhotoUri ?: rowPhotoUri)

                    if (data1 == null) {
                        contact = currentContact
                        continue
                    }

                    contact = when (mimeType) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                            val givenNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                            val middleNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                            val familyNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                            val suffixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                            val prefix = if (prefixIndex >= 0) cursor.getString(prefixIndex) ?: "" else ""
                            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) ?: "" else ""
                            val middleName = if (middleNameIndex >= 0) cursor.getString(middleNameIndex) ?: "" else ""
                            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) ?: "" else ""
                            val suffix = if (suffixIndex >= 0) cursor.getString(suffixIndex) ?: "" else ""

                            currentContact.copy(
                                namePrefix = prefix,
                                givenName = givenName,
                                middleName = middleName,
                                familyName = familyName,
                                nameSuffix = suffix
                            )
                        }
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(nickname = data1)
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val isPrimary = cursor.getInt(isPrimaryIdx) == 1

                            val phoneDetail = ContactPhoneDetail(
                                number = data1,
                                type = type,
                                label = label,
                                isPrimary = isPrimary
                            )

                            currentContact.copy(
                                phoneNumbers = deduplicateNumbers(currentContact.phoneNumbers + data1),
                                phoneDetails = (currentContact.phoneDetails + phoneDetail).distinctBy { it.number }
                            )
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val email = ContactEmail(type, label, data1)
                            currentContact.copy(emails = (currentContact.emails + email).distinct())
                        }
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val address = ContactAddress(type, label, data1)
                            currentContact.copy(addresses = (currentContact.addresses + address).distinct())
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            currentContact.copy(events = (currentContact.events + event).distinct())
                        }
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(notes = data1)
                        }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val companyIndex = cursor.getColumnIndex(CommonDataKinds.Organization.COMPANY)
                            val titleIndex = cursor.getColumnIndex(CommonDataKinds.Organization.TITLE)

                            val company = if (companyIndex >= 0) cursor.getString(companyIndex) ?: "" else ""
                            val jobTitle = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""

                            currentContact.copy(
                                company = company,
                                jobTitle = jobTitle
                            )
                        }
                        else -> currentContact
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read the requested contact", e)
        }
        return@withContext contact?.withPhotoIfMissing(systemPhotoUri)
    }

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        require(!contactId.startsWith("p")) { "Private contacts are retired" }

        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        contentResolver.update(updateUri, contentValue, null, null)
        Unit
    }

    override suspend fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean) = withContext(Dispatchers.IO) {
        val rawContactId = getRawContactId(contactId) ?: return@withContext

        val ops = ArrayList<ContentProviderOperation>()

        // Reset all phone numbers for this contact
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                )
                .withValue(CommonDataKinds.Phone.IS_PRIMARY, 0)
                .withValue(CommonDataKinds.Phone.IS_SUPER_PRIMARY, 0)
                .build()
        )

        // Set primary for selected number
        if (isPrimary) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.Phone.NUMBER} = ?",
                        arrayOf(rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneNumber)
                    )
                    .withValue(CommonDataKinds.Phone.IS_PRIMARY, 1)
                    .withValue(CommonDataKinds.Phone.IS_SUPER_PRIMARY, 1)
                    .build()
            )
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to set the default phone number", e)
        }
    }

    override suspend fun getContactGroups(contactId: String): List<ContactGroupMembership> = withContext(Dispatchers.IO) {
        if (contactId.startsWith("p")) return@withContext emptyList()

        data class RawSource(val id: Long, val accountName: String?, val accountType: String?)
        val rawSources = mutableListOf<RawSource>()
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                rawSources += RawSource(
                    id = cursor.getLong(idIndex),
                    accountName = cursor.getString(nameIndex),
                    accountType = cursor.getString(typeIndex),
                )
            }
        }
        if (rawSources.isEmpty()) return@withContext emptyList()

        val memberships = mutableSetOf<Pair<Long, Long>>()
        val rawIds = rawSources.map { it.id }
        val rawPlaceholders = rawIds.joinToString(",") { "?" }
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.RAW_CONTACT_ID,
                CommonDataKinds.GroupMembership.GROUP_ROW_ID,
            ),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.RAW_CONTACT_ID} IN ($rawPlaceholders)",
            arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) + rawIds.map(Long::toString),
            null,
        )?.use { cursor ->
            val rawIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID)
            val groupIndex = cursor.getColumnIndexOrThrow(CommonDataKinds.GroupMembership.GROUP_ROW_ID)
            while (cursor.moveToNext()) memberships += cursor.getLong(rawIndex) to cursor.getLong(groupIndex)
        }

        val result = mutableListOf<ContactGroupMembership>()
        contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.ACCOUNT_NAME,
                ContactsContract.Groups.ACCOUNT_TYPE,
                ContactsContract.Groups.DELETED,
                ContactsContract.Groups.AUTO_ADD,
                ContactsContract.Groups.FAVORITES,
            ),
            "${ContactsContract.Groups.DELETED} = 0",
            null,
            "${ContactsContract.Groups.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups.ACCOUNT_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups.ACCOUNT_TYPE)
            val autoAddIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups.AUTO_ADD)
            val favoritesIndex = cursor.getColumnIndexOrThrow(ContactsContract.Groups.FAVORITES)
            while (cursor.moveToNext()) {
                val groupId = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex)?.trim().orEmpty()
                if (title.isEmpty() || cursor.getInt(autoAddIndex) != 0 || cursor.getInt(favoritesIndex) != 0) continue
                val accountName = cursor.getString(nameIndex)
                val accountType = cursor.getString(typeIndex)
                val matchingRawSources = rawSources.filter {
                    it.accountName == accountName && it.accountType == accountType
                }
                val raw = matchingRawSources.firstOrNull { (it.id to groupId) in memberships }
                    ?: matchingRawSources.firstOrNull()
                    ?: continue
                result += ContactGroupMembership(
                    groupId = groupId,
                    rawContactId = raw.id,
                    title = title,
                    accountName = accountName,
                    accountType = accountType,
                    isMember = matchingRawSources.any { (it.id to groupId) in memberships },
                )
            }
        }
        result.distinctBy { it.groupId }
    }

    override suspend fun setContactGroups(contactId: String, selectedGroupIds: Set<Long>) = withContext(Dispatchers.IO) {
        val groups = getContactGroups(contactId)
        if (groups.isEmpty()) return@withContext

        val operations = ArrayList<ContentProviderOperation>()
        groups.forEach { group ->
            val shouldBeMember = group.groupId in selectedGroupIds
            when {
                shouldBeMember && !group.isMember -> operations +=
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, group.rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.GroupMembership.GROUP_ROW_ID, group.groupId)
                        .build()
                !shouldBeMember && group.isMember -> operations +=
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                            arrayOf(
                                contactId,
                                CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                                group.groupId.toString(),
                            ),
                        )
                        .build()
            }
        }
        if (operations.isNotEmpty()) contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
    }

    override suspend fun createContactGroup(contactId: String, title: String): Long? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || contactId.startsWith("p")) return@withContext null

        var rawId: Long? = null
        var accountName: String? = null
        var accountType: String? = null
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId),
            ContactsContract.RawContacts._ID + " ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                rawId = cursor.getLong(0)
                accountName = cursor.getString(1)
                accountType = cursor.getString(2)
            }
        }
        val targetRawId = rawId ?: return@withContext null
        val values = ContentValues().apply {
            put(ContactsContract.Groups.TITLE, cleanTitle)
            put(ContactsContract.Groups.ACCOUNT_NAME, accountName)
            put(ContactsContract.Groups.ACCOUNT_TYPE, accountType)
            put(ContactsContract.Groups.GROUP_VISIBLE, 1)
            put(ContactsContract.Groups.SHOULD_SYNC, 1)
        }
        val uri = contentResolver.insert(ContactsContract.Groups.CONTENT_URI, values)
            ?: return@withContext null
        val groupId = uri.lastPathSegment?.toLongOrNull() ?: return@withContext null
        contentResolver.insert(
            ContactsContract.Data.CONTENT_URI,
            ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, targetRawId)
                put(ContactsContract.Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
            },
        )
        groupId
    }

    override suspend fun renameContactGroup(groupId: Long, title: String) = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@withContext
        contentResolver.update(
            ContactsContract.Groups.CONTENT_URI,
            ContentValues().apply { put(ContactsContract.Groups.TITLE, cleanTitle) },
            "${ContactsContract.Groups._ID} = ?",
            arrayOf(groupId.toString()),
        )
    }

    override suspend fun deleteContactGroup(groupId: Long) = withContext(Dispatchers.IO) {
        contentResolver.delete(
            ContactsContract.Groups.CONTENT_URI,
            "${ContactsContract.Groups._ID} = ?",
            arrayOf(groupId.toString()),
        )
        Unit
    }

    private fun getPhotoBytes(uriString: String): ByteArray? {
        return try {
            val uri = uriString.toUri()
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null

            val maxSize = 720
            val width = bitmap.width
            val height = bitmap.height

            val finalBitmap = if (width > maxSize || height > maxSize) {
                val scale = maxSize.toFloat() / width.coerceAtLeast(height)
                bitmap.scale((width * scale).toInt(), (height * scale).toInt())
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            val bytes = outputStream.toByteArray()

            if (finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
            bitmap.recycle()

            bytes
        } catch (e: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to encode the contact photo", e)
            null
        }
    }

    private fun getRawContactIds(contactId: String): List<String> {
        val ids = mutableListOf<String>()
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                while (cursor.moveToNext()) {
                    ids.add(cursor.getString(idIdx))
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read raw-contact identifiers", e)
        }
        return ids
    }

    private fun getRawContactId(contactId: String): String? {
        return getRawContactIds(contactId).firstOrNull()
    }

    private fun getAccountInfo(contactId: String, raw: Boolean = false): Pair<String?, String?> {
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${if (raw) ContactsContract.RawContacts._ID else ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                    val typeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val type = if (typeIdx != -1) cursor.getString(typeIdx) else null
                    return Pair(name, type)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to resolve the contact account", e)
        }
        return Pair(null, null)
    }

    override suspend fun saveContact(contact: Contact) {
        saveContactAndRead(contact)
    }

    override suspend fun saveContactAndRead(contact: Contact): Contact = withContext(Dispatchers.IO) {
        require(!contact.isPrivate && !contact.id.startsWith("p")) { "Private contacts are retired" }


        if (contact.id.isNotBlank() && contact.id != "0") {
            val sources = getRawContactsForContact(contact.id).filterNot { it.isReadOnly }
            val explicit = contact.rawContactIds.singleOrNull()
            val source = if (explicit != null) sources.firstOrNull { it.rawContactId == explicit }
                else sources.singleOrNull()
            requireNotNull(source) { "Choose one editable contact source" }
            updateRawContact(source.rawContactId, contact)
            return@withContext requireNotNull(getRawContactData(source.rawContactId))
        }

        val ops = ArrayList<ContentProviderOperation>()
        val photoBytes = contact.photoUri?.let { requireNotNull(getPhotoBytes(it)) { "Cannot read contact photo" } }
        run {
            // New Contact
            val rawContactIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.accountName)
                    .withValue(ContactsContract.RawContacts.STARRED, if (contact.isFavorite) 1 else 0)
                    .withValue(ContactsContract.RawContacts.CUSTOM_RINGTONE, contact.customRingtone)
                    .withValue(
                        ContactsContract.RawContacts.AGGREGATION_MODE,
                        ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED
                    )
                    .build()
            )

            // Name with all components
            val nameBuilder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)

            if (contact.namePrefix.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.PREFIX, contact.namePrefix)
            }
            if (contact.givenName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
            }
            if (contact.middleName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
            }
            if (contact.familyName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
            }
            if (contact.nameSuffix.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.SUFFIX, contact.nameSuffix)
            }
            ops.add(nameBuilder.build())

            // Nickname
            if (contact.nickname.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Nickname.NAME, contact.nickname)
                        .withValue(CommonDataKinds.Nickname.TYPE, CommonDataKinds.Nickname.TYPE_DEFAULT)
                        .build()
                )
            }

            // Organization
            if (contact.company.isNotBlank() || contact.jobTitle.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Organization.COMPANY, contact.company)
                        .withValue(CommonDataKinds.Organization.TITLE, contact.jobTitle)
                        .build()
                )
            }

            // Photo
            if (photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }

            // Phone numbers with details
            if (contact.phoneDetails.isNotEmpty()) {
                contact.phoneDetails.forEach { phoneDetail ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(
                                ContactsContract.Data.RAW_CONTACT_ID,
                                rawContactIndex
                            )
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(CommonDataKinds.Phone.NUMBER, phoneDetail.number)
                            .withValue(CommonDataKinds.Phone.TYPE, phoneDetail.type)
                            .withValue(CommonDataKinds.Phone.LABEL, phoneDetail.label)
                            .withValue(
                                CommonDataKinds.Phone.IS_PRIMARY,
                                if (phoneDetail.isPrimary) 1 else 0
                            )
                            .build()
                    )
                }
            } else {
                // Private users don't have phoneDetails
                contact.phoneNumbers.forEach { number ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(CommonDataKinds.Phone.NUMBER, number)
                            .withValue(
                                CommonDataKinds.Phone.TYPE,
                                CommonDataKinds.Phone.TYPE_MOBILE
                            )
                            .build()
                    )
                }
            }

            // Emails
            contact.emails.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Email.ADDRESS, email.value)
                        .withValue(CommonDataKinds.Email.TYPE, email.type)
                        .withValue(CommonDataKinds.Email.LABEL, email.label)
                        .build()
                )
            }

            // Addresses
            contact.addresses.forEach { address ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.formattedAddress)
                        .withValue(CommonDataKinds.StructuredPostal.TYPE, address.type)
                        .withValue(CommonDataKinds.StructuredPostal.LABEL, address.label)
                        .build()
                )
            }

            // Events
            contact.events.forEach { event ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Event.START_DATE, event.date)
                        .withValue(CommonDataKinds.Event.TYPE, event.type)
                        .withValue(CommonDataKinds.Event.LABEL, event.label)
                        .build()
                )
            }

            // Notes
            if (contact.notes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Note.NOTE, contact.notes)
                        .build()
                )
            }
        }
        val result = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawId = requireNotNull(result.firstOrNull()?.uri?.lastPathSegment) { "No saved contact identity" }
        val saved = requireNotNull(getRawContactData(rawId)) { "Saved contact is unavailable" }
        requireContactEditRoundTrip(contact, saved)
        saved
    }

    private suspend fun clearCallBackground(contactId: String) {
        val numbers: List<String> = try {
            getContactById(contactId)?.phoneNumbers ?: emptyList()
        } catch (error: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to resolve numbers while clearing a call background", error)
            emptyList()
        }
        CallBackgroundStore.clearBlocking(context, contactId, numbers)
    }

    private suspend fun deleteContactInternal(contactId: String, clearBackground: Boolean) {
        require(!contactId.startsWith("p")) { "Private contacts are retired" }
        if (clearBackground) clearCallBackground(contactId)


        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
            contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Permission denied while deleting a contact", e)
        } catch (_: IllegalArgumentException) {
            // Invalid URI—ignore
        } catch (e: UnsupportedOperationException) {
            // The contact has already been deleted or is unavailable
            android.util.Log.w("ContactsRepository", "Contact was already unavailable during deletion", e)
        }
    }

    override suspend fun deleteContact(contactId: String) = withContext(Dispatchers.IO) {
        deleteContactInternal(contactId, true)
    }

    override suspend fun deleteContacts(contactIds: List<String>) = withContext(Dispatchers.IO) {
        require(contactIds.none { it.startsWith("p") }) { "Private contacts are retired" }
        val ops = ArrayList<ContentProviderOperation>()
        contactIds.forEach { id ->
            clearCallBackground(id)

            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
            ops.add(ContentProviderOperation.newDelete(uri).build())
        }
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to delete selected contacts", e)
        }
        Unit
    }

    override suspend fun moveContacts(
        contactIds: List<String>, accountName: String?, accountType: String?,
    ): Map<String,String> = withContext(Dispatchers.IO) {
        val mapping = mutableMapOf<String,String>()
        for(id in contactIds.distinct()) {
            val original = requireNotNull(getContactById(id)) { "Contact no longer exists" }
            require(!original.isPrivate) { "Private contacts are retired" }

                val raws = getRawContactsForContact(id)
                require(raws.isNotEmpty() && raws.none { it.isReadOnly }) { "Contact source is unavailable or read-only" }
                val ops = ArrayList<ContentProviderOperation>()
                raws.forEach { raw -> ops += ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(raw.rawContactId))
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME,accountName)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,accountType).build() }
                contentResolver.applyBatch(ContactsContract.AUTHORITY,ops)
                val saved = requireNotNull(getRawContactData(raws.first().rawContactId))
                raws.forEach { raw ->
                    val source = requireNotNull(getRawContactData(raw.rawContactId))
                    check(source.accountName == accountName && source.accountType == accountType) { "Move was not confirmed" }
                }
                mapping[id] = saved.id
                CallBackgroundStore.carryBlocking(context,id,saved.id,original.phoneNumbers)
            
        }
        mapping
    }

    override suspend fun getAvailableAccountsForMoving(): List<Account> = withContext(Dispatchers.IO) {
        val sources = LinkedHashSet<Account>()
        try {
            sources.addAll(AccountManager.get(context).accounts.toList())
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read Android accounts", e)
        }
        try {
            val hasPhoneState =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (hasPhoneState) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val subs = subManager?.activeSubscriptionInfoList
                if (!subs.isNullOrEmpty()) {
                    subs.forEach { sub ->
                        val simName = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
                        sources.add(Account(simName, "SIM_${sub.subscriptionId}"))
                    }
                } else {
                    sources.add(Account("sim_1", "SIM"))
                }
            } else {
                sources.add(Account("sim_1", "SIM"))
            }
        } catch (error: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to enumerate SIM contact accounts", error)
            sources.add(Account("sim_1", "SIM"))
        }

        sources.toList()
    }

    override suspend fun getAvailableAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val sources = LinkedHashSet<Account>()
        try {
            val projection = arrayOf(
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )

            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val accountName = cursor.getString(accountNameIdx)
                    val accountType = cursor.getString(accountTypeIdx)

                    if (accountName != null && accountType != null) {
                        sources.add(Account(accountName, accountType))
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read contact accounts", e)
        }

        try {
            val hasPhoneState =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (hasPhoneState) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val subs = subManager?.activeSubscriptionInfoList
                if (!subs.isNullOrEmpty()) {
                    subs.forEach { sub ->
                        val simName = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
                        sources.add(Account(simName, "SIM_${sub.subscriptionId}"))
                    }
                } else {
                    sources.add(Account("sim_1", "SIM"))
                }
            } else {
                sources.add(Account("sim_1", "SIM"))
            }
        } catch (error: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to enumerate available SIM accounts", error)
            sources.add(Account("sim_1", "SIM"))
        }

        sources.toList()
    }

    override suspend fun getContactByNumber(number: String): Contact? = lookupNumber(number) {
        dev.goodwy.rphone.controller.util.resolveContactLookupRegion(context)
    }

    override suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact? =
        lookupNumber(number) { regionIso }

    private suspend fun lookupNumber(number: String, region: () -> String?): Contact? = withContext(Dispatchers.IO) {
        val match = dev.goodwy.rphone.controller.util.lookupContactNumber(contentResolver, number, region)
            ?: return@withContext null
        val resolvedContactId = match.contactId

        // We retrieve the full contact information, including all fields
        val fullContact = getContactById(resolvedContactId)
        // A fallback enriches identity only: never add an alias even to the returned editable model.
        if (match.usedFallback) return@withContext fullContact

        // Add the number you were looking for if it's not on the list
        fullContact?.let {
            if (it.phoneNumbers.contains(number)) {
                it
            } else {
                it.copy(
                    phoneNumbers = it.phoneNumbers + number,
                    phoneDetails = it.phoneDetails + ContactPhoneDetail(
                        type = CommonDataKinds.Phone.TYPE_MOBILE,
                        label = null,
                        number = number,
                        isPrimary = false
                    )
                )
            }
        }
    }

    override suspend fun findDuplicates(): List<List<Contact>> = withContext(Dispatchers.IO) {
        check(androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            "Contacts permission is unavailable"
        }
        dev.goodwy.rphone.controller.util.duplicatePhoneGroups(
            getContacts(includePrivate = false),
            dev.goodwy.rphone.controller.util.resolveMergeRegion(context),
        )
    }

    override suspend fun setCustomRingtone(contactId: String, ringtoneUri: String?) = withContext(Dispatchers.IO) {
        require(!contactId.startsWith("p")) { "Private contacts are retired" }

        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        contentResolver.update(updateUri, contentValue, null, null)
        Unit
    }

    override suspend fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)?) = withContext(Dispatchers.IO) {
        val allContacts = getContacts(true)
        val ops = ArrayList<ContentProviderOperation>()
        val total = allContacts.size

        allContacts.forEachIndexed { index, contact ->
            onProgress?.invoke(index + 1, total)


                // For regular contacts, we process each RawContact individually
                val rawContactIds = getRawContactIds(contact.id)

                rawContactIds.forEach { rawId ->
                    // Retrieving data for a specific RawContact
                    val rawContact = getRawContactData(rawId)
                    if (rawContact != null) {
                        val formattedPhoneNumbers = rawContact.phoneNumbers.map { it.replace(" ", "") }
                        val formattedPhoneDetails = rawContact.phoneDetails.map {
                            it.copy(number = it.number.replace(" ", ""))
                        }

                        val phoneNumbersChanged = formattedPhoneNumbers != rawContact.phoneNumbers
                        val phoneDetailsChanged = formattedPhoneDetails != rawContact.phoneDetails

                        if (phoneNumbersChanged || phoneDetailsChanged) {
                            // Update the phone numbers in this RawContact
                            // First, delete the old phone numbers
                            ops.add(
                                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                                    .withSelection(
                                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                                                "${ContactsContract.Data.MIMETYPE} = ?",
                                        arrayOf(rawId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                    )
                                    .build()
                            )

                            // Add the formatted phone numbers
                            if (formattedPhoneDetails.isNotEmpty()) {
                                formattedPhoneDetails.forEach { phoneDetail ->
                                    ops.add(
                                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                            .withValue(CommonDataKinds.Phone.NUMBER, phoneDetail.number)
                                            .withValue(CommonDataKinds.Phone.TYPE, phoneDetail.type)
                                            .withValue(CommonDataKinds.Phone.LABEL, phoneDetail.label)
                                            .withValue(CommonDataKinds.Phone.IS_PRIMARY, if (phoneDetail.isPrimary) 1 else 0)
                                            .build()
                                    )
                                }
                            } else if (formattedPhoneNumbers.isNotEmpty()) {
                                formattedPhoneNumbers.forEach { number ->
                                    ops.add(
                                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                            .withValue(CommonDataKinds.Phone.NUMBER, number)
                                            .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                                            .build()
                                    )
                                }
                            }
                        }
                    }
                }
            
        }

        try {
            if (ops.isNotEmpty()) {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to format contact phone numbers", e).let { Unit }
        }
    }

    override suspend fun makeContactPrivate(contactId: String): String? =
        throw UnsupportedOperationException("Private contacts are retired")

    override suspend fun makeContactPublic(contactId: String): String? =
        throw UnsupportedOperationException("Private contacts are retired")

    override suspend fun exportPrivateContacts(uri: Uri): Unit =
        throw UnsupportedOperationException("Private contacts are retired")

    override suspend fun importPrivateContacts(uri: Uri): Unit =
        throw UnsupportedOperationException("Private contacts are retired")

    override suspend fun setContactHidden(contactId: String, isHidden: Boolean): Unit =
        throw UnsupportedOperationException("Private contacts are retired")

    override suspend fun getHiddenNumbers(): List<String> = emptyList()

    override suspend fun isNumberHidden(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        val clean = number.replace(" ", "")
        return@withContext getHiddenNumbers().any { areNumbersEqual(it, clean) }
    }

    // Goodwy
    data class ContactSource(
        val accountName: String?,
        val accountType: String?,
        val isPrimary: Boolean = false,
        val rawContactId: String
    )

    data class RawContactInfo(
        val rawContactId: String,
        val accountName: String?,
        val accountType: String?,
        val contactId: String,
        val isReadOnly: Boolean = false,
    )

    override suspend fun getRawContactsForContact(contactId: String): List<RawContactInfo> = withContext(Dispatchers.IO) {
        val rawContacts = mutableListOf<RawContactInfo>()

        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.CONTACT_ID,
        )
        val readOnlyAccess = contentResolver.getContactReadOnlyAccess(contactId)
        val uploadingTypes = if(readOnlyAccess.rowFlagsAvailable) emptySet() else
            runCatching { ContentResolver.getSyncAdapterTypes().filter {
                it.authority == ContactsContract.AUTHORITY && it.supportsUploading()
            }.map { it.accountType }.toSet() }.getOrDefault(emptySet())

        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                projection,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)

                while (cursor.moveToNext()) {
                    val rawId = cursor.getString(idIdx)
                    val accountName = cursor.getString(accountNameIdx)
                    val accountType = cursor.getString(accountTypeIdx)
                    val contactId = cursor.getString(contactIdIdx)

                    rawContacts.add(
                        RawContactInfo(
                            rawContactId = rawId,
                            accountName = accountName,
                            accountType = accountType,
                            contactId = contactId,
                            isReadOnly = rawId in readOnlyAccess.rawIds || (!readOnlyAccess.rowFlagsAvailable &&
                                !sourceWritableWithoutRowFlags(accountName,accountType,uploadingTypes)),
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read raw contacts", e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("ContactsRepository", "Contacts provider rejected the raw-contact query", e)
        }

        rawContacts
    }

    // We use Android's aggregation mechanism to avoid data loss.
    override suspend fun mergeContacts(targetContactId: String, sourceContactIds: List<String>) = withContext(Dispatchers.IO) {
        val ids = (listOf(targetContactId) + sourceContactIds).distinct()
        require(ids.size > 1 && ids.none { it.startsWith("p") }) { "Choose at least two public contacts" }
        val raws = ids.map { id -> getRawContactIds(id).also { require(it.isNotEmpty()) { "Contact no longer exists" } } }
        val ops = ArrayList<ContentProviderOperation>()
        raws.first().forEach { target -> raws.drop(1).flatten().forEach { source ->
            ops += aggregationOperation(target, source, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
        } }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        requireContactAggregation(contentResolver, raws.flatten(), together = true)
        val mergedId = requireNotNull(contentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?", arrayOf(raws.first().first()), null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }) { "Merged contact identity is unavailable" }
        carryMergedContactPreferences(context, ids, mergedId)
    }

    override suspend fun unmergeAllSources(contactId: String) = withContext(Dispatchers.IO) {
        require(!contactId.startsWith("p")) { "Private contacts have no system sources" }
        val rawIds = getRawContactIds(contactId)
        require(rawIds.size > 1) { "Contact sources have changed" }
        val ops = ArrayList<ContentProviderOperation>()
        rawIds.forEachIndexed { i, first -> rawIds.drop(i+1).forEach { second ->
            ops += aggregationOperation(first, second, ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE)
        } }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        requireContactAggregation(contentResolver, rawIds, together = false)
    }

    override suspend fun getRawContactData(rawContactId: String): Contact? = withContext(Dispatchers.IO) {
        var sourceContact: Contact? = null
        contentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.STARRED,
                ContactsContract.RawContacts.CUSTOM_RINGTONE),
            "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId), null)?.use { cursor ->
            if (cursor.moveToFirst()) sourceContact = Contact(
                id = cursor.getString(0), accountName = cursor.getString(1), accountType = cursor.getString(2),
                isFavorite = cursor.getInt(3) == 1, customRingtone = cursor.getString(4),
                rawContactIds = listOf(rawContactId))
        }
        if (sourceContact == null) return@withContext null
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.CUSTOM_RINGTONE,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE,
            ContactsContract.Data.PHOTO_ID
        )

        var contact: Contact? = sourceContact
        var contactId: String? = null
        var photoUri: String? = null

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(rawContactId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx)
                    if (contactId == null) contactId = id
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx).orEmpty()
                    val isStarred = cursor.getInt(starredIdx) == 1

                    val accountName: String?
                    val accountType: String?
                    if (contact == null) {
                        val (accName, accType) = getAccountInfo(rawContactId, raw = true)
                        accountName = accName
                        accountType = accType
                    } else {
                        accountName = contact.accountName
                        accountType = contact.accountType
                    }

                    if (mimeType == CommonDataKinds.Photo.CONTENT_ITEM_TYPE) {
                        photoUri = cursor.getString(photoIdx)
                        continue
                    }

                    val currentContact = contact ?: Contact(
                        id = id ?: "",
                        photoUri = null,
                        isFavorite = isStarred,
                        accountName = accountName,
                        accountType = accountType,
                        customRingtone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.CUSTOM_RINGTONE)),
                        rawContactIds = listOf(rawContactId)
                    )

                    contact = when (mimeType) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                            val givenNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                            val middleNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                            val familyNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                            val suffixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                            val prefix = if (prefixIndex >= 0) cursor.getString(prefixIndex) ?: "" else ""
                            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) ?: "" else ""
                            val middleName = if (middleNameIndex >= 0) cursor.getString(middleNameIndex) ?: "" else ""
                            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) ?: "" else ""
                            val suffix = if (suffixIndex >= 0) cursor.getString(suffixIndex) ?: "" else ""

                            currentContact.copy(
                                namePrefix = prefix,
                                givenName = givenName,
                                middleName = middleName,
                                familyName = familyName,
                                nameSuffix = suffix
                            )
                        }
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(nickname = data1)
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val isPrimary = cursor.getInt(isPrimaryIdx) == 1

                            val phoneDetail = ContactPhoneDetail(
                                number = data1,
                                type = type,
                                label = label,
                                isPrimary = isPrimary
                            )

                            currentContact.copy(
                                phoneNumbers = deduplicateNumbers(currentContact.phoneNumbers + data1),
                                phoneDetails = (currentContact.phoneDetails + phoneDetail).distinctBy { it.number }
                            )
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val email = ContactEmail(type, label, data1)
                            currentContact.copy(emails = (currentContact.emails + email).distinct())
                        }
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val address = ContactAddress(type, label, data1)
                            currentContact.copy(addresses = (currentContact.addresses + address).distinct())
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            currentContact.copy(events = (currentContact.events + event).distinct())
                        }
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(notes = data1)
                        }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val companyIndex = cursor.getColumnIndex(CommonDataKinds.Organization.COMPANY)
                            val titleIndex = cursor.getColumnIndex(CommonDataKinds.Organization.TITLE)

                            val company = if (companyIndex >= 0) cursor.getString(companyIndex) ?: "" else ""
                            val jobTitle = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""

                            currentContact.copy(
                                company = company,
                                jobTitle = jobTitle
                            )
                        }
                        else -> currentContact
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.w("ContactsRepository", "Unable to read raw-contact data", e)
        }

        return@withContext contact?.copy(photoUri = getPhotoUriForRawContact(rawContactId) ?: photoUri)
    }

    // A method for retrieving a photo associated with a specific RawContact
    private fun getPhotoUriForRawContact(rawContactId: String): String? {
        var photoUri: String? = null

        try {
            val projection = arrayOf(
                ContactsContract.Data.PHOTO_URI,
                ContactsContract.Data._ID
            )

            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                        "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId, CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoUriIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                    photoUri = cursor.getString(photoUriIdx)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to read the raw-contact photo", e)
        }

        return photoUri
    }

    override suspend fun updateRawContact(rawContactId: String, contact: Contact) = withContext(Dispatchers.IO) {
        val before = requireNotNull(getRawContactData(rawContactId)) { "Contact source no longer exists" }
        val source = getRawContactsForContact(before.id).firstOrNull { it.rawContactId == rawContactId }
        require(source != null && !source.isReadOnly && before.id == contact.id) { "Contact source is not editable" }
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId))
            .withExpectedCount(1)
            .withValue(ContactsContract.RawContacts.STARRED, if (contact.isFavorite) 1 else 0).build())
        val changedFields = changedContactEditMimeTypes(before, contact)
        fun sync(mime: String, values: List<Map<String, Any?>>) {
            if (mime in changedFields) ops.syncContactEditRows(contentResolver, rawContactId, mime, values)
        }
        sync(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, listOf(mapOf(
            CommonDataKinds.StructuredName.DISPLAY_NAME to contact.displayName,
            CommonDataKinds.StructuredName.PREFIX to contact.namePrefix,
            CommonDataKinds.StructuredName.GIVEN_NAME to contact.givenName,
            CommonDataKinds.StructuredName.MIDDLE_NAME to contact.middleName,
            CommonDataKinds.StructuredName.FAMILY_NAME to contact.familyName,
            CommonDataKinds.StructuredName.SUFFIX to contact.nameSuffix)))
        sync(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, if (contact.nickname.isBlank()) emptyList() else listOf(
            mapOf(CommonDataKinds.Nickname.NAME to contact.nickname, CommonDataKinds.Nickname.TYPE to CommonDataKinds.Nickname.TYPE_DEFAULT)))
        sync(CommonDataKinds.Organization.CONTENT_ITEM_TYPE, if (contact.company.isBlank() && contact.jobTitle.isBlank()) emptyList()
            else listOf(mapOf(CommonDataKinds.Organization.COMPANY to contact.company, CommonDataKinds.Organization.TITLE to contact.jobTitle)))
        if (contact.photoUri != before.photoUri) {
            val photoBytes = contact.photoUri?.let { requireNotNull(getPhotoBytes(it)) { "Cannot read contact photo" } }
            sync(CommonDataKinds.Photo.CONTENT_ITEM_TYPE, if (photoBytes == null) emptyList()
                else listOf(mapOf(CommonDataKinds.Photo.PHOTO to photoBytes)))
        }
        val phones = contact.phoneDetails.ifEmpty { contact.phoneNumbers.map { ContactPhoneDetail(number = it) } }
        sync(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phones.map { mapOf(
            CommonDataKinds.Phone.NUMBER to it.number, CommonDataKinds.Phone.TYPE to it.type,
            CommonDataKinds.Phone.LABEL to it.label, CommonDataKinds.Phone.IS_PRIMARY to if (it.isPrimary) 1 else 0,
            CommonDataKinds.Phone.IS_SUPER_PRIMARY to if (it.isPrimary) 1 else 0) })
        sync(CommonDataKinds.Email.CONTENT_ITEM_TYPE, contact.emails.map { mapOf(
            CommonDataKinds.Email.ADDRESS to it.value, CommonDataKinds.Email.TYPE to it.type, CommonDataKinds.Email.LABEL to it.label) })
        sync(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, contact.addresses.map { mapOf(
            CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS to it.formattedAddress,
            CommonDataKinds.StructuredPostal.TYPE to it.type, CommonDataKinds.StructuredPostal.LABEL to it.label) })
        sync(CommonDataKinds.Event.CONTENT_ITEM_TYPE, contact.events.map { mapOf(
            CommonDataKinds.Event.START_DATE to it.date, CommonDataKinds.Event.TYPE to it.type, CommonDataKinds.Event.LABEL to it.label) })
        sync(CommonDataKinds.Note.CONTENT_ITEM_TYPE, contact.notes?.let { listOf(mapOf(CommonDataKinds.Note.NOTE to it)) } ?: emptyList())
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val saved = requireNotNull(getRawContactData(rawContactId)) { "Saved contact source is unavailable" }
        requireContactEditRoundTrip(contact, saved)
        Unit
    }

    // A helper method for updating or inserting data
    private fun updateOrInsertData(
        ops: ArrayList<ContentProviderOperation>,
        rawContactId: String,
        mimeType: String,
        values: Map<String, Any>,
        checkNotEmpty: () -> Boolean
    ) {
        if (!checkNotEmpty()) {
            ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId, mimeType)).build())
            return
        }

        val hasData = checkIfDataExists(rawContactId, mimeType)

        if (hasData) {
            // Updating existing data
            val builder = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId, mimeType)
                )

            values.forEach { (key, value) ->
                when (value) {
                    is String -> builder.withValue(key, value)
                    is Int -> builder.withValue(key, value)
                    is ByteArray -> builder.withValue(key, value)
                    else -> builder.withValue(key, value.toString())
                }
            }

            ops.add(builder.build())
        } else {
            // Enter the new data
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)

            values.forEach { (key, value) ->
                when (value) {
                    is String -> builder.withValue(key, value)
                    is Int -> builder.withValue(key, value)
                    is ByteArray -> builder.withValue(key, value)
                    else -> builder.withValue(key, value.toString())
                }
            }

            ops.add(builder.build())
        }
    }

    // Checking Whether Data Exists
    private fun checkIfDataExists(rawContactId: String, mimeType: String): Boolean {
        var exists = false
        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId, mimeType),
                null
            )?.use { cursor ->
                exists = cursor.count > 0
            }
        } catch (e: Exception) {
            android.util.Log.w("ContactsRepository", "Unable to check whether contact data exists", e)
        }
        return exists
    }

    private suspend fun findAndMergeByPhoneNumber(contactId: String) {
        if (contactId.startsWith("p")) return

        val contact = getContactById(contactId) ?: return
        val phoneNumbers = contact.phoneNumbers.toSet()
        if (phoneNumbers.isEmpty()) return

        val allContacts = getContacts(false)
        val matchingContacts = allContacts.filter { other ->
            other.id != contactId &&
                    other.phoneNumbers.any { number -> phoneNumbers.contains(number) }
        }

        if (matchingContacts.isNotEmpty()) {
            val allRawIds = mutableListOf<Long>()
            allRawIds.addAll(getRawContactIds(contactId).map { it.toLong() })

            matchingContacts.forEach { other ->
                allRawIds.addAll(getRawContactIds(other.id).map { it.toLong() })
            }

            if (allRawIds.size > 1) {
                mergeRawContactsDirectly(allRawIds.distinct())
            }
        }
    }

    private fun mergeRawContactsDirectly(rawContactIds: List<Long>) {
        if (rawContactIds.size < 2) return

        val ops = ArrayList<ContentProviderOperation>()
        val sortedIds = rawContactIds.sorted()
        val primaryId = sortedIds.first()

        // First, delete the old rules
        for ((i, element) in sortedIds.withIndex()) {
            for (j in i + 1 until sortedIds.size) {
                val id1 = minOf(element, sortedIds[j])
                val id2 = maxOf(element, sortedIds[j])

                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.AggregationExceptions.RAW_CONTACT_ID1}=? AND " +
                                    "${ContactsContract.AggregationExceptions.RAW_CONTACT_ID2}=?",
                            arrayOf(id1.toString(), id2.toString())
                        )
                        .build()
                )
            }
        }

        // Add KEEP_TOGETHER
        sortedIds.drop(1).forEach { otherId ->
            val id1 = minOf(primaryId, otherId)
            val id2 = maxOf(primaryId, otherId)

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(
                        ContactsContract.AggregationExceptions.TYPE,
                        ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
                    )
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, id1)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, id2)
                    .build()
            )
        }

        try {
            if (ops.isNotEmpty()) {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
        } catch (error: Exception) {
            android.util.Log.e("ContactsRepository", "Unable to restore contact aggregation links", error)
        }
    }

    override suspend fun dumpContact(contactId: String): String = withContext(Dispatchers.IO) {
        ContactDumpUtils.dumpFullContact(contentResolver, contactId)
    }
}
