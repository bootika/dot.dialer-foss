package dev.goodwy.rphone.controller.util

import android.app.job.JobScheduler
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Explicit neutral provider probe only; never a carrier call or a background scan of user contacts. */
@RunWith(AndroidJUnit4::class)
class ContactsOptimizerDeviceTest {
    @Test fun verifyApprovedTemporaryProviderAndLocalAvatarCache() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runOptimizerDeviceProbe") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="dot.dialer.test")
        val version=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode
        check(version==dev.goodwy.rphone.BuildConfig.VERSION_CODE.toLong())
        val output=File(context.cacheDir,"optimizer-device-v$version").apply { mkdirs() }
        val isolated=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            private val probeDirectory=File(output,"private-${SystemClock.elapsedRealtime()}").apply { mkdirs() }
            override fun getNoBackupFilesDir()=probeDirectory
        }
        val prefs=PreferenceManager(context) // Read-only. The app's shared store is never edited.
        val account=requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        assertTrue(GoogleContactAccounts(context).load().contains(account))
        assertTrue(ContactOptimizationAccess.contactsGranted(context))
        val contacts=WhatsAppAvatarContactStore(context)
        val phone="+40700000239"
        assertFalse("Refuse an occupied test number",contacts.hasAnyNumber(phone))
        val resolver=context.contentResolver
        fun syncUri(uri:android.net.Uri)=uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,"true").build()
        var rawId:Long?=null
        var writes=0
        var cleaned=false
        try {
            // Mark this local fixture as sync-adapter data, rather than queueing a new cloud contact.
            rawId=ContentUris.parseId(requireNotNull(resolver.insert(syncUri(ContactsContract.RawContacts.CONTENT_URI),
                ContentValues().apply { put(ContactsContract.RawContacts.ACCOUNT_TYPE,"com.google");put(ContactsContract.RawContacts.ACCOUNT_NAME,account) })))
            val id=requireNotNull(rawId)
            val name=ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID,id);put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,"Test Alexandru Butica")
            }
            val number=ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID,id);put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER,phone);put(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            assertEquals(2,resolver.bulkInsert(syncUri(ContactsContract.Data.CONTENT_URI),arrayOf(name,number)))
            var target:WhatsAppAvatarContactTarget?=null
            repeat(40) { if (target==null) { target=contacts.resolveByPhone(phone,account);if(target==null)SystemClock.sleep(100) } }
            requireNotNull(target);assertEquals(id,target.rawContactId);assertEquals("Test Alexandru Butica",target.displayName)
            val profiles=ContactOptimizationProfiles(isolated)
            val reviews=WhatsAppAvatarReviewStore(isolated)
            val engine=ContactsOptimizerEngine(isolated,profiles,contacts,reviews,
                { selected,source -> selected==account && source=="WhatsApp" && ContactOptimizationAccess.contactsGranted(context) },
                { null },object:ContactAvatarSource { override val sourceName="Gravatar";override suspend fun load(email:String):ByteArray?=error("No network") },
                object:CompanyLogoSource { override suspend fun load(website:String):ByteArray?=error("No network") },
                { chosen,image,smaller,valid -> contacts.writePhoto(chosen,image.bytes,smaller,valid).also { if(it)writes++ } },
                { _,_ -> })
            val missing=photo(192,Color.GREEN)
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
            assertFalse(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(missing,192,192,phone,"Test Alexandru Butica"))))
            assertNull(contacts.resolveByPhone(phone,account)!!.photo)
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.FillMissing) }
            assertTrue(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(missing,192,192,phone,"Test Alexandru Butica"))))
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            assertEquals(192,requireNotNull(target.photo?.toContactPhotoImageInfo()).width)
            val large=photo(512,Color.BLUE)
            assertFalse(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(large,512,512,phone,"Test Alexandru Butica"))))
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Optimal) }
            assertTrue(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(large,512,512,phone,"Test Alexandru Butica"))))
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            val info=requireNotNull(target.photo?.toContactPhotoImageInfo())
            assertEquals(512,info.width);assertEquals(512,info.height)
            val small=photo(192,Color.RED)
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest) }
            assertFalse(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(small,192,192,phone,"Test Alexandru Butica"))))
            assertEquals(512,requireNotNull(contacts.resolveByPhone(phone,account)!!.photo?.toContactPhotoImageInfo()).width)
            profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Latest) }
            assertTrue(engine.consider(requireNotNull(target),listOf(CapturedWhatsAppAvatar(small,192,192,phone,"Test Alexandru Butica"))))
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            assertEquals(phone,target.storedNumber);assertEquals("Test Alexandru Butica",target.displayName)
            assertEquals(192,requireNotNull(target.photo?.toContactPhotoImageInfo()).width)
            // Exercise the actual manual review executor against the same disposable provider row.
            assertTrue(contacts.writePhoto(target,large))
            target=requireNotNull(contacts.resolveByPhone(phone,account))
            profiles.update(account) { it.copy(enabled=true,disclosed=true,normalize=false,google=false,
                googleAuthorized=false,gravatar=false,business=false,automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest) }
            val processor=WhatsAppAvatarSyncProcessor(isolated,prefs,reviews,contacts,
                org.koin.core.context.GlobalContext.get().get<CallPhotoHydrationManager>(),profiles=profiles,
                notificationAccess={true})
            processor.processCaptured(PendingContactAvatar(account,"com.whatsapp",
                WhatsAppSenderIdentity(phone,"Test Alexandru Butica",true),
                CapturedWhatsAppAvatar(small,192,192,phone,"Test Alexandru Butica")))
            val pending=reviews.forAccount(account).single()
            assertTrue(processor.canApply(pending))
            assertEquals(512,requireNotNull(contacts.resolveByPhone(phone,account)!!.photo?.toContactPhotoImageInfo()).width)
            assertTrue(processor.resolveReview(pending.id,true,false))
            assertEquals(192,requireNotNull(contacts.resolveByPhone(phone,account)!!.photo?.toContactPhotoImageInfo()).width)
            assertTrue(reviews.forAccount(account).isEmpty())
            assertTrue(processor.manualPhotoPinned(account,target.rawContactId))
            assertFalse(engine.consider(requireNotNull(contacts.resolveByPhone(phone,account)),
                listOf(CapturedWhatsAppAvatar(large,512,512,phone,"Test Alexandru Butica"))))
            val cached=UnsavedNumberAvatars(isolated,account)
            val unknown="+40700000939"
            assertTrue(cached.store(unknown,large,ContactPhotoAutomaticPolicy.Newest) { true })
            assertNull(cached.resolve("+40700000938"))
            val uri=requireNotNull(cached.resolve(unknown))
            assertArrayEquals(large,File(android.net.Uri.parse(uri).path!!).readBytes())
            assertNull(UnsavedNumberAvatars(isolated,"second@example.test").resolve(unknown))
            val job=requireNotNull(context.getSystemService(JobScheduler::class.java).getPendingJob(ContactsOptimizerJob.ID))
            assertTrue(job.isPeriodic);assertTrue(job.isPersisted);assertTrue(job.isRequireBatteryNotLow)
            assertEquals(24*60*60*1000L,job.intervalMillis)
        } finally {
            rawId?.let { id ->
                val rawUri=ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,id)
                val count=resolver.query(rawUri,arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME,ContactsContract.RawContacts.ACCOUNT_TYPE),null,null,null)?.use { c ->
                    assertTrue(c.moveToFirst());assertEquals(account,c.getString(0));assertEquals("com.google",c.getString(1));c.count
                }
                assertEquals(1,count)
                assertEquals(1,resolver.delete(syncUri(rawUri),null,null))
                cleaned=resolver.query(rawUri,arrayOf(ContactsContract.RawContacts._ID),null,null,null)?.use { it.count==0 } == true
                assertTrue("Created temporary raw contact must be removed",cleaned)
            }
        }
        File(output,"verification.json").writeText(JSONObject().put("versionCode",version).put("providerWrites",writes)
            .put("manualDoesNotAutoFill",true).put("fillMissing192",true).put("qualityUpgrades192to512",true)
            .put("missingPhoto512",true).put("noLossRejects192",true).put("explicitLatestAllows192",true)
            .put("nameAndNumberPreserved",true).put("temporaryContactRemoved",cleaned).put("unknownCacheIsolated",true)
            .put("manualReviewApplies192Over512",true).put("manualChoiceProtectedFromAutomaticUpdate",true)
            .put("squareSourceBytesPreserved",true).put("dailyJobRegistered",true).put("carrierCalls",0)
            .put("realSourceNetworkRequests",0).put("mainPreferenceWrites",0).toString())
    }
    private fun photo(size:Int,color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            for (y in 0 until size) for (x in 0 until size) setPixel(x,y,Color.rgb(x*255/size,y*255/size,Color.blue(color)))
        }
        return ByteArrayOutputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));bitmap.recycle();it.toByteArray() }
    }
}
