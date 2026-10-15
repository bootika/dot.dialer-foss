package dev.goodwy.rphone.controller

import android.app.Application
import android.app.NotificationManager
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTelecomManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class, shadows = [MissedCallTelecomResetTest.TelecomResetShadow::class])
class MissedCallTelecomResetTest {
    @Implements(TelecomManager::class)
    class TelecomResetShadow : ShadowTelecomManager() {
        companion object { var resets = 0; var unread = 0; var denied = false }
        @Implementation override fun cancelMissedCallsNotification() {
            resets++
            if (denied) throw SecurityException("Role unavailable")
            unread = 0
        }
    }
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    @Before fun cleanState() {
        TelecomResetShadow.denied = false
        MissedCallNotifications(context).clear()
        TelecomResetShadow.resets = 0
        TelecomResetShadow.unread = 10
    }
    @Test fun tenAcknowledgedCallsResetPlatformAndNextCallRecoversOne() {
        val notifications = MissedCallNotifications(context)
        assertTrue(notifications.recover(TelecomResetShadow.unread, "0700000001"))
        assertEquals(10, manager.activeNotifications.single().notification.number)
        notifications.clear()
        assertEquals(1, TelecomResetShadow.resets)
        assertEquals(0, TelecomResetShadow.unread)
        assertNull(MissedCallBadgeState.read(context))
        assertTrue(manager.activeNotifications.isEmpty())
        TelecomResetShadow.unread++
        assertTrue(MissedCallNotifications(context).recover(TelecomResetShadow.unread, "0700000001"))
        assertEquals(1, manager.activeNotifications.single().notification.number)
    }
    @Test fun validDismissResetsPlatformButStaleEventCannotClearNewCalls() {
        val notifications = MissedCallNotifications(context)
        notifications.recover(10, "0700000001")
        val oldEvent = requireNotNull(MissedCallBadgeState.read(context)).event
        MissedCallNotifications.dismiss(context, oldEvent)
        assertEquals(1, TelecomResetShadow.resets)
        assertEquals(0, TelecomResetShadow.unread)
        notifications.recover(1, "0700000001")
        val newEvent = requireNotNull(MissedCallBadgeState.read(context)).event
        MissedCallNotifications.dismiss(context, oldEvent)
        assertEquals(1, TelecomResetShadow.resets)
        assertEquals(newEvent, MissedCallBadgeState.read(context)?.event)
        assertEquals(1, manager.activeNotifications.single().notification.number)
    }
    @Test fun failedPlatformResetStillClearsLocalStateAndInvalidatesQueuedRecovery() {
        val notifications = MissedCallNotifications(context)
        notifications.recover(10, "0700000001")
        val generation = notifications.generationToken()
        TelecomResetShadow.denied = true
        notifications.clear()
        assertEquals(1, TelecomResetShadow.resets)
        assertNull(MissedCallBadgeState.read(context))
        assertTrue(manager.activeNotifications.isEmpty())
        assertFalse(notifications.recover(10, "0700000001", expectedGeneration = generation))
    }
}
