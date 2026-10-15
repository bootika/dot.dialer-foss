package dev.goodwy.rphone.modal.repository

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as Kinds
import dev.goodwy.rphone.modal.data.*
import dev.goodwy.rphone.modal.db.PrivateContactDao
import dev.goodwy.rphone.modal.db.PrivateContactEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** Acceptance regressions against the real repository and an isolated transactional provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ContactEditPersistenceAuditTest {
    private lateinit var provider: AuditContactsProvider
    private lateinit var repository: ContactsRepository
    private val dao = MemoryPrivateDao()

    @Before fun setup() {
        provider = AuditContactsProvider()
        provider.onCreate()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        repository = ContactsRepository(RuntimeEnvironment.getApplication(), dao)
        provider.raw("11", "101")
        provider.raw("12", "101") // keep aggregation-repair branch outside field tests
    }

    @After fun close() { provider.close() }

    @Test fun providerWithoutReadOnlyProjectionCanSaveWritableDeviceSource() = runTest {
        repository.updateRawContact("11",populated())
        val before=requireNotNull(repository.getRawContactData("11"))
        provider.rejectReadOnlyProjection=true
        val draft=before.copy(familyName="Butica Edit")
        repository.updateRawContact("11",draft)
        assertEditableEquals(draft,requireNotNull(repository.getRawContactData("11")))
    }
    @Test fun hiddenRowFlagsDoNotAllowAProviderRefusalToPartiallySaveOtherFields() = runTest {
        repository.updateRawContact("11",populated())
        val before=requireNotNull(repository.getRawContactData("11"))
        provider.update(ContactsContract.Data.CONTENT_URI,ContentValues().apply {
            put(ContactsContract.Data.IS_READ_ONLY,1)
        },"raw_contact_id=? AND mimetype=?",arrayOf("11",Kinds.StructuredName.CONTENT_ITEM_TYPE))
        provider.rejectReadOnlyProjection=true
        assertFails { repository.updateRawContact("11",before.copy(givenName="Test Alexandru Butica",isFavorite=!before.isFavorite)) }
        assertEditableEquals(before,requireNotNull(repository.getRawContactData("11")))
        assertEquals(before.isFavorite,requireNotNull(repository.getRawContactData("11")).isFavorite)
    }

    @Test fun `compound first name and literal dash middle name survive a family name edit`() = runTest {
        val original = populated().copy(namePrefix = "", nameSuffix = "", givenName = "Test Alexandru",
            middleName = "-", familyName = "Butica")
        repository.updateRawContact("11", original)
        val before = requireNotNull(repository.getRawContactData("11"))
        val draft = before.copy(familyName = "Butica Actualizat")
        repository.updateRawContact("11", draft)
        assertEditableEquals(draft, requireNotNull(repository.getRawContactData("11")))
        assertEquals("Test Alexandru - Butica Actualizat",
            provider.value(Kinds.StructuredName.CONTENT_ITEM_TYPE, Kinds.StructuredName.DISPLAY_NAME))
    }

    @Test fun `name only edit preserves an unchanged duplicate read only phone row`() = runTest {
        repository.updateRawContact("11", populated())
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "11"); put("mimetype", Kinds.Phone.CONTENT_ITEM_TYPE)
            put(Kinds.Phone.NUMBER, populated().phoneDetails.first().number)
            put(Kinds.Phone.TYPE, Kinds.Phone.TYPE_WORK); put("is_read_only", 1)
        })
        val before = requireNotNull(repository.getRawContactData("11"))
        val rowCount = provider.count(Kinds.Phone.CONTENT_ITEM_TYPE)
        repository.updateRawContact("11", before.copy(givenName = "Test Alexandru Butica"))
        assertEquals("Test Alexandru Butica", requireNotNull(repository.getRawContactData("11")).givenName)
        assertEquals(rowCount, provider.count(Kinds.Phone.CONTENT_ITEM_TYPE))
    }

    @Test fun `changing phones still refuses removal of a protected duplicate before commit`() = runTest {
        repository.updateRawContact("11", populated())
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "11"); put("mimetype", Kinds.Phone.CONTENT_ITEM_TYPE)
            put(Kinds.Phone.NUMBER, populated().phoneDetails.first().number)
            put(Kinds.Phone.TYPE, Kinds.Phone.TYPE_WORK); put("is_read_only", 1)
        })
        val before = requireNotNull(repository.getRawContactData("11"))
        val count = provider.count(Kinds.Phone.CONTENT_ITEM_TYPE)
        assertFails { repository.updateRawContact("11", before.copy(givenName = "Test Alexandru Butica",
            phoneNumbers = listOf("0700000009"), phoneDetails = listOf(ContactPhoneDetail(number = "0700000009")))) }
        assertEquals(before.givenName, requireNotNull(repository.getRawContactData("11")).givenName)
        assertEquals(count, provider.count(Kinds.Phone.CONTENT_ITEM_TYPE))
    }

    @Test fun `round trip still rejects missing exact phone rows and changed labels`() = runTest {
        val expected = Contact(id = "101", phoneDetails = listOf(
            ContactPhoneDetail(number = "0700000001"), ContactPhoneDetail(number = "+40700000001")))
        assertFails { requireContactEditRoundTrip(expected, expected.copy(phoneDetails = expected.phoneDetails.take(1))) }
        assertFails { requireContactEditRoundTrip(expected, expected.copy(phoneDetails = expected.phoneDetails.map { it.copy(label = "Work") })) }
        requireContactEditRoundTrip(expected.copy(phoneDetails = emptyList(), phoneNumbers = listOf("0700000001")),
            Contact(id = "101", phoneDetails = listOf(ContactPhoneDetail(number = "0700000001"))))
    }

    @Test fun `name edit with equivalent national and international phone forms verifies successfully`() = runTest {
        repository.updateRawContact("11", populated().copy(phoneNumbers = listOf("0700000001"),
            phoneDetails = listOf(ContactPhoneDetail(number = "0700000001"))))
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "11"); put("mimetype", Kinds.Phone.CONTENT_ITEM_TYPE)
            put(Kinds.Phone.NUMBER, "+40700000001"); put(Kinds.Phone.TYPE, Kinds.Phone.TYPE_MOBILE)
        })
        val before = requireNotNull(repository.getRawContactData("11"))
        assertEquals(1, before.phoneNumbers.size)
        assertEquals(2, before.phoneDetails.size)
        val editorDraft = before.copy(givenName = "Test Alexandru Butica",
            phoneNumbers = before.phoneDetails.map { it.number })
        repository.updateRawContact("11", editorDraft)
        assertEquals(2, provider.count(Kinds.Phone.CONTENT_ITEM_TYPE))
        assertEditableEquals(before.copy(givenName = "Test Alexandru Butica"),
            requireNotNull(repository.getRawContactData("11")))
    }

    @Test fun `raw save and fresh read retains every nonempty editable data family`() = runTest {
        val draft = populated()
        repository.updateRawContact("11", draft)
        assertEditableEquals(draft, requireNotNull(repository.getRawContactData("11")))
    }

    @Test fun `raw clearing lists organization and notes really removes those fields`() = runTest {
        repository.updateRawContact("11", populated())
        repository.updateRawContact("11", Contact(id = "101", givenName = "Test Alexandru Butica"))
        val read = requireNotNull(repository.getRawContactData("11"))
        assertTrue(read.phoneDetails.isEmpty())
        assertTrue(read.emails.isEmpty())
        assertTrue(read.addresses.isEmpty())
        assertTrue(read.events.isEmpty())
        assertEquals("", read.company)
        assertEquals("", read.jobTitle)
        assertNull(read.notes)
    }

    @Test fun `raw name parts and nickname can be cleared`() = runTest {
        repository.updateRawContact("11", populated())
        repository.updateRawContact("11", Contact(id = "101", givenName = "Test Alexandru Butica"))
        val read = requireNotNull(repository.getRawContactData("11"))
        assertEquals("", read.namePrefix)
        assertEquals("", read.middleName)
        assertEquals("", read.familyName)
        assertEquals("", read.nameSuffix)
        assertEquals("", read.nickname)
    }

    @Test fun `raw reader retains title-only organization rows`() = runTest {
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "11"); put("mimetype", Kinds.Organization.CONTENT_ITEM_TYPE)
            put(Kinds.Organization.TITLE, "Designer")
        })
        assertEquals("Designer", provider.value(Kinds.Organization.CONTENT_ITEM_TYPE, Kinds.Organization.TITLE))
        assertEquals("Designer", requireNotNull(repository.getRawContactData("11")).jobTitle)
    }

    @Test fun `raw reader gets account from the exact raw id`() = runTest {
        provider.raw("21", "201", "person@example.test", "com.google")
        repository.updateRawContact("21", populated().copy(id = "201"))
        val read = requireNotNull(repository.getRawContactData("21"))
        assertEquals("person@example.test", read.accountName)
        assertEquals("com.google", read.accountType)
    }

    @Test fun `list snapshot stays minimal while editor raw read has all details`() = runTest {
        repository.updateRawContact("11", populated())
        val read = repository.getContacts(includePrivate = false, includeHidden = false).first { it.id == "101" }
        assertEquals("", read.nickname)
        assertEquals("", read.company)
        assertEquals("", read.jobTitle)
        assertTrue(read.addresses.isEmpty())
        assertTrue(read.events.isEmpty())
        assertNull(read.notes)
        assertFalse(read.phoneDetails.first().isPrimary)
        assertNull(read.phoneDetails.first().label)
        assertNull(read.emails.first().label)
    }

    @Test fun `provider failure reaches callers and preserves existing data`() = runTest {
        repository.updateRawContact("11", populated())
        provider.failWrites = true
        assertFails { repository.updateRawContact("11", populated().copy(givenName="Elena")) }
        assertFails { repository.saveContact(populated().copy(rawContactIds=listOf("11"))) }
        assertEquals(2, provider.failedBatches)
        assertEditableEquals(populated(), requireNotNull(repository.getRawContactData("11")))
    }

    @Test fun `aggregate edit requires one source and preserves other and unsupported data`() = runTest {
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "12"); put("mimetype", "vnd.android.cursor.item/website")
            put("data1", "https://example.test")
        })
        assertFails { repository.saveContact(populated()) }
        repository.saveContact(populated().copy(rawContactIds=listOf("11")))
        assertEditableEquals(populated(), requireNotNull(repository.getRawContactData("11")))
        assertEquals("", requireNotNull(repository.getRawContactData("12")).givenName)
        assertEquals(1, provider.count("vnd.android.cursor.item/website"))
    }

    @Test fun `read only raw source is never edited`() = runTest {
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id","12"); put("mimetype",Kinds.StructuredName.CONTENT_ITEM_TYPE)
            put("data1","Mara"); put("data2","Mara"); put("is_read_only",1)
        })
        assertFails { repository.updateRawContact("12", populated()) }
        assertEquals("Mara", requireNotNull(repository.getRawContactData("12")).givenName)
    }

    @Test fun `new contact returns generated identity with every field and favorite`() = runTest {
        val draft=populated().copy(id="0",isFavorite=true)
        val saved=repository.saveContactAndRead(draft)
        assertNotEquals("0",saved.id)
        assertEquals(1,saved.rawContactIds.size)
        assertEditableEquals(draft,saved)
        assertTrue(saved.isFavorite)
    }

    @Test fun `retired private creation is rejected without a local insert`() = runTest {
        assertFails { repository.saveContactAndRead(populated().copy(id="0", isPrivate=true)) }
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun `changed editable families survive round trip and unrelated website survives`() = runTest {
        repository.updateRawContact("11", populated())
        provider.insert(ContactsContract.Data.CONTENT_URI,ContentValues().apply {
            put("raw_contact_id","11"); put("mimetype","vnd.android.cursor.item/website"); put("data1","https://example.test")
        })
        val draft=populated().copy(givenName="Elena",nickname="E",company="Atelier",jobTitle="Manager",
            notes="Text nou",phoneNumbers=listOf("07700900125"),
            phoneDetails=listOf(ContactPhoneDetail(Kinds.Phone.TYPE_WORK,null,"07700900125",true)),
            emails=listOf(ContactEmail(Kinds.Email.TYPE_WORK,null,"elena@example.test")),
            addresses=listOf(ContactAddress(Kinds.StructuredPostal.TYPE_WORK,null,"Strada Exemplu 2")),
            events=listOf(ContactEvent(Kinds.Event.TYPE_ANNIVERSARY,null,"--05-06")))
        repository.updateRawContact("11",draft)
        assertEditableEquals(draft,requireNotNull(repository.getRawContactData("11")))
        assertEquals(1,provider.count("vnd.android.cursor.item/website"))
    }

    @Test fun `known row updates preserve unmodelled organization columns`() = runTest {
        repository.updateRawContact("11",populated())
        provider.update(ContactsContract.Data.CONTENT_URI,ContentValues().apply { put("data5","Echipa") },
            "raw_contact_id=? AND mimetype=?",arrayOf("11",Kinds.Organization.CONTENT_ITEM_TYPE))
        repository.updateRawContact("11",populated().copy(company="Atelier"))
        assertEquals("Echipa",provider.value(Kinds.Organization.CONTENT_ITEM_TYPE,"data5"))
        assertEquals("Atelier",provider.value(Kinds.Organization.CONTENT_ITEM_TYPE,"data1"))
    }

    @Test fun `mixed source keeps unchanged read only phone and refuses its removal before writing`() = runTest {
        repository.updateRawContact("11",populated())
        provider.update(ContactsContract.Data.CONTENT_URI,ContentValues().apply {
            put("is_read_only",1); putNull("is_super_primary")
        },"raw_contact_id=? AND mimetype=? AND data1=?",arrayOf("11",Kinds.Phone.CONTENT_ITEM_TYPE,"07700900124"))
        repository.updateRawContact("11",populated().copy(company="Atelier"))
        assertEquals("Atelier",requireNotNull(repository.getRawContactData("11")).company)
        assertFails { repository.updateRawContact("11",populated().copy(company="Altă companie",phoneNumbers=emptyList(),phoneDetails=emptyList())) }
        assertEquals("Atelier",requireNotNull(repository.getRawContactData("11")).company)
        assertEquals(2,provider.count(Kinds.Phone.CONTENT_ITEM_TYPE))
    }

    private suspend fun assertFails(action: suspend () -> Unit) {
        var failure: Exception?=null
        try { action() } catch(error: Exception) { failure=error }
        assertNotNull("Expected a failed save",failure)
    }

    @Test fun `retired private save preserves dormant legacy rows`() = runTest {
        dao.insert(PrivateContactEntity.fromContact(Contact(id="p1", givenName="Mara", isPrivate=true)))
        dao.insert(PrivateContactEntity.fromContact(Contact(id="p2", givenName="Elena", isPrivate=true)))
        assertFails { repository.saveContact(populated().copy(id="p2", isPrivate=true)) }
        assertEquals("Mara", dao.getById(1)?.givenName)
        assertEquals("Elena", dao.getById(2)?.givenName)
    }

    @Test fun `private serialization retains all editable fields and photo metadata`() {
        val draft = populated().copy(id="p7", isPrivate=true, photoUri="content://example.test/photo/7", isFavorite=true)
        val read = PrivateContactEntity.fromContact(draft).toContact()
        assertEditableEquals(draft, read)
        assertEquals(draft.photoUri, read.photoUri)
        assertTrue(read.isFavorite)
    }

    @Test fun `raw edit persists favorite flag`() = runTest {
        repository.updateRawContact("11", populated().copy(isFavorite=true))
        assertTrue(requireNotNull(repository.getRawContactData("11")).isFavorite)
    }

    @Test fun `unreadable photo fails without removing previous photo`() = runTest {
        provider.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
            put("raw_contact_id", "11"); put("mimetype", Kinds.Photo.CONTENT_ITEM_TYPE)
            put("photo_uri", "content://example.test/photo/old"); put("data15", byteArrayOf(1))
        })
        assertFails { repository.updateRawContact("11", populated().copy(photoUri="file:///unavailable-contact-audit/photo-7.jpg")) }
        assertEquals(1, provider.count(Kinds.Photo.CONTENT_ITEM_TYPE))
    }

    private fun populated() = Contact(
        id="101", namePrefix="Dr", givenName="Test Alexandru", middleName="Mihai", familyName="Butica",
        nameSuffix="Jr", nickname="Alex", company="Studio", jobTitle="Designer", notes="Notă de test",
        phoneNumbers=listOf("07700900123", "07700900124"),
        phoneDetails=listOf(ContactPhoneDetail(Kinds.Phone.TYPE_CUSTOM, "Birou", "07700900123", true),
            ContactPhoneDetail(Kinds.Phone.TYPE_HOME, null, "07700900124")),
        emails=listOf(ContactEmail(Kinds.Email.TYPE_CUSTOM, "Personal", "alex@example.test")),
        addresses=listOf(ContactAddress(Kinds.StructuredPostal.TYPE_CUSTOM, "Studio", "Strada Exemplu 1")),
        events=listOf(ContactEvent(Kinds.Event.TYPE_BIRTHDAY, null, "2000-01-02"),
            ContactEvent(Kinds.Event.TYPE_CUSTOM, "Eveniment", "--03-04")),
    )

    private fun assertEditableEquals(expected: Contact, actual: Contact) {
        assertEquals(expected.namePrefix, actual.namePrefix); assertEquals(expected.givenName, actual.givenName)
        assertEquals(expected.middleName, actual.middleName); assertEquals(expected.familyName, actual.familyName)
        assertEquals(expected.nameSuffix, actual.nameSuffix); assertEquals(expected.nickname, actual.nickname)
        assertEquals(expected.company, actual.company); assertEquals(expected.jobTitle, actual.jobTitle)
        assertEquals(expected.phoneNumbers, actual.phoneNumbers); assertEquals(expected.phoneDetails, actual.phoneDetails)
        assertEquals(expected.emails, actual.emails); assertEquals(expected.addresses, actual.addresses)
        assertEquals(expected.events, actual.events); assertEquals(expected.notes, actual.notes)
    }

    class MemoryPrivateDao : PrivateContactDao {
        private val rows = linkedMapOf<Long, PrivateContactEntity>()
        override fun getAll() = rows.values.toList()
        override fun getById(id: Long) = rows[id]
        override fun insert(contact: PrivateContactEntity): Long {
            val id=contact.localId.takeIf { it != 0L } ?: ((rows.keys.maxOrNull() ?: 0L)+1)
            rows[id]=contact.copy(localId=id); return id
        }
        override fun update(contact: PrivateContactEntity) { if(rows.containsKey(contact.localId)) rows[contact.localId]=contact }
        override fun delete(contact: PrivateContactEntity) { rows.remove(contact.localId) }
        override fun deleteById(id: Long) { rows.remove(id) }
        override fun setHidden(id: Long, isHidden: Boolean) { rows[id]?.let { rows[id]=it.copy(isHidden=isHidden) } }
    }

    class AuditContactsProvider : ContentProvider() {
        private lateinit var db: SQLiteDatabase
        var failWrites=false
        var failedBatches=0
        var rejectReadOnlyProjection=false
        override fun onCreate(): Boolean {
            db=SQLiteDatabase.create(null)
            db.execSQL("CREATE TABLE raw_contacts (_id INTEGER PRIMARY KEY, contact_id INTEGER, account_name TEXT, account_type TEXT, aggregation_mode INTEGER, starred INTEGER DEFAULT 0, custom_ringtone TEXT)")
            db.execSQL("CREATE TABLE data (_id INTEGER PRIMARY KEY AUTOINCREMENT, raw_contact_id INTEGER, contact_id INTEGER, mimetype TEXT, ${ (1..15).joinToString { "data$it"+if(it==15) " BLOB" else " TEXT" } }, is_read_only INTEGER DEFAULT 0, is_primary INTEGER DEFAULT 0, is_super_primary INTEGER DEFAULT 0, starred INTEGER DEFAULT 0, photo_uri TEXT, photo_id INTEGER, custom_ringtone TEXT, display_name TEXT)")
            db.execSQL("CREATE TABLE contacts (_id INTEGER PRIMARY KEY, starred INTEGER DEFAULT 0, photo_uri TEXT, photo_thumb_uri TEXT)")
            return true
        }
        fun raw(id: String, contactId: String, accountName: String?=null, accountType: String?=null) {
            db.insertOrThrow("raw_contacts", null, ContentValues().apply { put("_id",id); put("contact_id",contactId); put("account_name",accountName); put("account_type",accountType) })
            db.insertWithOnConflict("contacts", null, ContentValues().apply { put("_id",contactId) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        private fun table(uri: Uri)=uri.pathSegments.first()
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            if(rejectReadOnlyProjection && projection?.any { it==ContactsContract.Data.IS_READ_ONLY }==true)
                throw IllegalArgumentException("Invalid column is_read_only")
            return db.query(table(uri), projection, selection, selectionArgs, null, null, sortOrder)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val copy=ContentValues(requireNotNull(values))
            if(table(uri)=="raw_contacts" && !copy.containsKey("contact_id")) {
                val contactId=db.rawQuery("SELECT COALESCE(MAX(_id),0)+1 FROM contacts",null).use { it.moveToFirst(); it.getLong(0) }
                copy.put("contact_id",contactId)
                db.insertOrThrow("contacts",null,ContentValues().apply { put("_id",contactId); put("starred",copy.getAsInteger("starred") ?: 0) })
            }
            if(table(uri)=="data") {
                val raw=copy.getAsString("raw_contact_id")
                db.rawQuery("SELECT contact_id FROM raw_contacts WHERE _id=?", arrayOf(raw)).use { if(it.moveToFirst()) copy.put("contact_id",it.getLong(0)) }
                copy.put("display_name", copy.getAsString("data1"))
            }
            return ContentUris.withAppendedId(uri, db.insertOrThrow(table(uri),null,copy))
        }
        private fun providerWriteSelection(uri: Uri, selection: String?): String? {
            val base=selection ?: uri.lastPathSegment?.takeIf { it.toLongOrNull()!=null }?.let { "_id=$it" }
            return if(rejectReadOnlyProjection && table(uri)=="data") "(${base ?: "1=1"}) AND is_read_only=0" else base
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?)=
            db.update(table(uri), requireNotNull(values), providerWriteSelection(uri,selection), selectionArgs)
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?)=db.delete(table(uri),providerWriteSelection(uri,selection),selectionArgs)
        override fun getType(uri: Uri): String?=null
        override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
            if(failWrites) { failedBatches++; throw SecurityException("Write unavailable") }
            db.beginTransaction()
            return try { super.applyBatch(operations).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
        }
        fun value(mime: String, column: String): String?=db.query("data",arrayOf(column),"mimetype=?",arrayOf(mime),null,null,null).use { if(it.moveToFirst()) it.getString(0) else null }
        fun count(mime: String)=db.query("data",arrayOf("_id"),"mimetype=?",arrayOf(mime),null,null,null).use { it.count }
        fun close() { db.close() }
    }
}
