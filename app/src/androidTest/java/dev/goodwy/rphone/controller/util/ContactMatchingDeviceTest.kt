package dev.goodwy.rphone.controller.util

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.data.repository.CallerRepositoryImpl
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.db.PrivateContactDao
import dev.goodwy.rphone.modal.repository.ContactsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy

/** Native provider probe: temporary neutral rows only; no existing contact is modified. */
@RunWith(AndroidJUnit4::class)
class ContactMatchingDeviceTest {
    @Test fun verifyRegionalFallbackAndRealProviderAmbiguity() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runMatchingDeviceProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "dot.dialer.test")
        val resolver = context.contentResolver
        val account = requireNotNull(PreferenceManager(context).getString(GoogleContactPhotoPreferences.ACCOUNT, null))
        fun sync(uri: Uri) = uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
        val phone = "+40700000245"
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        resolver.query(lookup, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)!!.use {
            assertEquals("Test number must be unused", 0, it.count)
        }
        val raws = mutableListOf<Long>()
        fun insertFixture(): Long {
            val raw = ContentUris.parseId(requireNotNull(resolver.insert(sync(ContactsContract.RawContacts.CONTENT_URI),
                ContentValues().apply { put(ContactsContract.RawContacts.ACCOUNT_NAME, account)
                    put(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.google")
                    put(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED) })))
            raws += raw
            fun row(mime: String, value: String) = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, raw)
                put(ContactsContract.Data.MIMETYPE, mime); put(ContactsContract.Data.DATA1, value)
            }
            assertEquals(2, resolver.bulkInsert(sync(ContactsContract.Data.CONTENT_URI), arrayOf(
                row(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "Test Alexandru Butica"),
                row(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phone))))
            var id: Long? = null
            repeat(40) {
                if (id == null) {
                    resolver.query(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, raw),
                        arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null)!!.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) id = c.getLong(0).takeIf { it > 0 }
                    }
                    if (id == null) SystemClock.sleep(100)
                }
            }
            return requireNotNull(id)
        }
        // Only the original lookup is empty. The fallback reads the actual Samsung provider.
        val emptyLookup = ContentResolver.wrap(object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String? = null
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
                if (uri.pathSegments.firstOrNull() == "phone_lookup") MatrixCursor(requireNotNull(projection))
                else resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only lookup")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only lookup")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only lookup")
        }.apply { attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "example.test.device-probe" }) })
        val output = File(context.cacheDir, "matching-device-v${dev.goodwy.rphone.BuildConfig.VERSION_CODE}").apply { mkdirs() }
        try {
            val id = insertFixture().toString()
            for (number in listOf("0700000245", phone, "0040700000245")) {
                assertEquals(ContactNumberMatch(id, true), lookupContactNumber(emptyLookup, number) { "RO" })
            }
            assertNull(lookupContactNumber(emptyLookup, "0700000245") { null })
            val dao = Proxy.newProxyInstance(PrivateContactDao::class.java.classLoader,
                arrayOf(PrivateContactDao::class.java)) { _, _, _ -> error("Private contacts are outside this probe") } as PrivateContactDao
            val repository = ContactsRepository(context, dao)
            val before = requireNotNull(repository.getContactById(id))
            val caller = GetCallerNameUseCase(CallerRepositoryImpl(repository))("0700000245", null, "RO")
            assertEquals("Test Alexandru Butica", caller.name)
            assertEquals("0700000245", caller.number)
            assertNull(caller.forLookupRegion("GB"))
            assertEquals(before, repository.getContactById(id))
            assertEquals(listOf(phone), before.phoneNumbers)
            verifyDetailsRefresh(context, id, raws.single(), output)
            val second = insertFixture().toString()
            assertNotEquals(id, second)
            assertNull(lookupContactNumber(emptyLookup, "0700000245") { "RO" })
            val regions = context.getSystemService(TelecomManager::class.java).callCapablePhoneAccounts
                .map { resolveContactLookupRegion(context, it) }
            File(output, "verification.json").writeText(JSONObject()
                .put("provider", "Samsung native ContactsProvider").put("regionalFallback", true)
                .put("forcedEmptyOriginalLookup", true).put("ambiguousIdentityRejected", true)
                .put("storedFieldsAndIncomingNumberPreserved", true).put("callAccountRegions", JSONArray(regions))
                .put("detailsRepeatedProviderRefreshStable", true).put("detailsRealNameUpdateVisible", true)
                .put("carrierCalls", 0).toString())
        } finally {
            for (raw in raws) {
                val uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, raw)
                    .buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
                resolver.query(uri, arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME), null, null, null)!!.use {
                    assertTrue(it.moveToFirst()); assertEquals(account, it.getString(0))
                }
                assertEquals(1, resolver.delete(uri, null, null))
                resolver.query(uri, arrayOf(ContactsContract.RawContacts._ID), null, null, null)!!.use { assertEquals(0, it.count) }
            }
        }
    }

    private fun verifyDetailsRefresh(context: android.content.Context, id: String, raw: Long, output: File) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun visible(text: String): Boolean {
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int): Boolean {
                if(node==null || depth>22) return false
                if(node.text?.toString()==text) return true
                return (0 until node.childCount).any { find(node.getChild(it),depth+1) }
            }
            return find(instrumentation.uiAutomation.rootInActiveWindow,0)
        }
        fun await(text: String) {
            val deadline=SystemClock.uptimeMillis()+15000
            while(!visible(text)) { assertTrue("Contact details did not show the expected fixture",SystemClock.uptimeMillis()<deadline);SystemClock.sleep(100) }
        }
        fun capture(name: String) {
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
                File(output,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
        val intent=android.content.Intent(android.content.Intent.ACTION_VIEW,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id.toLong()),context,dev.goodwy.rphone.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<dev.goodwy.rphone.MainActivity>(intent).use {
            await("Test Alexandru Butica")
            SystemClock.sleep(500)
            capture("details-before.png")
            repeat(100) { sample ->
                if(sample%20==0) context.contentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI,null)
                assertTrue("Contact disappeared during provider refresh at sample $sample",visible("Test Alexandru Butica"))
                SystemClock.sleep(100)
            }
            capture("details-after-refresh.png")
            val data=ContactsContract.Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,"true").build()
            assertEquals(1,context.contentResolver.update(data,ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,"Test Alexandru Butica Actualizat")
            },"${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(raw.toString(),ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)))
            await("Test Alexandru Butica Actualizat")
            capture("details-real-update.png")
        }
    }

    @Test fun inspectRequestedDuplicateWithoutChangingIt() {
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("inspectDuplicateName") ?: run { assumeTrue(false); return }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val result = JSONArray()
        resolver.query(ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_URI),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%$name%"), null)!!.use { contacts ->
            while (contacts.moveToNext()) {
                val id = contacts.getLong(0)
                val sources = JSONArray()
                resolver.query(ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE), "${ContactsContract.RawContacts.CONTACT_ID}=?",
                    arrayOf(id.toString()), null)!!.use { raw -> while (raw.moveToNext()) sources.put(raw.getString(0) ?: "local") }
                val numbers = linkedSetOf<String>()
                resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                    arrayOf(id.toString()), null)!!.use { phones -> while (phones.moveToNext()) {
                        normalizedContactNumber(phones.getString(0), "RO")?.let(numbers::add)
                    } }
                // Only irreversible equality keys leave the device, never names/numbers/account names.
                val keys = numbers.map { ContactOptimizationProfiles.accountKey(it) }
                result.put(JSONObject().put("sources", sources).put("numberKeys", JSONArray(keys))
                    .put("hasPhoto", !contacts.isNull(1)))
            }
        }
        File(context.cacheDir, "duplicate-contact-device.json").writeText(JSONObject()
            .put("matchingAggregatedContacts", result.length()).put("contacts", result).put("contactWrites", 0).toString())
    }
}
