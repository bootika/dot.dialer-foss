package dev.goodwy.rphone.controller

import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class MissedCallCountReconciliationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun count() = manager.activeNotifications.single().notification.number
    private fun call(start: Long): Call {
        val call = Shadow.newInstanceOf(Call::class.java)
        val details = Shadow.newInstanceOf(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mHandle", Uri.fromParts("tel", "0700000001", null))
        ReflectionHelpers.setField(details, "mHandlePresentation", TelecomManager.PRESENTATION_ALLOWED)
        ReflectionHelpers.setField(details, "mCreationTimeMillis", start)
        ReflectionHelpers.setField(call, "mDetails", details)
        return call
    }
    @Test fun recoveryBeforeAndAfterDeliveryCountsOneAndNextSameNumberCountsTwo() = runBlocking {
        val missed = MissedCallNotifications(context) { null }
        missed.clear()
        assertTrue(missed.recover(1, "0700000001", 200))
        missed.show(call(100), "Test Alexandru Butica", null) { null }
        assertEquals(1, count())
        MissedCallNotifications(context) { null }.show(call(100), "Test Alexandru Butica", null) { null }
        assertEquals(1, count())
        missed.show(call(300), "Test Alexandru Butica", null) { null }
        assertEquals(2, count())
        assertTrue(missed.recover(2, "0700000001", 400))
        assertEquals(2, count())
        missed.clear()
        Unit
    }
    @Test fun localBeforeRecoveryAndPendingDecodeDoNotDoubleCount() = runBlocking {
        val missed = MissedCallNotifications(context) { null }
        missed.clear()
        missed.show(call(100), "Test Alexandru Butica", null) { null }
        val local = manager.activeNotifications.single().notification
        missed.recover(1, "0700000001", 200)
        assertEquals(1, count())
        assertEquals(local.actions.size, manager.activeNotifications.single().notification.actions.size)
        assertEquals(local.shortcutId, manager.activeNotifications.single().notification.shortcutId)
        assertEquals(local.extras.getString(MissedCallNotifications.EXTRA_EVENT),
            manager.activeNotifications.single().notification.extras.getString(MissedCallNotifications.EXTRA_EVENT))
        missed.clear()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pending = async { missed.show(call(100), "Test Alexandru Butica", "photo") {
            started.complete(Unit); release.await(); null
        } }
        started.await()
        missed.recover(1, "0700000001", 200)
        release.complete(Unit)
        pending.await()
        assertEquals(1, count())
        missed.clear()
        Unit
    }
    @Test fun providerReplacesInflatedLegacyCountAndPreservesPendingDistinctCalls() = runBlocking {
        var snapshot = MissedCallLogSnapshot(setOf(100), listOf(100))
        val missed = MissedCallNotifications(context) { snapshot }
        missed.clear()
        MissedCallBadgeState.write(context, MissedCallBadgeState(5, List(5) { "Test Alexandru Butica" }, "old"))
        missed.show(call(100), "Test Alexandru Butica", null) { null }
        assertEquals(1, count())
        missed.show(call(200), "Test Alexandru Butica", null) { null }
        assertEquals(2, count())
        snapshot = MissedCallLogSnapshot(setOf(100, 300), listOf(100, 300))
        missed.show(call(300), "Test Alexandru Butica", null) { null }
        assertEquals(3, count())
        missed.clear()
        Unit
    }
    @Test fun authoritativeRecoveryCanReduceLegacyCountAndReadRowCannotResurrect() = runBlocking {
        val missed = MissedCallNotifications(context) { MissedCallLogSnapshot(setOf(100), emptyList()) }
        missed.clear()
        MissedCallBadgeState.write(context, MissedCallBadgeState(5, emptyList(), "old"))
        missed.recover(1, "0700000001", 200)
        assertEquals(1, count())
        missed.clear()
        missed.show(call(100), "Test Alexandru Butica", null) { null }
        assertTrue(manager.activeNotifications.isEmpty())
        Unit
    }
    @Test fun legacyStateAndDelayedProviderRowsAreReconciledWithoutLosingRealCalls() = runBlocking {
        var snapshot = MissedCallLogSnapshot(emptySet(), emptyList())
        val missed = MissedCallNotifications(context) { snapshot }
        missed.clear()
        context.createDeviceProtectedStorageContext().getSharedPreferences("missed_call_badge", 0)
            .edit().putString("state", """{"count":5,"names":["Test Alexandru Butica"],"event":"legacy"}""").commit()
        assertTrue(requireNotNull(MissedCallBadgeState.read(context)).callStarts.isEmpty())
        missed.show(call(100), "Test Alexandru Butica", null) { null }
        assertEquals(1, count())
        missed.show(call(200), "Test Alexandru Butica", null) { null }
        assertEquals(2, count())
        snapshot = MissedCallLogSnapshot(setOf(100, 200, 300), listOf(100, 200, 300))
        missed.show(call(300), "Test Alexandru Butica", null) { null }
        assertEquals(3, count())
        val epoch = missed.generationToken()
        missed.clear()
        assertFalse(missed.recover(3, "0700000001", 400, epoch))
        assertTrue(manager.activeNotifications.isEmpty())
        Unit
    }
}
