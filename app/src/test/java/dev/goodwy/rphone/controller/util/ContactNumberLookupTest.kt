package dev.goodwy.rphone.controller.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import dev.goodwy.rphone.modal.repository.ContactEditPersistenceAuditTest
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import dev.goodwy.rphone.data.repository.CallerRepositoryImpl
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = android.app.Application::class)
class ContactNumberLookupTest {
    private lateinit var provider: LookupProvider
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver
    @Before fun setup() {
        provider = LookupProvider()
        provider.onCreate()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
    }
    @After fun close() { assertEquals(0, provider.writes); provider.audit.close() }

    @Test fun nationalInternationalAndInternationalAccessPrefixMatchWithoutStoredChanges() = runTest {
        provider.rows += "101" to "+40722123456"
        for (number in listOf("0722 123 456", "+40722123456", "0040722123456")) {
            assertEquals(ContactNumberMatch("101", true), lookupContactNumber(resolver, number) { "RO" })
        }
        assertEquals(listOf("101" to "+40722123456"), provider.rows)
        provider.rows.clear(); provider.rows += "101" to "0722123456"
        assertEquals("101", lookupContactNumber(resolver, "+40722123456") { "RO" }?.contactId)
    }
    @Test fun countryPlansKeepAllDigitsAndSignificantItalianZero() = runTest {
        for ((iso, national, international) in listOf(Triple("GB", "02079460018", "+442079460018"),
            Triple("US", "2025550123", "+12025550123"), Triple("IT", "0236618300", "+390236618300"))) {
            provider.rows.clear(); provider.rows += "101" to international
            assertEquals("101", lookupContactNumber(resolver, national) { iso }?.contactId)
        }
        provider.rows.clear(); provider.rows += "101" to "+44722123456"
        assertNull(lookupContactNumber(resolver, "0722123456") { "RO" })
    }
    @Test fun distinctContactsAreAmbiguousButRepeatedRowsOfOneContactAreNot() = runTest {
        provider.rows += listOf("101" to "+40722123456", "101" to "0722123456")
        assertEquals("101", lookupContactNumber(resolver, "0722123456") { "RO" }?.contactId)
        provider.rows += "202" to "+40722123456"
        assertNull(lookupContactNumber(resolver, "0722123456") { "RO" })
    }
    @Test fun successfulOriginalLookupNeverScansOrReadsRegion() = runTest {
        provider.direct += "202"
        provider.rows += "101" to "+40722123456"
        assertEquals(ContactNumberMatch("202", false), lookupContactNumber(resolver, "0722123456") { error("unused") })
        assertEquals(0, provider.scans)
    }
    @Test fun unavailableCountryAndSpecialOrInvalidNumbersNeverScan() = runTest {
        for (iso in listOf(null, "", "ZZ", "UK", "Romania")) {
            assertNull(lookupContactNumber(resolver, "0722123456") { iso })
        }
        for (number in listOf("", "112", "*222#", "+40722123456;12", "+40722123456 ext 12", "++40722123456", "407+22123456")) {
            assertNull(lookupContactNumber(resolver, number) { "RO" })
        }
        assertEquals(0, provider.scans)
    }
    @Test fun providerDenialAndNullLookupDoNotTriggerFallback() = runTest {
        provider.deny = true
        assertNull(lookupContactNumber(resolver, "0722123456") { "RO" })
        provider.deny = false; provider.nullLookup = true
        assertNull(lookupContactNumber(resolver, "0722123456") { "RO" })
        assertEquals(0, provider.scans)
    }
    @Test fun cancelledFallbackCannotPublishAMatch() = runTest {
        provider.rows += "101" to "+40722123456"
        var returned = false
        val job = backgroundScope.launch {
            provider.onScan = { coroutineContext[kotlinx.coroutines.Job]!!.cancel() }
            lookupContactNumber(resolver, "0722123456") { "RO" }
            returned = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }
    @Test fun noPersistentIndexSurvivesChangedProviderRows() = runTest {
        provider.rows += "101" to "+40722123456"
        assertEquals("101", lookupContactNumber(resolver, "0722123456") { "RO" }?.contactId)
        provider.rows.clear()
        assertNull(lookupContactNumber(resolver, "0722123456") { "RO" })
        provider.rows += "202" to "+40722123456"
        assertEquals("202", lookupContactNumber(resolver, "0722123456") { "RO" }?.contactId)
    }
    @Test fun regionNeedsEveryActiveSimToAgree() {
        assertEquals("RO", reliableContactLookupRegion(listOf("ro", "RO")))
        for (countries in listOf(emptyList(), listOf(null), listOf("RO", null), listOf("RO", "GB"), listOf("ZZ"))) {
            assertNull(reliableContactLookupRegion(countries))
        }
    }
    @Test fun callAccountMustMapToASubscriptionAndPermissionRevocationDisablesFallback() {
        val app = RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_PHONE_STATE)
        val telephony = app.getSystemService(android.telephony.TelephonyManager::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(telephony)
        shadow.setSimCountryIso("ro")
        shadow.setNetworkCountryIso("gb")
        val known = android.telecom.PhoneAccountHandle(android.content.ComponentName("example.test", "Phone"), "one")
        val unknown = android.telecom.PhoneAccountHandle(android.content.ComponentName("example.test", "Phone"), "two")
        shadow.setTelephonyManagerForHandle(known, telephony)
        assertEquals("RO", resolveContactLookupRegion(app, known))
        assertNull(resolveContactLookupRegion(app, unknown))
        org.robolectric.Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.READ_PHONE_STATE)
        assertNull(resolveContactLookupRegion(app, known))
    }

    @Test fun actualRepositoryAndUseCasePreserveSavedNumbersAndRealIncomingNumber() = runTest {
        provider.seed("101", "+40722123456")
        val app = RuntimeEnvironment.getApplication()
        val repository = CachingContactsRepository(app, ContactsRepository(app, ContactEditPersistenceAuditTest.MemoryPrivateDao()))
        val stored = requireNotNull(repository.getContactById("101"))
        val matched = requireNotNull(repository.getContactByNumberInRegion("0722123456", "RO"))
        assertEquals(stored, matched)
        assertEquals(listOf("+40722123456"), matched.phoneNumbers)
        val metadata = GetCallerNameUseCase(CallerRepositoryImpl(repository))("0722123456", "Network name", "RO")
        assertTrue(metadata.isLocalContact)
        assertEquals("Test Alexandru Butica", metadata.name)
        assertEquals("0722123456", metadata.number)
        assertEquals(stored, repository.getContactById("101"))
        assertNull(repository.getContactByNumberInRegion("0722123456", null))
    }

    private class LookupProvider : ContentProvider() {
        val audit = ContactEditPersistenceAuditTest.AuditContactsProvider()
        val rows = mutableListOf<Pair<String, String>>()
        val direct = mutableListOf<String>()
        var onScan: (() -> Unit)? = null
        var scans = 0; var writes = 0; var deny = false; var nullLookup = false
        override fun onCreate() = audit.onCreate()
        fun seed(id: String, number: String) {
            audit.raw("11", id)
            audit.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put("raw_contact_id", "11"); put("mimetype", ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put("data1", "Test Alexandru Butica"); put("data2", "Test Alexandru Butica")
            })
            audit.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put("raw_contact_id", "11"); put("mimetype", ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put("data1", number); put("data2", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            })
            rows += id to number
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (deny) throw SecurityException("Access unavailable")
            if (uri.pathSegments.firstOrNull() == "phone_lookup") {
                if (nullLookup) return null
                return MatrixCursor(requireNotNull(projection)).apply { direct.forEach { addRow(arrayOf(it)) } }
            }
            if (uri == ContactsContract.CommonDataKinds.Phone.CONTENT_URI) {
                scans++; onScan?.invoke()
                return MatrixCursor(requireNotNull(projection)).apply { rows.forEach { addRow(arrayOf(it.first, it.second)) } }
            }
            return audit.query(uri, projection, selection, selectionArgs, sortOrder)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? { writes++; error("Lookup must not write") }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int { writes++; error("Lookup must not write") }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { writes++; error("Lookup must not write") }
        override fun getType(uri: Uri): String? = null
    }
}
