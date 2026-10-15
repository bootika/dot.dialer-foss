package dev.goodwy.rphone.controller.util

import android.content.ContentUris
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

/** Explicit opt-in, read-only contact verification using private device-local evidence, never shipped fixtures. */
@RunWith(AndroidJUnit4::class)
class ContactPhotoReadbackDeviceTest {
    @Test fun savedPhotoIsConfirmedAndItsOldReviewIsRemovedWithoutRewriting() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifySavedProposal")=="true")
        val raw=requireNotNull(args.getString("rawId")).toLong()
        val reviewId=requireNotNull(args.getString("reviewId"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=GlobalContext.get().get<PreferenceManager>()
        val account=requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        val contacts=WhatsAppAvatarContactStore(context)
        val uri=ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,raw)
        val contact=context.contentResolver.query(uri,arrayOf(ContactsContract.RawContacts.CONTACT_ID),null,null,null)!!.use {
            assertTrue(it.moveToFirst());it.getLong(0)
        }
        val input=File(context.cacheDir,"readback-proof-v248")
        val candidate=File(input,"candidate.bin").readBytes()
        val before=File(input,"stored.bin").readBytes()
        val target=requireNotNull(contacts.chooseContact(contact,account))
        assertEquals(raw,target.rawContactId)
        assertArrayEquals(before,target.photo)
        assertTrue(contactPhotoReadbackMatches(candidate,requireNotNull(target.photo)))
        val processor=GlobalContext.get().get<WhatsAppAvatarSyncProcessor>()
        val pinnedBefore=processor.manualPhotoPinned(account,raw)
        processor.reconcileAppliedPhotos()
        val reviews=GlobalContext.get().get<WhatsAppAvatarReviewStore>()
        assertFalse(reviews.forAccount(account).any { it.id==reviewId })
        assertArrayEquals(before,contacts.chooseContact(contact,account)!!.photo)
        assertEquals(pinnedBefore,processor.manualPhotoPinned(account,raw))
        File(input,"verification.json").writeText(JSONObject().put("versionCode",dev.goodwy.rphone.BuildConfig.VERSION_CODE)
            .put("readbackMatches",true).put("oldReviewRemoved",true).put("storedPhotoByteIdentical",true)
            .put("pinPreserved",true).put("contactWrites",0).toString())
    }
}
