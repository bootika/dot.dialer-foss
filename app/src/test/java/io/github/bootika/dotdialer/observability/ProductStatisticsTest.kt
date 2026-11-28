package io.github.bootika.dotdialer.observability

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application
import android.app.job.JobScheduler
import android.net.NetworkCapabilities
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class ProductStatisticsTest {
    @Test fun `statistics pseudonym rotates daily and does not expose seed`() {
        val seed="6c43ee4d-1111-4111-8111-010203040506"
        val first=ProductStatistics.dailyId(seed,"2026-10-04")
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertEquals(first,ProductStatistics.dailyId(seed,"2026-10-04"))
        assertNotEquals(first,ProductStatistics.dailyId(seed,"2026-10-05"))
        assertNotEquals(first,ProductStatistics.dailyId("other-seed","2026-10-04"))
        assertFalse(first.contains(seed))
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `automatic reports minimal daily snapshot and opt out cancels queued statistics`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR=="gplay")
        val app=RuntimeEnvironment.getApplication(); val prefs=PreferenceManager(app)
        java.io.File(app.noBackupFilesDir,"product-statistics-v1.json").delete()
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to "automatic",PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO to "GB"))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val worker=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val sent=mutableListOf<JSONObject>()
        try {
            val stats=ProductStatistics(app,prefs,worker) { sent.add(JSONObject(it.toString()));true }
            stats.homeVisible();testScheduler.runCurrent()
            assertEquals(1,sent.size);assertEquals("GB",sent.single().getString("region"))
            assertEquals(setOf("package","day","daily_id","version","android","manufacturer","model","language","sim","region","counts","appearance"),sent.single().keys().asSequence().toSet())
            stats.record(ProductStatistics.Event.Favorite);testScheduler.runCurrent()
            ProductStatistics.setReportingGate(ReportingMode.OnDemand)
            // Do not let virtual time jump 60 seconds while awaiting real DataStore IO.
            runBlocking { prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to "on_demand")) }
            testScheduler.advanceUntilIdle();stats.homeVisible();stats.record(ProductStatistics.Event.Dialpad);testScheduler.advanceUntilIdle()
            assertEquals(1,sent.size)
            assertFalse(java.io.File(app.noBackupFilesDir,"product-statistics-v1.json").exists())
        } finally { worker.cancel();ProductStatistics.setReportingGate(ReportingMode.Automatic);java.io.File(app.noBackupFilesDir,"product-statistics-v1.json").delete() }
    }

    @Test
    fun `backup restoring automatic reporting reopens statistics after opting out`() = runBlocking {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val delivered = CompletableDeferred<JSONObject>()
        val statisticsFile = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        statisticsFile.delete()
        try {
            AppTelemetry.initialize(app, prefs)
            AppTelemetry.setReportingMode(prefs, ReportingMode.OnDemand)
            prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
            val statistics = ProductStatistics(app, prefs, worker) {
                delivered.complete(JSONObject(it.toString()))
                true
            }
            val snapshot = withTimeout(5_000) {
                while (!delivered.isCompleted) {
                    statistics.homeVisible()
                    delay(25)
                }
                delivered.await()
            }
            assertEquals(app.packageName, snapshot.getString("package"))
            assertEquals(0, snapshot.getJSONObject("counts").getInt("dialpad"))
        } finally {
            worker.cancel()
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            statisticsFile.delete()
            io.sentry.Sentry.close()
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `home retries unsent counts after a successful snapshot earlier the same day`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val statisticsFile = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        statisticsFile.delete()
        val worker = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var online = true
        val attempts = mutableListOf<JSONObject>()
        try {
            val stats = ProductStatistics(app, prefs, worker) {
                attempts += JSONObject(it.toString())
                online
            }
            stats.homeVisible()
            testScheduler.runCurrent()
            assertEquals(1, attempts.size)
            assertEquals(0, attempts[0].getJSONObject("counts").getInt("dialpad"))
            online = false
            stats.record(ProductStatistics.Event.Dialpad)
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(2, attempts.size)
            assertEquals(1, attempts[1].getJSONObject("counts").getInt("dialpad"))
            assertEquals(1, JSONObject(statisticsFile.readText()).getJSONObject("counts").getInt("dialpad"))
            online = true
            stats.homeVisible()
            testScheduler.runCurrent()
            assertEquals("Returning Home online must retry the failed newer counts", 3, attempts.size)
            assertEquals(1, attempts[2].getJSONObject("counts").getInt("dialpad"))
            stats.homeVisible()
            testScheduler.runCurrent()
            assertEquals("An unchanged successfully sent snapshot must not be resent", 3, attempts.size)
        } finally {
            worker.cancel()
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            statisticsFile.delete()
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `successful home flush cancels stale queue and next event schedules normally`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val file = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        file.delete()
        val worker = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val delivered = mutableListOf<JSONObject>()
        try {
            val stats = ProductStatistics(app, prefs, worker) { delivered += JSONObject(it.toString()); true }
            stats.homeVisible()
            testScheduler.runCurrent()
            stats.record(ProductStatistics.Event.Dialpad)
            testScheduler.runCurrent()
            stats.homeVisible()
            testScheduler.runCurrent()
            assertEquals(2, delivered.size)
            assertEquals(1, delivered.last().getJSONObject("counts").getInt("dialpad"))
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals("Already delivered counts must not be sent by stale queue", 2, delivered.size)
            stats.record(ProductStatistics.Event.ConnectedCall)
            testScheduler.runCurrent()
            assertEquals("A connected call flushes before Home or the UI batching delay", 3, delivered.size)
            assertEquals(1, delivered.last().getJSONObject("counts").getInt("dialpad"))
            assertEquals(1, delivered.last().getJSONObject("counts").getInt("calls"))
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(3, delivered.size)
        } finally {
            worker.cancel()
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed connected call report survives process restart and retries without Home`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val file = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        file.delete()
        val firstProcess = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val secondProcess = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val attempts = mutableListOf<JSONObject>()
        try {
            val initial = ProductStatistics(app, prefs, firstProcess) { attempts += JSONObject(it.toString()); false }
            initial.record(ProductStatistics.Event.ConnectedCall)
            testScheduler.runCurrent()
            assertEquals(1, attempts.size)
            assertTrue(JSONObject(file.readText()).getBoolean("connected_call_pending"))
            val job = app.getSystemService(JobScheduler::class.java).allPendingJobs.single {
                it.service.className == ConnectedCallStatisticsJob::class.java.name
            }
            assertTrue(job.requiredNetwork?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            assertTrue(job.isPersisted)
            firstProcess.cancel()

            val restarted = ProductStatistics(app, prefs, secondProcess) { attempts += JSONObject(it.toString()); attempts.size >= 3 }
            assertTrue("A failed worker attempt remains pending", restarted.retryConnectedCalls())
            assertFalse("A successful worker attempt clears pending state", restarted.retryConnectedCalls())
            assertFalse("A stale job exits without transmitting another report", restarted.retryConnectedCalls())
            assertEquals(3, attempts.size)
            assertTrue(attempts.all { it.getJSONObject("counts").getInt("calls") == 1 })
            assertFalse(JSONObject(file.readText()).has("connected_call_pending"))
        } finally {
            firstProcess.cancel(); secondProcess.cancel()
            ConnectedCallStatisticsJob.cancel(app)
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `revoking automatic reporting cancels a pending connected call job`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val file = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        file.delete()
        val worker = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var attempts = 0
        try {
            val stats = ProductStatistics(app, prefs, worker) { attempts++; false }
            stats.record(ProductStatistics.Event.ConnectedCall)
            testScheduler.runCurrent()
            assertEquals(1, attempts)
            ProductStatistics.setReportingGate(ReportingMode.OnDemand)
            assertFalse(stats.retryConnectedCalls())
            runBlocking { prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.OnDemand.value)) }
            testScheduler.runCurrent()
            assertFalse(file.exists())
            assertTrue(app.getSystemService(JobScheduler::class.java).allPendingJobs.none {
                it.service.className == ConnectedCallStatisticsJob::class.java.name
            })
            assertEquals(1, attempts)
        } finally {
            worker.cancel()
            ConnectedCallStatisticsJob.cancel(app)
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            file.delete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `opting out deletes dirty counts and cancels retries without network`() = runTest {
        org.junit.Assume.assumeTrue(BuildConfig.FLAVOR == "gplay")
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value))
        ProductStatistics.setReportingGate(ReportingMode.Automatic)
        val file = java.io.File(app.noBackupFilesDir, "product-statistics-v1.json")
        file.delete()
        val worker = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val attempts = mutableListOf<JSONObject>()
        try {
            val stats = ProductStatistics(app, prefs, worker) { attempts += JSONObject(it.toString()); attempts.size == 1 }
            stats.homeVisible()
            testScheduler.runCurrent()
            stats.record(ProductStatistics.Event.Favorite)
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(2, attempts.size)
            stats.record(ProductStatistics.Event.Dialpad)
            testScheduler.runCurrent()
            ProductStatistics.setReportingGate(ReportingMode.OnDemand)
            runBlocking { prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.OnDemand.value)) }
            testScheduler.runCurrent()
            stats.homeVisible()
            stats.record(ProductStatistics.Event.Swipe)
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(2, attempts.size)
            assertFalse(file.exists())
            runBlocking { prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to ReportingMode.Automatic.value)) }
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            stats.homeVisible()
            testScheduler.runCurrent()
            assertEquals(3, attempts.size)
            assertEquals(0, attempts.last().getJSONObject("counts").getInt("favorites"))
            assertEquals(0, attempts.last().getJSONObject("counts").getInt("dialpad"))
            assertEquals(0, attempts.last().getJSONObject("counts").getInt("swipes"))
        } finally {
            worker.cancel()
            ProductStatistics.setReportingGate(ReportingMode.Automatic)
            file.delete()
        }
    }
}
