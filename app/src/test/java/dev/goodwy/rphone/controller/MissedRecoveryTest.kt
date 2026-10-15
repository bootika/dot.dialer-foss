package dev.goodwy.rphone.controller

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class MissedRecoveryTest {
    @Test fun recoveryIsIdempotentAndRestoresTheBadge() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        val notifications = MissedCallNotifications(context)
        notifications.clear()
        assertTrue(notifications.recover(2, "+40712345678"))
        assertEquals(2, MissedCallBadgeState.read(context)?.count)
        val event = MissedCallBadgeState.read(context)?.event
        assertTrue(notifications.recover(2, "+40712345678"))
        assertEquals(event, MissedCallBadgeState.read(context)?.event)
        manager.cancel(MissedCallNotifications.TAG, MissedCallNotifications.ID)
        assertTrue(MissedCallNotifications(context).recover(2, "+40712345678"))
        assertEquals(2, MissedCallBadgeState.read(context)?.count)
    }
    @Test fun disabledNotificationsAndZeroCountDoNotSuppressTheFallback() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notifications = MissedCallNotifications(context)
        notifications.clear()
        assertFalse(notifications.recover(0, ""))
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertFalse(notifications.recover(1, "+40712345678"))
        assertNull(MissedCallBadgeState.read(context))
    }
}
