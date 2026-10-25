package dev.goodwy.rphone.recordings

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CaptureReminderDeliveryTest {
    @Test fun oneReminderPerActivationPersistsAndFailedDeliveryIsRetryable() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        val p=c.getSharedPreferences("delivery-test",0);p.edit().clear().commit()
        var posts=0
        CaptureReminderDelivery(p).postOnce("26:123"){posts++;true}
        CaptureReminderDelivery(p).postOnce("26:123"){posts++;true}
        assertEquals(1,posts)
        CaptureReminderDelivery(p).postOnce("26:456"){false}
        CaptureReminderDelivery(p).postOnce("26:456"){posts++;true}
        assertEquals(2,posts)
    }
    @Test fun reminderIsSilentAndExcludedFromLockscreenWithoutChangingPairingChannel() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        CaptureSetupNotifications.channel(c)
        val notification=CaptureSetupNotifications.reactivationNotification(c)
        val manager=c.getSystemService(NotificationManager::class.java)
        assertEquals(Notification.VISIBILITY_SECRET,notification.visibility)
        val channel=manager.getNotificationChannel(notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW,channel.importance)
        assertNull(channel.sound);assertFalse(channel.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,manager.getNotificationChannel("call_capture_setup").importance)
    }
}
