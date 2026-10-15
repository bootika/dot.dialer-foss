package io.github.bootika.dotdialer.feature.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class DiagnosticFileExportTest {
    @Test fun clipboardBudgetCountsUtf8BytesAndDoesNotSilentlyTruncate() {
        assertTrue(diagnosticClipboardFits("x".repeat(DIAGNOSTIC_CLIPBOARD_MAX_BYTES / 2)))
        assertFalse(diagnosticClipboardFits("x".repeat(DIAGNOSTIC_CLIPBOARD_MAX_BYTES / 2+1)))
        assertFalse(diagnosticClipboardFits("😀".repeat(150000)))
    }
    @Test fun fullLargeReportIsSharedViaNarrowReadOnlyFileProviderAndOldExportsAreScoped() {
        val context=RuntimeEnvironment.getApplication()
        val folder=File(context.cacheDir,"diagnostic_reports").apply { mkdirs() }
        val old=File(folder,"dot-dialer-diagnostics-00000000-0000-0000-0000-000000000000.txt").apply { writeText("old"); setLastModified(1) }
        val unrelated=File(folder,"user-report.txt").apply { writeText("keep"); setLastModified(1) }
        val report="DOT DIALER DIAGNOSTICS\n"+"event | safe=true\n".repeat(70000)+"final=Șț😀"
        val prepared=prepareDiagnosticExportFile(context,report)
        val preparedUri=if(File.separatorChar == '\\') Uri.Builder().scheme("content").authority(context.packageName+".provider")
            .appendPath("diagnostic_reports").appendPath(prepared.name).build()
            else FileProvider.getUriForFile(context,context.packageName+".provider",prepared)
        val share=diagnosticFileReadIntent(preparedUri)
        assertEquals(Intent.ACTION_SEND,share.action); assertEquals("text/plain",share.type)
        @Suppress("DEPRECATION") val uri=share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content",uri.scheme)
        assertEquals(context.packageName+".provider",uri.authority)
        assertEquals(uri,share.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,share.flags)
        assertFalse(share.hasExtra(Intent.EXTRA_TEXT))
        assertFalse(old.exists()); assertTrue(unrelated.exists())
        val files=folder.listFiles()!!.filter { it.name.startsWith("dot-dialer-diagnostics-") }
        assertEquals(1,files.size)
        assertArrayEquals(report.toByteArray(Charsets.UTF_8),files.single().readBytes())
        val secondFile=prepareDiagnosticExportFile(context,report)
        assertNotEquals(prepared.name,secondFile.name)
        context.resources.getXml(dev.goodwy.rphone.R.xml.file_paths).use { xml ->
            var found=false
            while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) if(xml.eventType==org.xmlpull.v1.XmlPullParser.START_TAG && xml.name=="cache-path" && xml.getAttributeValue(null,"name")=="diagnostic_reports") {
                assertEquals("diagnostic_reports/",xml.getAttributeValue(null,"path"));found=true
            }
            assertTrue(found)
        }
        if(File.separatorChar == '\\') {
            val countBefore=folder.listFiles()!!.size
            try { diagnosticFileShareIntent(context,report); fail("Windows cannot emulate AndroidX canonical slash boundaries") }
            catch(expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("configured root")) }
            assertEquals(countBefore,folder.listFiles()!!.size)
        } else assertEquals(Intent.ACTION_SEND,diagnosticFileShareIntent(context,report).action)
        try { FileProvider.getUriForFile(context,context.packageName+".provider",File(context.cacheDir,"not-shared.txt")); fail("Broad cache access must not be granted") }
        catch(expected: IllegalArgumentException) { /* narrow provider contract */ }
    }
}
