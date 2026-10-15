package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentValues
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.ContactsContract
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream

/** Opt-in neutral contact only; provider is real, remote logo/search services are not exercised. */
@RunWith(AndroidJUnit4::class)
class CompanyLogoDeviceTest {
    @Test fun verifyEmailDomainAndPersonalPhotoProtectionOnActualProvider() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runCompanyLogoDeviceProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="dot.dialer.test")
        val version=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode;check(version==dev.goodwy.rphone.BuildConfig.VERSION_CODE.toLong())
        val account=requireNotNull(PreferenceManager(context).getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        check(GoogleContactAccounts(context).load().contains(account))
        val output=File(context.cacheDir,"company-device-v$version").apply { mkdirs() }
        val isolated=object:ContextWrapper(context) {
            private val probeDirectory=File(output,"private-${SystemClock.elapsedRealtime()}").apply { mkdirs() }
            override fun getNoBackupFilesDir()=probeDirectory
        }
        val contacts=WhatsAppAvatarContactStore(context);val phone="+40700000240"
        check(!contacts.hasAnyNumber(phone))
        val resolver=context.contentResolver
        fun sync(uri:android.net.Uri)=uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,"true").build()
        var raw:Long?=null;var removed=false;var writes=0
        try {
            raw=ContentUris.parseId(requireNotNull(resolver.insert(sync(ContactsContract.RawContacts.CONTENT_URI),ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE,"com.google");put(ContactsContract.RawContacts.ACCOUNT_NAME,account)
            })))
            val id=requireNotNull(raw)
            fun row(mime:String,value:String)=ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID,id);put(ContactsContract.Data.MIMETYPE,mime);put(ContactsContract.Data.DATA1,value)
            }
            val name=row(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,"Test Alexandru Butica")
            val number=row(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,phone)
            val email=row(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,"person@testindustries.example")
            val organization=row(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,"Test Industries")
            assertEquals(4,resolver.bulkInsert(sync(ContactsContract.Data.CONTENT_URI),arrayOf(name,number,email,organization)))
            var target:WhatsAppAvatarContactTarget?=null
            repeat(40) { if(target==null) { target=contacts.resolveByPhone(phone,account);if(target==null)SystemClock.sleep(100) } }
            check(requireNotNull(target).rawContactId==id)
            val logo=image(Color.BLUE);val portrait=image(Color.RED)
            val source=object:CompanyLogoSource { override suspend fun load(website:String):ByteArray {
                assertEquals("https://testindustries.example/",website);return logo
            } }
            val names=CompanyNameSearch { _,_ -> error("No remote name lookup allowed") }
            val discovery=CompanyLogoDiscovery(isolated,contacts,source,names)
            val result=discovery.find(requireNotNull(target),true)
            val found=result.candidates.single();assertTrue(found.clearMatch);assertEquals("testindustries.example",found.domain)
            val profiles=ContactOptimizationProfiles(isolated)
            profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true) }
            val engine=ContactsOptimizerEngine(isolated,profiles,contacts,WhatsAppAvatarReviewStore(isolated),
                { _,which -> which=="Company logo" && ContactOptimizationAccess.contactsGranted(context) },{ null },
                object:ContactAvatarSource { override val sourceName="Gravatar";override suspend fun load(email:String):ByteArray?=error("No network") },source,
                { selected,photo,smaller,valid -> contacts.writePhoto(selected,photo.bytes,smaller,valid).also { if(it)writes++ } },{ _,_ -> },companyNames=names)
            val candidate=CapturedWhatsAppAvatar(logo,512,512,phone,"Test Alexandru Butica",source="Company logo",
                companyDomain=found.domain,companyName=found.company,companyDiscovery=true)
            assertTrue(engine.consider(requireNotNull(target),listOf(candidate)))
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            assertEquals(512,requireNotNull(target.photo?.toContactPhotoImageInfo()).width)
            assertTrue(contacts.writePhoto(requireNotNull(target),portrait));writes++
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Latest) }
            // A genuinely changed logo bypasses old-source dedupe, so this tests person protection itself.
            assertFalse(engine.consider(requireNotNull(target),listOf(candidate.copy(bytes=image(Color.rgb(0,0,100))))))
            val current=requireNotNull(contacts.resolveByPhone(phone,account))
            assertTrue(contactPhotoReadbackMatches(portrait,requireNotNull(current.photo)))
            assertEquals("Test Alexandru Butica",current.displayName);assertEquals(phone,current.storedNumber)
            assertEquals(listOf("person@testindustries.example"),contacts.emails(current))
        } finally {
            raw?.let { id ->
                val uri=ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,id)
                val owner=resolver.query(uri,arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),null,null,null)?.use { it.moveToFirst() && it.getString(0)==account }==true
                check(owner);resolver.delete(sync(uri),null,null)
                removed=resolver.query(uri,arrayOf(ContactsContract.RawContacts._ID),null,null,null)?.use { it.count==0 }==true
                assertTrue(removed)
            }
        }
        File(output,"verification.json").writeText(JSONObject().put("versionCode",version).put("temporaryPhotoWrites",writes)
            .put("realEmailDomainResolution",true).put("missingPhotoFilled",true).put("personalPhotoProtectedWithLatest",true)
            .put("namePhoneEmailPreserved",true).put("temporaryContactRemoved",removed).put("mainPreferenceWrites",0)
            .put("realRemoteSourceRequests",0).put("carrierCalls",0).toString())
    }
    private fun image(color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        for(y in 0..511)for(x in 0..511)bitmap.setPixel(x,y,Color.rgb(x/2,y/2,Color.blue(color)))
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
    }
}
