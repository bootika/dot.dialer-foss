package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PhotoScanCheckpointTest {
    @Test fun interruptedProcessRestoresProgressAsResumableNotRunning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PhotoScanCheckpointStore(context)
        store.save(PhotoScanCheckpoint(photoScanAccountHash("photos@example.test"),
            PhotoScanState(PhotoScanStatus.Running, total = 10, checked = 3, updated = 1, skipped = 1, failed = 1),
            setOf(1L, 2L, 3L), mapOf(3L to PhotoScanFailureKind.InvalidImage)))
        val restored = requireNotNull(PhotoScanCheckpointStore(context).read())
        assertEquals(PhotoScanStatus.Stopped, restored.state.status)
        assertTrue(restored.state.canResume)
        assertEquals(3, restored.state.checked)
        assertEquals(1, restored.state.failureCounts[PhotoScanFailureKind.InvalidImage])
        val raw = context.getSharedPreferences("google_photo_scan_progress", Context.MODE_PRIVATE).getString("checkpoint", "")!!
        assertFalse(raw.contains("photos@example.test"))
        assertFalse(raw.contains("token"))
        store.clear()
        assertNull(store.read())
    }

    @Test fun corruptedProgressIsNotPresentedAsCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("google_photo_scan_progress", Context.MODE_PRIVATE).edit().putString("checkpoint", "broken").commit()
        assertNull(PhotoScanCheckpointStore(context).read())
    }

    @Test fun safeFailureCategoriesSeparateDownloadAndWriteProblems() {
        assertEquals(PhotoScanFailureKind.Network, photoScanFailureKind("Network unavailable"))
        assertEquals(PhotoScanFailureKind.RateLimit, photoScanFailureKind("HTTP 429"))
        assertEquals(PhotoScanFailureKind.GoogleService, photoScanFailureKind("HTTP 503"))
        assertEquals(PhotoScanFailureKind.InvalidImage, photoScanFailureKind("Google photo service returned HTTP 404"))
        assertEquals(PhotoScanFailureKind.ContactChanged, photoScanFailureKind("IdentityChanged"))
    }
}
