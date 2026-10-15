package dev.goodwy.rphone.controller

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import dev.goodwy.rphone.controller.util.PreferenceManager
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class CallNotificationBadgesTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var missed: MissedCallNotifications

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        missed = MissedCallNotifications(context)
        missed.clear()
    }

    @Test fun `one missed call carries photo person long lived shortcut and safe navigation`() = runBlocking {
        val photo = createBitmap(300, 180).apply { eraseColor(Color.BLUE) }
        missed.show(call(), "Test Alexandru Butica", "photo") { photo }
        val notification = active()
        assertEquals(1, notification.number)
        assertEquals(NotificationCompat.CATEGORY_MISSED_CALL, notification.category)
        assertEquals(Notification.BADGE_ICON_LARGE, notification.badgeIconType)
        assertNotNull(notification.getLargeIcon())
        val style = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("Test Alexandru Butica", style.messages.single().person?.name)
        assertNotNull(style.messages.single().person?.icon)
        assertNotEquals(style.user.name, style.messages.single().person?.name)
        val id = requireNotNull(notification.shortcutId)
        val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == id }
        // Android strips icons from shortcut Persons; the shortcut itself owns the avatar.
        assertEquals("Test Alexandru Butica", ReflectionHelpers.getField<Array<android.app.Person>>(shortcut, "mPersons").single().name)
        assertNotNull(ReflectionHelpers.getField<android.graphics.drawable.Icon>(shortcut, "mIcon"))
        if (Build.VERSION.SDK_INT >= 30) {
            val flag = ReflectionHelpers.getStaticField<Int>(android.content.pm.ShortcutInfo::class.java, "FLAG_LONG_LIVED")
            assertTrue(ReflectionHelpers.getField<Int>(shortcut, "mFlags") and flag != 0)
        }
        assertEquals(android.content.Intent.ACTION_VIEW, shortcut.intent?.action)
        assertEquals("tel", shortcut.intent?.data?.scheme)
        assertFalse(shortcut.intent?.action == android.content.Intent.ACTION_CALL)
        assertEquals(2, notification.actions.size)
    }

    @Test fun `two missed calls switch from person to exact count even for same number`() = runBlocking {
        missed.show(call(), "Test Alexandru Butica", null) { null }
        missed.show(call(), "Test Alexandru Butica", null) { null }
        val notification = active()
        assertEquals(2, notification.number)
        assertEquals(1, manager.activeNotifications.size)
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertNull(notification.shortcutId)
        assertNotNull(notification.getLargeIcon())
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals("2 missed calls", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test fun `counter survives manager recreation and different callers do not select an arbitrary avatar`() = runBlocking {
        missed.show(call(), "Test Alexandru Butica", null) { null }
        MissedCallNotifications(context).show(call("0700000002"), "Mara Ionescu", null) { null }
        MissedCallNotifications(context).show(call("0700000003"), "Radu Popescu", null) { null }
        assertEquals(3, active().number)
        assertNull(active().shortcutId)
        assertTrue(active().extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Mara Ionescu"))
    }

    @Test fun `clearing and swiping start a fresh count without touching ongoing calls`() = runBlocking {
        val ongoing = Notification.Builder(context, "active-test").setSmallIcon(android.R.drawable.sym_action_call).build()
        manager.createNotificationChannel(android.app.NotificationChannel("active-test", "Active calls", NotificationManager.IMPORTANCE_LOW))
        manager.notify(CallNotificationManager.NOTIFICATION_ID, ongoing)
        missed.show(call(), "Test Alexandru Butica", null) { null }
        MissedCallNotifications(context).clear()
        assertEquals(listOf(CallNotificationManager.NOTIFICATION_ID), manager.activeNotifications.map { it.id })
        missed.show(call(), "Mara Ionescu", null) { null }
        assertEquals(1, active().number)
        manager.cancel(MissedCallNotifications.TAG, MissedCallNotifications.ID)
        // System swipe delivers the delete intent; app-side cancel alone does not.
        MissedCallNotifications.dismiss(context, requireNotNull(MissedCallBadgeState.read(context)).event)
        missed.show(call(), "Radu Popescu", null) { null }
        assertEquals(1, active().number)
    }

    @Test fun `replayed removal callback never increments the same call twice`() = runBlocking {
        val first = call()
        missed.show(first, "Test Alexandru Butica", null) { null }
        missed.show(first, "Test Alexandru Butica", null) { null }
        assertEquals(1, active().number)
    }

    @Test fun `acknowledgement during photo decode cannot resurrect a stale badge`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pending = async {
            missed.show(call(), "Test Alexandru Butica", "photo") {
                started.complete(Unit)
                release.await()
                null
            }
        }
        started.await()
        MissedCallNotifications(context).clear()
        release.complete(Unit)
        pending.await()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun `stale callback action cannot clear a newer aggregate`() = runBlocking {
        missed.show(call(), "Test Alexandru Butica", null) { null }
        val oldAction = org.robolectric.Shadows.shadowOf(active().actions.first().actionIntent).savedIntent
        val oldDelete = requireNotNull(active().deleteIntent)
        val event = requireNotNull(active().extras.getString(MissedCallNotifications.EXTRA_EVENT))
        missed.show(call("0700000002"), "Mara Ionescu", null) { null }
        MissedCallNotifications.dismissSingle(context, event)
        oldDelete.send()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, active().number)
        assertEquals(event, oldAction.getStringExtra(MissedCallNotifications.EXTRA_EVENT))
        assertEquals("0700000001", oldAction.getStringExtra(NotificationActivity.EXTRA_NUMBER))
        missed.clear()
        missed.show(call(), "Test Alexandru Butica", null) { null }
        MissedCallNotifications.dismissSingle(context, requireNotNull(active().extras.getString(MissedCallNotifications.EXTRA_EVENT)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun `restricted callers never publish identity photos shortcuts or callback actions`() = runBlocking {
        val restricted = call(presentation = TelecomManager.PRESENTATION_RESTRICTED)
        var loaded = false
        missed.show(restricted, "Test Alexandru Butica", "private-photo") { loaded = true; null }
        assertFalse(loaded)
        val notification = active()
        assertNull(notification.shortcutId)
        assertTrue(notification.actions.isNullOrEmpty())
        val style = requireNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals("Unknown Number", style.messages.single().person?.name)
        assertNull(style.messages.single().person?.uri)
    }

    @Test fun `legacy notifications migrate once and names stay bounded while count grows`() = runBlocking {
        manager.createNotificationChannel(android.app.NotificationChannel(CallNotificationManager.MISSED_CHANNEL_ID, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(400, Notification.Builder(context, CallNotificationManager.MISSED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call).setContentTitle("Mara Ionescu").build())
        repeat(12) { missed.show(call(), "Test Alexandru Butica", null) { null } }
        assertEquals(13, active().number)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(8, active().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.size)
    }

    @Test fun `ongoing native call keeps avatar and controls but does not add to missed badge`() {
        val notifications = CallNotificationManager(context, PreferenceManager(context))
        val photo = createBitmap(64, 64).apply { eraseColor(Color.RED) }
        val notification = notifications.buildCallNotification(call(state = Call.STATE_ACTIVE), "Test Alexandru Butica", photo, null)
        assertEquals(Notification.CATEGORY_CALL, notification.category)
        assertEquals(0, notification.number)
        assertNotNull(notification.getLargeIcon())
        assertNotNull(notification.shortcutId)
        assertFalse(manager.getNotificationChannel(notification.channelId).canShowBadge())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.actions.size >= 2)
    }

    @Test fun `conversation key normalizes formatting and avatar handles initials and recycled photos`() {
        assertEquals(callConversationKey("+40 700 000 001"), callConversationKey("+40700000001"))
        assertNotEquals(callConversationKey("0700000001"), callConversationKey("0700000002"))
        assertFalse(callConversationKey("0700000001").contains("0700000001"))
        val photo = createBitmap(10, 10).apply { recycle() }
        val avatar = notificationAvatar(photo, "A")
        assertEquals(192, avatar.width)
        assertEquals(192, avatar.height)
        assertFalse(avatar.isRecycled)
    }

    @Test fun `count does not depend on immediate system notification delivery`() = runBlocking {
        missed.show(call(), "Test Alexandru Butica", null) { null }
        // Models a notification still queued by Android, not a swipe (no delete intent).
        manager.cancel(MissedCallNotifications.TAG, MissedCallNotifications.ID)
        MissedCallNotifications(context).show(call("0700000002"), "Mara Ionescu", null) { null }
        assertEquals(2, active().number)
    }

    @Test fun `concurrent photo loads cannot lose a missed call`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            missed.show(call(), "Test Alexandru Butica", "photo") {
                started.complete(Unit)
                release.await()
                null
            }
        }
        started.await()
        missed.show(call("0700000002"), "Mara Ionescu", null) { null }
        release.complete(Unit)
        first.await()
        assertEquals(2, active().number)
    }

    @Test fun `system dismissal resets aggregate without clearing ongoing call`() = runBlocking {
        missed.show(call(), "Test Alexandru Butica", null) { null }
        missed.show(call("0700000002"), "Mara Ionescu", null) { null }
        requireNotNull(active().deleteIntent).send()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(manager.activeNotifications.isEmpty())
        assertNull(MissedCallBadgeState.read(context))
        missed.show(call(), "Test Alexandru Butica", null) { null }
        assertEquals(1, active().number)
    }

    @Test fun `incoming call retains answer decline and avatar without missed badge`() {
        val notifications = CallNotificationManager(context, PreferenceManager(context))
        val notification = notifications.buildCallNotification(call(state = Call.STATE_RINGING), "Test Alexandru Butica", null, null)
        val actions = notification.actions.map { org.robolectric.Shadows.shadowOf(it.actionIntent).savedIntent.action }
        assertTrue(actions.contains("ANSWER_CALL"))
        assertTrue(actions.contains("DECLINE_CALL"))
        assertFalse(actions.contains("TOGGLE_MUTE"))
        assertFalse(actions.contains("TOGGLE_SPEAKER"))
        assertNotNull(notification.getLargeIcon())
        assertEquals(0, notification.number)
        assertFalse(manager.getNotificationChannel(notification.channelId).canShowBadge())
    }

    @Test fun `conference identity never uses one participant photograph or shortcut`() {
        val conference = call(state = Call.STATE_ACTIVE)
        ReflectionHelpers.setField(conference.details, "mCallProperties", Call.Details.PROPERTY_CONFERENCE)
        val photo = createBitmap(20, 20).apply { eraseColor(Color.BLUE) }
        val identity = notificationCallerIdentity(context, conference, "Test Alexandru Butica", photo)
        assertEquals(context.getString(dev.goodwy.rphone.R.string.conference_call), identity.name)
        assertEquals("", identity.number)
        assertNull(identity.publishConversation(context))
        assertNull(identity.person.uri)
        assertNotEquals(Color.BLUE, identity.avatar.getPixel(0, 0))
    }

    @Test fun `only unanswered incoming calls increment badge not declined or answered elsewhere`() {
        val details = call().details
        ReflectionHelpers.setField(details, "mConnectTimeMillis", 0L)
        ReflectionHelpers.setField(details, "mCallDirection", Call.Details.DIRECTION_INCOMING)
        for (cause in listOf(android.telecom.DisconnectCause.MISSED, android.telecom.DisconnectCause.REMOTE)) {
            ReflectionHelpers.setField(details, "mDisconnectCause", android.telecom.DisconnectCause(cause))
            assertTrue(shouldNotifyMissedCall(details))
        }
        for (cause in listOf(android.telecom.DisconnectCause.REJECTED, android.telecom.DisconnectCause.LOCAL,
            android.telecom.DisconnectCause.ANSWERED_ELSEWHERE, android.telecom.DisconnectCause.ERROR)) {
            ReflectionHelpers.setField(details, "mDisconnectCause", android.telecom.DisconnectCause(cause))
            assertFalse(shouldNotifyMissedCall(details))
        }
        ReflectionHelpers.setField(details, "mDisconnectCause", android.telecom.DisconnectCause(android.telecom.DisconnectCause.MISSED))
        ReflectionHelpers.setField(details, "mCallDirection", Call.Details.DIRECTION_OUTGOING)
        assertFalse(shouldNotifyMissedCall(details))
        ReflectionHelpers.setField(details, "mCallDirection", Call.Details.DIRECTION_INCOMING)
        ReflectionHelpers.setField(details, "mConnectTimeMillis", 1_000L)
        assertFalse(shouldNotifyMissedCall(details))
    }

    @Test fun `missed notification delivery survives end of service lifetime`() = runBlocking {
        val notifications = CallNotificationManager(context, PreferenceManager(context))
        val serviceLifetime = kotlinx.coroutines.SupervisorJob()
        val serviceScope = kotlinx.coroutines.CoroutineScope(serviceLifetime + kotlinx.coroutines.Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val queued = CompletableDeferred<kotlinx.coroutines.Job>()
        serviceScope.launch {
            queued.complete(notifications.enqueueMissedCallNotification(call(), "Test Alexandru Butica", null) {
                started.complete(Unit)
                release.await()
                true
            })
        }
        val delivery = queued.await()
        started.await()
        serviceLifetime.cancel()
        release.complete(Unit)
        delivery.join()
        assertEquals(1, active().number)
        assertFalse(delivery.isCancelled)
    }

    @Test fun `acknowledgement during queued eligibility check cannot resurrect badge`() = runBlocking {
        val notifications = CallNotificationManager(context, PreferenceManager(context))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delivery = notifications.enqueueMissedCallNotification(call(), "Test Alexandru Butica", null) {
            started.complete(Unit)
            release.await()
            true
        }
        started.await()
        notifications.clearAllMissedCallNotifications()
        release.complete(Unit)
        delivery.join()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    private fun active(): Notification = manager.activeNotifications.single { it.tag == MissedCallNotifications.TAG }.notification

    private fun call(number: String = "0700000001", presentation: Int = TelecomManager.PRESENTATION_ALLOWED, state: Int = Call.STATE_DISCONNECTED): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", number, null))
        ReflectionHelpers.setField(details, "mHandlePresentation", presentation)
        if (Build.VERSION.SDK_INT >= 31) ReflectionHelpers.setField(details, "mState", state)
        ReflectionHelpers.setField(details, "mConnectTimeMillis", 1_000L)
        ReflectionHelpers.setField(call, "mDetails", details)
        ReflectionHelpers.setField(call, "mState", state)
        ReflectionHelpers.setField(call, "mChildren", ArrayList<Call>())
        ReflectionHelpers.setField(call, "mUnmodifiableChildren", Collections.emptyList<Call>())
        ReflectionHelpers.setField(call, "mChildrenIds", ArrayList<String>())
        ReflectionHelpers.setField(call, "mChildrenCached", true)
        return call
    }
}
