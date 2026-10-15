package dev.goodwy.rphone.controller.util

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.core.app.NotificationCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class ContactsOptimizerStateTest {
    private fun context(): Context {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val root = File(app.cacheDir, "optimizer-test-"+UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(app) { override fun getNoBackupFilesDir() = root }
    }
    @Test fun observationsPersistWithoutRefreshingOldImagesAndAccountsAreIsolated() {
        val ctx=context(); val state=ContactsOptimizerState(ctx,"first@example.test")
        assertEquals(100L, state.observe(20,"Google",byteArrayOf(1),100))
        assertEquals(100L, ContactsOptimizerState(ctx,"first@example.test").observe(20,"Google",byteArrayOf(1),200))
        assertEquals(300L, state.observe(20,"Google",byteArrayOf(2),300))
        state.consume(20,300,"Google"); state.pin(20,true); state.cursor(20)
        val reloaded=ContactsOptimizerState(ctx,"first@example.test")
        assertTrue(reloaded.pinned(20)); assertEquals(300L,reloaded.consumed(20)); assertEquals(20L,reloaded.cursor())
        assertEquals("Google",reloaded.source(20))
        assertFalse(ContactsOptimizerState(ctx,"second@example.test").pinned(20))
        reloaded.pin(20,false); assertFalse(state.pinned(20))
    }
    @Test fun corruptStateFailsClosedInsteadOfForgettingProtection() {
        val ctx=context(); val state=ContactsOptimizerState(ctx,"first@example.test"); state.pin(20,true)
        File(ctx.noBackupFilesDir,"contact-optimization/"+ContactOptimizationProfiles.accountKey("first@example.test")+"/optimizer-state.json").writeText("broken")
        assertTrue(runCatching { state.pinned(20) }.isFailure)
    }
    @Test fun legacyReviewIsCanceledWithoutClearingUnrelatedNotificationsOrPublishingResults() {
        val ctx=context(); val manager=ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("whatsapp_avatar_review","Test results",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("missed","Missed calls",NotificationManager.IMPORTANCE_LOW))
        manager.notify("account",84104,NotificationCompat.Builder(ctx,"whatsapp_avatar_review").setSmallIcon(android.R.drawable.sym_action_call).build())
        manager.notify(50,NotificationCompat.Builder(ctx,"missed").setSmallIcon(android.R.drawable.sym_action_call).build())
        ContactOptimizationNotifications.update(ctx,"first@example.test",12)
        assertEquals(listOf(50),manager.activeNotifications.map { it.id })
    }
    @Test fun dailyJobIsPersistentConstrainedAndUnique() {
        val ctx=context(); ContactsOptimizerJob.schedule(ctx); ContactsOptimizerJob.schedule(ctx)
        val jobs=ctx.getSystemService(JobScheduler::class.java).allPendingJobs
        val job=jobs.single { it.id==ContactsOptimizerJob.ID }
        assertTrue(job.isPeriodic); assertTrue(job.isPersisted); assertTrue(job.isRequireBatteryNotLow)
        assertEquals(24*60*60*1000L,job.intervalMillis)
        assertTrue(requireNotNull(job.requiredNetwork).hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }
}
