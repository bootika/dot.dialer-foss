package dev.goodwy.rphone.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Quiet Samsung shell fixtures; no Telecom calls, contacts or missed-call ledger changes. */
@RunWith(AndroidJUnit4::class)
class NotificationAvatarDeviceTest {
    @Test fun productionAvatarsRenderInSamsungNotificationShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "dot.dialer.test")
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "avatar_visual_review_214"
        val directory = File(context.cacheDir, "notification-avatar-device-214").apply { mkdirs() }
        manager.createNotificationChannel(NotificationChannel(channel, "Test avatar", NotificationManager.IMPORTANCE_LOW))
        try {
            val action = PendingIntent.getBroadcast(context, 7414,
                Intent("dev.goodwy.rphone.AVATAR_REVIEW_NO_ACTION").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val presentation = PendingIntent.getActivity(context, 7415,
                Intent(context, dev.goodwy.rphone.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val unknown = notificationAvatar(null, "?", "unknown_caller")
            assertEquals(0, android.graphics.Color.alpha(unknown.getPixel(0, 0)))
            val person = Person.Builder().setName("Test Alexandru Butica")
                .setIcon(IconCompat.createWithBitmap(unknown)).build()
            val incoming = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_dot_dialer_notification).setLargeIcon(unknown)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, action, action))
                // Android requires this field without a real call foreground service. LOW
                // importance and no high-priority flag keep this fixture in the shade.
                .setFullScreenIntent(presentation, false)
                .setSilent(true).build()
            manager.notify(7414, incoming)
            assertTrue(manager.activeNotifications.any { it.id == 7414 })
            capture(instrumentation, directory, "incoming")
            val count = notificationAvatar(null, "6", "missed_calls")
            val missed = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_dot_dialer_notification).setLargeIcon(count)
                .setContentTitle("Test Alexandru Butica").setContentText("6 missed calls")
                .setNumber(6).setSilent(true).build()
            manager.notify(7414, missed)
            assertEquals(6, manager.activeNotifications.first { it.id == 7414 }.notification.number)
            capture(instrumentation, directory, "missed-count")
        } finally {
            manager.cancel(7414)
            manager.deleteNotificationChannel(channel)
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        }
        assertFalse(manager.activeNotifications.any { it.id == 7414 })
    }

    private fun capture(instrumentation: android.app.Instrumentation, directory: File, name: String) {
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        android.os.SystemClock.sleep(900)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

