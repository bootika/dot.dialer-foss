package dev.goodwy.rphone.recordings

import android.content.ContentUris
import android.content.ContentValues
import android.content.ContextWrapper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.controller.util.HttpCompanyNameSearch
import dev.goodwy.rphone.controller.util.OfficialCompanyLogoSource
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

class RecentProviderDeviceTest {
    @Test fun deletingSingleHistoryRowKeepsSibling() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runOwnedHistoryProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="dot.dialer.test")
        val resolver=context.contentResolver
        val rows=mutableListOf<Long>()
        resolver.query(CallLog.Calls.CONTENT_URI,arrayOf("_id"),
            "${CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME} LIKE ? AND ${CallLog.Calls.DATE}>=? AND ${CallLog.Calls.NUMBER} IN (?,?,?)",
            arrayOf("%CallLabConnectionService%",checkNotNull(InstrumentationRegistry.getArguments().getString("ownedSinceEpochMillis")),"0700000199","0700000102","0700000843"),null)!!.use {
            while(it.moveToNext())rows+=it.getLong(0)
        }
        check(rows.size>=2) {"Need current session owned Call Lab rows; direct Samsung insertion is unsupported"}
        try {
            GlobalContext.get().get<ICallLogRepository>().deleteCallLogsByIds(listOf(rows.first()))
            fun exists(id: Long)=resolver.query(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,id),arrayOf("_id"),null,null,null)!!.use {it.count>0}
            assertFalse(exists(rows.first()));assertTrue(exists(rows.last()))
        }finally {rows.forEach {resolver.delete(CallLog.Calls.CONTENT_URI,"_id=?",arrayOf(it.toString()))}}
        Unit
    }
    @Test fun cleanupExplicitlyOwnedHistoryFixtures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("cleanupOwnedFixtures")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="dot.dialer.test")
        val since=checkNotNull(InstrumentationRegistry.getArguments().getString("ownedSinceEpochMillis"))
        val resolver=context.contentResolver
        val selection="${CallLog.Calls.NUMBER}=? AND ${CallLog.Calls.DATE}>=?"
        val args=arrayOf("0700000842",since)
        fun ids()=resolver.query(CallLog.Calls.CONTENT_URI,arrayOf("_id"),selection,args,null)!!.use {c->
            buildList {while(c.moveToNext())add(c.getLong(0))}
        }
        val owned=ids()
        owned.forEach {resolver.delete(CallLog.Calls.CONTENT_URI,"_id=?",arrayOf(it.toString()))}
        val remaining=ids()
        File(context.cacheDir,"audit-cleanup-result.json").writeText(JSONObject().put("ownedRowsRemoved",owned.size).put("remaining",remaining.size).toString())
        assertTrue(remaining.isEmpty())
    }
    @Test fun deletingOneHistoryRowPreservesOtherRowAndContact() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLocalContactProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="dot.dialer.test")
        val resolver=context.contentResolver
        val raws=ContactsContract.RawContacts.CONTENT_URI
        val rawUri=checkNotNull(resolver.insert(raws,ContentValues().apply {
            putNull(ContactsContract.RawContacts.ACCOUNT_NAME);putNull(ContactsContract.RawContacts.ACCOUNT_TYPE)
            put(ContactsContract.RawContacts.AGGREGATION_MODE,ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
        }))
        val raw=ContentUris.parseId(rawUri)
        val created=mutableListOf<Long>()
        try {
            for((mime,value) in listOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE to "Test Alexandru Butica",
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE to "0700000842")) {
                resolver.insert(ContactsContract.Data.CONTENT_URI,ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID,raw);put(ContactsContract.Data.MIMETYPE,mime);put(ContactsContract.Data.DATA1,value)
                })
            }
            val contact=resolver.query(rawUri,arrayOf(ContactsContract.RawContacts.CONTACT_ID,ContactsContract.RawContacts.ACCOUNT_TYPE),null,null,null)!!.use {
                check(it.moveToFirst());check(it.isNull(1));it.getLong(0)
            }
            for(i in 0..1)created+=ContentUris.parseId(checkNotNull(resolver.insert(CallLog.Calls.CONTENT_URI,ContentValues().apply {
                put(CallLog.Calls.NUMBER,"0700000842");put(CallLog.Calls.DATE,System.currentTimeMillis()+i)
                put(CallLog.Calls.DURATION,5);put(CallLog.Calls.TYPE,CallLog.Calls.OUTGOING_TYPE);put(CallLog.Calls.NEW,0)
            })))
            fun exists(uri: android.net.Uri)=resolver.query(uri,arrayOf("_id"),null,null,null)!!.use {it.count>0}
            GlobalContext.get().get<ICallLogRepository>().deleteCallLogsByIds(listOf(created.first()))
            assertFalse(exists(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,created.first())))
            assertTrue(exists(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,created.last())))
            val contactUri=ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contact)
            assertTrue(exists(contactUri))
            val rawCount=resolver.query(raws,arrayOf("_id"),"contact_id=?",arrayOf(contact.toString()),null)!!.use {it.count}
            assertEquals(1,rawCount)
            GlobalContext.get().get<IContactsRepository>().deleteContact(contact.toString())
            assertFalse(exists(contactUri))
        } finally {
            created.forEach {resolver.delete(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,it),null,null)}
            resolver.delete(rawUri,null,null)
        }
        Unit
    }
    @Test fun actualCompanySearchAndOfficialIconAreOperational() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runCompanyNetworkProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"company-network-fixture").apply {mkdirs()}
        val isolated=object:ContextWrapper(context) {override fun getNoBackupFilesDir()=directory}
        val result=HttpCompanyNameSearch(isolated).search("Vodafone","fixture-account")
        val report=JSONObject().put("unavailable",result.unavailable).put("matches",result.matches.size)
        try {
            assertFalse("Actual company-search endpoint unavailable",result.unavailable)
            assertTrue("Expected at least one real company match",result.matches.isNotEmpty())
            val icon=OfficialCompanyLogoSource().load("https://www.wikipedia.org/")
            report.put("officialIconBytes",icon?.size ?: 0)
            assertNotNull("Actual official icon retrieval",icon)
        } finally {File(context.cacheDir,"company-network-result.json").writeText(report.toString(2))}
        Unit
    }
}
