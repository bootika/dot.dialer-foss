package dev.goodwy.rphone.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.R
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPipelineDeviceTest {
    @Test fun installedPipelineWritesLocalTimingsAndPublishesQuietFixture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "photo_pipeline_timing_review"
        manager.createNotificationChannel(NotificationChannel(channel, "Test photo", NotificationManager.IMPORTANCE_LOW))
        try {
            val trace = requireNotNull(beginCallPhotoTrace("call-900001", 1))
            val bitmap = createBitmap(32, 32).apply { eraseColor(Color.BLUE) }
            assertTrue(publishCallPhotoNotification(true, { bitmap }, { true }, { photo ->
                NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_dot_dialer_notification)
                    .setContentTitle("Test Alexandru Butica").setContentText("Photo timing check")
                    .setLargeIcon(photo).setSilent(true).build()
            }, { manager.notify(900001, it) }, trace))
            assertTrue(manager.activeNotifications.any { it.id == 900001 })
            var report = ""
            for (attempt in 0 until 20) {
                report = AppDiagnostics.report(context)
                if (report.contains("session=call-900001 | attempt=1 | stage=PUBLISHED")) break
                android.os.SystemClock.sleep(100)
            }
            for (stage in listOf("REQUESTED", "DECODE_STARTED", "DECODE_COMPLETED", "NOTIFICATION_BUILT", "PUBLISHED")) {
                assertTrue(report.contains("session=call-900001 | attempt=1 | stage=$stage"))
            }
        } finally {
            manager.cancel(900001)
            manager.deleteNotificationChannel(channel)
        }
        assertFalse(manager.activeNotifications.any { it.id == 900001 })
    }
}
