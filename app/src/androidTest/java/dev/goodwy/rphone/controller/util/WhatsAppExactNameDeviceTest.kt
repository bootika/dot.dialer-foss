package dev.goodwy.rphone.controller.util

import android.app.Notification
import android.app.Person
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
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
import org.koin.core.context.GlobalContext
import java.io.File

/** Production notification extraction/processor and Samsung provider, disposable neutral contacts only. */
@RunWith(AndroidJUnit4::class)
class WhatsAppExactNameDeviceTest {
    /** Read-only probe for a user-selected raw ID; no real contact contents in the report. */
    @Test fun selectedContactPhotoReadback() {
        val raw=InstrumentationRegistry.getArguments().getString("readPhotoRawId")?.toLongOrNull()
        assumeTrue(raw!=null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=GlobalContext.get().get<PreferenceManager>()
        val account=requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        val contacts=WhatsAppAvatarContactStore(context)
        val uri=ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,requireNotNull(raw))
        val contact=context.contentResolver.query(uri,arrayOf(ContactsContract.RawContacts.CONTACT_ID),null,null,null)!!.use {
            assertTrue(it.moveToFirst());it.getLong(0)
        }
        val target=requireNotNull(contacts.chooseContact(contact,account))
        assertEquals(raw,target.rawContactId)
        val info=target.photo?.toContactPhotoImageInfo()
        val output=File(context.cacheDir,"exact-name-v${dev.goodwy.rphone.BuildConfig.VERSION_CODE}").apply { mkdirs() }
        if(InstrumentationRegistry.getArguments().getString("exportReadback")=="true") {
            val stored=requireNotNull(target.photo)
            val review=WhatsAppAvatarReviewStore(context).forAccount(account).first { it.rawContactId==raw && it.kind=="photo" }
            val candidate=requireNotNull(WhatsAppAvatarReviewStore(context).candidateBytes(review))
            File(output,"stored.bin").writeBytes(stored)
            File(output,"candidate.bin").writeBytes(candidate)
            File(output,"comparison.json").writeText(JSONObject()
                .put("same",sameNullablePhoto(candidate,stored)).put("readback",contactPhotoReadbackMatches(candidate,stored))
                .put("hashDistance",perceptualHashDistance(candidate.toContactPhotoImageInfo()!!.perceptualHash,
                    stored.toContactPhotoImageInfo()!!.perceptualHash)).toString())
        }
        File(output,"selected-photo.json").writeText(JSONObject().put("rawId",raw).put("contactId",contact)
            .put("hasPhoto",target.photo!=null).put("width",info?.width?:0).put("height",info?.height?:0)
            .put("uniqueExactName",contacts.resolveByUniqueName(target.displayName,account)?.rawContactId==raw)
            .put("pinned",ContactsOptimizerState(context,account).pinned(raw))
            .put("versionCode",dev.goodwy.rphone.BuildConfig.VERSION_CODE).put("providerWrites",0).toString())
    }

    @Test fun exactNameNotificationUsesPolicyAndRefusesDuplicates() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runExactNameProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=GlobalContext.get().get<PreferenceManager>()
        val account=requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        check(ContactOptimizationAccess.granted(context))
        check(ContactOptimizationAccess.contactsGranted(context))
        val output=File(context.cacheDir,"exact-name-v${dev.goodwy.rphone.BuildConfig.VERSION_CODE}").apply { mkdirs() }
        val isolatedDirectory=File(output,"private-${SystemClock.elapsedRealtime()}").apply { mkdirs() }
        val isolated=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getNoBackupFilesDir()=isolatedDirectory
        }
        val contacts=WhatsAppAvatarContactStore(context)
        val profiles=ContactOptimizationProfiles(isolated)
        profiles.update(account) { it.copy(enabled=true,disclosed=true,normalize=false,gravatar=false,
            business=false,google=false,automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest,autoUpdateExisting=true) }
        val reviews=WhatsAppAvatarReviewStore(isolated).also { it.selectAccount(account) }
        val processor=WhatsAppAvatarSyncProcessor(isolated,prefs,reviews,contacts,
            GlobalContext.get().get<CallPhotoHydrationManager>(),profiles=profiles,
            gravatar=object:ContactAvatarSource {
                override val sourceName="Gravatar"
                override suspend fun load(email:String):ByteArray?=error("Unexpected network")
            },companies=object:CompanyLogoSource {
                override suspend fun load(website:String):ByteArray?=error("Unexpected network")
            })
        val resolver=context.contentResolver
        val name="Test Alexandru Butica - verificare ${SystemClock.elapsedRealtime()}"
        val owned=mutableListOf<Long>()
        fun sync(uri:android.net.Uri)=uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,"true").build()
        fun create(phone:String):Long {
            assertFalse("Temporary number must be unused",contacts.hasAnyNumber(phone))
            val id=ContentUris.parseId(requireNotNull(resolver.insert(sync(ContactsContract.RawContacts.CONTENT_URI),ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_NAME,account)
                put(ContactsContract.RawContacts.ACCOUNT_TYPE,"com.google")
                put(ContactsContract.RawContacts.AGGREGATION_MODE,ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
            })))
            owned+=id
            assertEquals(2,resolver.bulkInsert(sync(ContactsContract.Data.CONTENT_URI),arrayOf(
                ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID,id)
                    put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name)
                },ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID,id)
                    put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER,phone)
                })))
            return id
        }
        fun notification(size:Int,color:Int):Notification {
            val image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            for(y in 0 until size)for(x in 0 until size)image.setPixel(x,y,
                if((x-size/2f)*(x-size/2f)+(y-size/2f)*(y-size/2f)>size*size/4f) Color.TRANSPARENT
                else Color.rgb(x*255/size,y*255/size,Color.blue(color)))
            val sender=Person.Builder().setName(name).setKey("998877665544332@lid").setIcon(Icon.createWithBitmap(image)).build()
            val style=Notification.MessagingStyle(Person.Builder().setName("Test user").build()).setGroupConversation(false)
                .addMessage(Notification.MessagingStyle.Message("Test message",System.currentTimeMillis(),sender))
            return Notification.Builder(context,"test-messages").setSmallIcon(android.R.drawable.sym_action_call)
                .setCategory(Notification.CATEGORY_MESSAGE).setShortcutId("998877665544332@lid").setStyle(style).build()
        }
        check(contacts.resolveByUniqueName(name,account)==null) { "Test name is occupied" }
        var cleaned=false
        try {
            val raw=create("+40700000247")
            var target:WhatsAppAvatarContactTarget?=null
            repeat(40) { if(target==null) { target=contacts.resolveByUniqueName(name,account);if(target==null)SystemClock.sleep(100) } }
            assertEquals(raw,requireNotNull(target).rawContactId)
            assertEquals(WhatsAppAvatarMatchStrength.UniqueName,target.matchStrength)
            processor.process("com.whatsapp",notification(192,Color.GREEN),null)
            assertEquals("updated",profiles.result(account).outcome)
            target=requireNotNull(contacts.resolveByUniqueName(name,account))
            assertEquals(192,target.photo!!.toContactPhotoImageInfo()!!.width)
            assertTrue(reviews.items.value.isEmpty())
            val higher=notification(512,Color.BLUE)
            val captured=requireNotNull(WhatsAppNotificationAvatarExtractor.extract(context,"com.whatsapp",higher,null))
            val expectedSize=minOf(captured.width,contacts.maximumDisplayPhotoDimension()?:Int.MAX_VALUE)
            assertTrue("Notification/provider must offer an actual improvement",expectedSize>192)
            processor.process("com.whatsapp",higher,null)
            target=requireNotNull(contacts.resolveByUniqueName(name,account))
            assertEquals(expectedSize,target.photo!!.toContactPhotoImageInfo()!!.width)
            val large=requireNotNull(target.photo)
            processor.process("com.whatsapp",notification(192,Color.RED),null)
            assertArrayEquals(large,contacts.resolveByUniqueName(name,account)!!.photo)
            processor.protectPhoto(account,raw)
            processor.process("com.whatsapp",notification(512,Color.RED),null)
            assertArrayEquals(large,contacts.resolveByUniqueName(name,account)!!.photo)
            processor.resumeAutomaticPhoto(account,raw)
            val duplicate=create("+40700000347")
            repeat(30) { if(contacts.resolveByUniqueName(name,account)!=null)SystemClock.sleep(100) }
            assertNull(contacts.resolveByUniqueName(name,account))
            processor.process("com.whatsapp",notification(512,Color.RED),null)
            assertArrayEquals(large,contacts.chooseContact(target.contactId,account)!!.photo)
            val duplicateContact=resolver.query(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,duplicate),
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),null,null,null)!!.use { it.moveToFirst();it.getLong(0) }
            assertNull(contacts.chooseContact(duplicateContact,account)!!.photo)
            assertEquals("+40700000247",contacts.chooseContact(target.contactId,account)!!.displayNumber)
        } finally {
            for(id in owned.reversed()) {
                val uri=ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,id)
                resolver.query(uri,arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),null,null,null)!!.use {
                    assertTrue(it.moveToFirst());assertEquals(account,it.getString(0))
                }
                assertEquals(1,resolver.delete(sync(uri),null,null))
                assertEquals(0,resolver.query(uri,arrayOf(ContactsContract.RawContacts._ID),null,null,null)!!.use { it.count })
            }
            cleaned=true
            File(output,"verification.json").writeText(JSONObject().put("temporaryContactsRemoved",cleaned)
                .put("versionCode",dev.goodwy.rphone.BuildConfig.VERSION_CODE).put("temporaryContacts",owned.size)
                .put("mainPreferenceWrites",0).put("realSourceRequests",0).put("carrierCalls",0).toString())
        }
        assertTrue(cleaned)
    }
}
