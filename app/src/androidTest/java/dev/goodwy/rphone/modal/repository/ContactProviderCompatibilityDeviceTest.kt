package dev.goodwy.rphone.modal.repository

import android.content.ContentUris
import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import dev.goodwy.rphone.controller.util.ContactOptimizationProfiles
import dev.goodwy.rphone.controller.util.WhatsAppAvatarContactStore
import dev.goodwy.rphone.controller.util.WhatsAppAvatarReviewStore
import dev.goodwy.rphone.controller.util.WhatsAppAvatarReviewItem
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Explicit read-only probe; only the authorized temporary contact, never arbitrary contacts. */
@RunWith(AndroidJUnit4::class)
class ContactProviderCompatibilityDeviceTest {
    @Test fun inspectExplicitlyRequestedLogoOpacity() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("inspectApprovedLogo") == "true")
        val prefix=requireNotNull(args.getString("approvedLogoPrefix"))
        require(prefix.length in 3..160 && '%' !in prefix && '_' !in prefix)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolver=context.contentResolver
        resolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf(ContactsContract.Contacts.PHOTO_URI),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",arrayOf("$prefix%"),null)!!.use {
            assertEquals("Explicit logo prefix must match one contact",1,it.count);assertTrue(it.moveToFirst())
            val uri=Uri.parse(requireNotNull(it.getString(0)))
            val image=resolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
            requireNotNull(image)
            val pixels=IntArray(image.width*image.height)
            image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
            val result=JSONObject().put("width",image.width).put("height",image.height)
                .put("bitmapHasAlpha",image.hasAlpha()).put("nonOpaquePixels",pixels.count { c -> Color.alpha(c)<255 })
                .put("writes",0)
            File(context.filesDir,"approved-logo-opacity-probe.json").writeText(result.toString())
            image.recycle()
        }
    }
    @Test fun prepareLargeTemporaryPhotoReview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("prepareTemporaryPhotoCandidate") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val metadata=JSONObject(File(context.filesDir,"temporary-contact-provider-probe.json").readText())
        val account=metadata.getString("account")
        val target=requireNotNull(WhatsAppAvatarContactStore(context).resolveByPhone("+40700000233",account))
        assertEquals(metadata.getLong("contactId"),target.contactId)
        assertEquals("Test Alexandru Butica",target.displayName)
        assertNull(target.photo)
        assertTrue(ContactOptimizationProfiles(context).businessAllowed(account))
        val reviews=WhatsAppAvatarReviewStore(context)
        assertTrue("Review queue must have room without dropping other proposals",reviews.forAccount(account).size<100)
        val image=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
        for(y in 0 until 1024) for(x in 0 until 1024) image.setPixel(x,y,
            Color.rgb(x*255/1024,y*255/1024,if((x/146+y/113)%2==0)64 else 192))
        val bytes=ByteArrayOutputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray() }
        image.recycle()
        val id=UUID.randomUUID().toString()
        val item=WhatsAppAvatarReviewItem(id,target.contactId,target.rawContactId,target.phoneDataId,target.displayName,
            target.matchedNumber,account,"$id-candidate.png",null,1024,1024,null,null,"",target.matchStrength,
            System.currentTimeMillis(),source="Company logo",displayNumber=target.displayNumber)
        reviews.enqueue(item,bytes,null)
        File(context.filesDir,"temporary-contact-photo-fixture.json").writeText(JSONObject().put("id",id)
            .put("candidateWidth",1024).put("candidateHeight",1024).put("providerWrites",0).toString())
    }
    @Test fun inspectTemporaryContactProviderColumns() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runTemporaryContactProbe") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolver=context.contentResolver
        val contactIds=mutableSetOf<Long>()
        resolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode("+40700000233")),
            arrayOf(ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.DISPLAY_NAME),null,null,null)!!.use {
            while(it.moveToNext()) {
                assertEquals("Test Alexandru Butica",it.getString(1))
                contactIds += it.getLong(0)
            }
        }
        assertEquals(1,contactIds.size)
        val contactId=contactIds.single()
        val raws=mutableListOf<Long>()
        val result=JSONObject().put("contactId",contactId)
        resolver.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID,ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.CONTACT_ID}=?",arrayOf(contactId.toString()),null)!!.use {
            while(it.moveToNext()) { raws+=it.getLong(0);result.put("account",it.getString(1)) }
        }
        assertEquals(1,raws.size); result.put("rawId",raws.single())
        for((key,uri,column) in listOf(
            Triple("dataRowFlag",ContactsContract.Data.CONTENT_URI,ContactsContract.Data.IS_READ_ONLY),
            Triple("dataRawFlag",ContactsContract.Data.CONTENT_URI,ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY),
            Triple("rawFlag",ContactsContract.RawContacts.CONTENT_URI,ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY),
            Triple("entityFlag",ContactsContract.RawContactsEntity.CONTENT_URI,ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY))) {
            try {
                val selection=if(uri==ContactsContract.RawContacts.CONTENT_URI) "${ContactsContract.RawContacts._ID}=?"
                    else "${ContactsContract.Data.RAW_CONTACT_ID}=?"
                resolver.query(uri,arrayOf(column),selection,arrayOf(raws.single().toString()),null)!!.use {
                    result.put(key,JSONObject().put("accepted",true).put("rows",it.count))
                }
            } catch(error: IllegalArgumentException) { result.put(key,JSONObject().put("accepted",false).put("errorType",error.javaClass.simpleName)) }
        }
        result.put("writes",0)
        val uploading=ContentResolver.getSyncAdapterTypes().filter {
            it.authority==ContactsContract.AUTHORITY && it.supportsUploading()
        }.map { it.accountType }.toSet()
        var accountType:String?=null
        resolver.query(ContactsContract.RawContacts.CONTENT_URI,arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts._ID}=?",arrayOf(raws.single().toString()),null)!!.use {
            assertTrue(it.moveToFirst());accountType=it.getString(0)
        }
        result.put("sourceSupportsUploading",accountType in uploading)
        File(context.filesDir,"temporary-contact-provider-probe.json").writeText(result.toString())
        assertTrue(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contactId).scheme=="content")
    }
}
