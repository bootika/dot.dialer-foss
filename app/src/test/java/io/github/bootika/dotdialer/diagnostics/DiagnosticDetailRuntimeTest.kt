package io.github.bootika.dotdialer.diagnostics

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import io.github.bootika.dotdialer.core.diagnostics.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticDetailRuntimeTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun selectionPersistsColdReloadUntilManualStopAndIsIgnoredByRelease() = runBlocking {
        var clock = 1000L
        val settings = DiagnosticDetailSettings(context, true) { clock }
        settings.select(DiagnosticLevel.VERBOSE)
        val reloaded = DiagnosticDetailSettings(context, true) { clock }.also { it.load() }
        assertEquals(DiagnosticLevel.VERBOSE, reloaded.current())
        assertEquals(DiagnosticLevel.NORMAL, DiagnosticDetailSettings(context, false) { clock }.also { it.load() }.current())
        clock += DiagnosticLevel.WINDOW_MILLIS * 30
        assertEquals(DiagnosticLevel.VERBOSE, reloaded.current())
        assertEquals("until_manual_stop", reloaded.duration())
        assertEquals(DiagnosticLevel.VERBOSE, DiagnosticDetailSettings(context, true) { clock }.also { it.load() }.current())
        settings.select(DiagnosticLevel.NORMAL)
        assertEquals(DiagnosticLevel.NORMAL, DiagnosticDetailSettings(context, true) { clock }.also { it.load() }.current())
    }

    @Test fun oldExpiredSelectionIsNotReactivatedAndNewSelectionHasNoDeadline() = runBlocking {
        val file = File(context.noBackupFilesDir, "diagnostic_detail_level")
        file.parentFile?.mkdirs()
        file.writeText("VERBOSE\n2000")
        val settings = DiagnosticDetailSettings(context, true) { 3000L }.also { it.load() }
        assertEquals(DiagnosticLevel.NORMAL, settings.current())
        settings.select(DiagnosticLevel.VERBOSE)
        assertEquals("VERBOSE\n-1", file.readText())
        settings.select(DiagnosticLevel.NORMAL)
        assertEquals("NORMAL\n0", file.readText())
    }

    @Test fun journalFiltersDetailsAndClearDoesNotChangeReportingOrLevel() = runBlocking {
        AppDiagnostics.initialize(context)
        AppDiagnostics.clear(context)
        AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL)
        AppDiagnostics.recordDetail(DiagnosticDetail.PhotoProbe(1, PhotoDiagnosticResult.NO_MATCH))
        assertFalse(AppDiagnostics.report(context).contains("call_photo_probe"))
        AppDiagnostics.selectLevel(DiagnosticLevel.DEBUG)
        AppDiagnostics.recordDetail(DiagnosticDetail.AudioState(2, true, false))
        AppDiagnostics.recordDetail(DiagnosticDetail.PhotoProbe(1, PhotoDiagnosticResult.NO_MATCH))
        val debug = AppDiagnostics.report(context)
        assertTrue(debug.contains("call_audio")); assertFalse(debug.contains("call_photo_probe"))
        AppDiagnostics.selectLevel(DiagnosticLevel.VERBOSE)
        AppDiagnostics.recordDetail(DiagnosticDetail.PhotoProbe(1, PhotoDiagnosticResult.NO_MATCH))
        assertTrue(AppDiagnostics.report(context).contains("call_photo_probe"))
        AppDiagnostics.clear(context)
        assertFalse(AppDiagnostics.report(context).contains("call_photo_probe"))
        assertEquals(DiagnosticLevel.VERBOSE, AppDiagnostics.detailSettings.current())
        AppDiagnostics.selectLevel(DiagnosticLevel.NORMAL)
    }

    @Test fun providerProbeReadsAggregateAndThumbnailWithoutAnyWritesOrIdentityInOutput() = runBlocking {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider = FixtureProvider(context)
        provider.attachInfo(context, ProviderInfo().apply { authority = "com.android.contacts" })
        ShadowContentResolver.registerProviderInternal("com.android.contacts", provider)
        val probe = readContactPhotoProbe(context, "0740000001", 7)
        assertEquals(PhotoDiagnosticResult.OK, probe.result)
        assertEquals(1, probe.matches); assertEquals(3, probe.rawSources)
        assertEquals(1, probe.googleSources); assertEquals(1, probe.samsungSources)
        assertEquals(1, probe.readableFull); assertEquals(1, probe.readableThumbnails)
        assertEquals(512, probe.maxFullWidth); assertEquals(64, probe.maxThumbnailWidth)
        assertEquals(3, probe.readableRawFull); assertEquals(1024, probe.maxRawFullWidth)
        assertEquals(0, provider.writes)
        val text = DiagnosticDetailFormatter.format(probe)
        for (privateValue in listOf("0740000001", "content://", "com.google", "display_photo", "@")) assertFalse(text.contains(privateValue))
    }

    @Test fun deniedMissingAndBrokenProviderAreNotReportedAsWorkingImages() = runBlocking {
        shadowOf(context).denyPermissions(android.Manifest.permission.READ_CONTACTS)
        assertEquals(PhotoDiagnosticResult.PERMISSION_DENIED, readContactPhotoProbe(context, "0740000001", 1).result)
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider = FixtureProvider(context).apply { empty = true }
        provider.attachInfo(context, ProviderInfo().apply { authority = "com.android.contacts" })
        ShadowContentResolver.registerProviderInternal("com.android.contacts", provider)
        assertEquals(PhotoDiagnosticResult.NO_MATCH, readContactPhotoProbe(context, "0740000001", 1).result)
        provider.empty = false; provider.fail = true
        assertEquals(PhotoDiagnosticResult.ERROR, readContactPhotoProbe(context, "0740000001", 1).result)
        shadowOf(context).denyPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val connection = readCarConnection(context, -1)
        assertFalse(connection.bluetoothPermission); assertEquals(-1, connection.handsFreeState)
    }
    @Test fun rawPhotosCanBeUnavailableWhileTheAggregatePhotoIsReadable() = runBlocking {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider=FixtureProvider(context).apply { rawUnavailable=true }
        provider.attachInfo(context,ProviderInfo().apply { authority="com.android.contacts" })
        ShadowContentResolver.registerProviderInternal("com.android.contacts",provider)
        val probe=readContactPhotoProbe(context,"0700000001",1)
        assertEquals(PhotoDiagnosticResult.OK,probe.result)
        assertEquals(1,probe.readableFull); assertEquals(0,probe.readableRawFull)
        assertEquals(0,probe.maxRawFullWidth); assertEquals(3,probe.rawSources)
        assertEquals(0,provider.writes)
    }

    private class FixtureProvider(context: Context) : ContentProvider() {
        var writes = 0; var empty = false; var fail = false; var rawUnavailable=false
        private val full = image(context, "full.png", 512)
        private val rawFull = image(context, "raw-full.png", 1024)
        private val thumbnail = image(context, "thumbnail.png", 64)
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            if (fail) throw IllegalStateException("unlogged fixture")
            val cursor = MatrixCursor(projection ?: emptyArray())
            if (uri.path.orEmpty().contains("phone_lookup")) {
                if (!empty) cursor.addRow(arrayOf<Any>(12, "content://com.android.contacts/display_photo/12", "content://com.android.contacts/photo/12"))
            } else if (uri == ContactsContract.RawContacts.CONTENT_URI) {
                for ((index, type) in listOf("com.google", "com.samsung.android.contacts", "other").withIndex())
                    cursor.addRow(arrayOf<Any>(index + 20, type))
            } else cursor.addRow(arrayOf(1))
            return cursor
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if(rawUnavailable && uri.path.orEmpty().startsWith("/raw_contacts/")) throw java.io.FileNotFoundException()
            return ParcelFileDescriptor.open(
            when {
                uri.path.orEmpty().startsWith("/raw_contacts/") -> rawFull
                uri.path.orEmpty().contains("display_photo") -> full
                else -> thumbnail
            }, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun getType(uri: Uri) = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri? { writes++; error("No writes permitted") }
        override fun delete(uri: Uri, where: String?, args: Array<out String>?): Int { writes++; error("No writes permitted") }
        override fun update(uri: Uri, values: ContentValues?, where: String?, args: Array<out String>?): Int { writes++; error("No writes permitted") }
        companion object {
            private fun image(context: Context, name: String, size: Int): File {
                val output = File(context.cacheDir, name)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                return output
            }
        }
    }
}
