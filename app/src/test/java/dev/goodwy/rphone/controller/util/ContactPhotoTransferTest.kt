package dev.goodwy.rphone.controller.util

import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ContactPhotoTransferTest {
    @Test fun `save copies original bytes to chosen document without changing source`() = runTest {
        val context=RuntimeEnvironment.getApplication()
        val source=File(context.cacheDir,"contact-photo-source.png").apply { writeBytes(byteArrayOf(1,2,3,4,5)) }
        val target=File(context.cacheDir,"contact-photo-destination.png")
        copyContactPhoto(context,Uri.fromFile(source),Uri.fromFile(target))
        assertArrayEquals(source.readBytes(),target.readBytes())
        assertEquals(5L,source.length())
        assertEquals("image/png",contactPhotoMime(context,Uri.fromFile(source)))
    }

    @Test fun `share grants narrow read access or reports unsupported Windows FileProvider paths`() = runTest {
        val context=RuntimeEnvironment.getApplication()
        val source=File(context.cacheDir,"contact-photo-share.png").apply { writeBytes(byteArrayOf(9,8,7)) }
        // AndroidX FileProvider requires slash-separated canonical paths; Windows JVM cannot emulate that boundary.
        if (File.separatorChar == '\\') {
            try { contactPhotoShareIntent(context,Uri.fromFile(source)); fail("Expected host path limitation") }
            catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("configured root")) }
            assertArrayEquals(byteArrayOf(9,8,7),source.readBytes())
            assertTrue(File(context.cacheDir,"shared_contact_photos").listFiles().orEmpty().isEmpty())
            context.resources.getXml(dev.goodwy.rphone.R.xml.file_paths).use { parser ->
                var narrowRoot=false
                while(parser.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if(parser.eventType==org.xmlpull.v1.XmlPullParser.START_TAG && parser.name=="cache-path" &&
                        parser.getAttributeValue(null,"name")=="shared_contact_photos") {
                        assertEquals("shared_contact_photos/",parser.getAttributeValue(null,"path")); narrowRoot=true
                    }
                }
                assertTrue(narrowRoot)
            }
            return@runTest
        }
        val intent=contactPhotoShareIntent(context,Uri.fromFile(source))
        val uri=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java)
        assertEquals(Intent.ACTION_SEND,intent.action)
        assertEquals("image/png",intent.type)
        assertEquals("content",uri?.scheme)
        assertEquals(context.packageName+".provider",uri?.authority)
        assertTrue(requireNotNull(uri).path.orEmpty().contains("shared_contact_photos"))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags)
        assertEquals(uri,intent.clipData?.getItemAt(0)?.uri)
        requireNotNull(context.contentResolver.openInputStream(uri)).use { assertArrayEquals(source.readBytes(),it.readBytes()) }
    }
}
