package io.github.bootika.dotdialer.observability

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application
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
            assertEquals(setOf("package","day","daily_id","version","android","manufacturer","model","language","sim","region","counts"),sent.single().keys().asSequence().toSet())
            stats.record(ProductStatistics.Event.Favorite);testScheduler.runCurrent()
            ProductStatistics.setReportingGate(ReportingMode.OnDemand)
            // Do not let virtual time jump 60 seconds while awaiting real DataStore IO.
            runBlocking { prefs.restoreAllPreferences(mapOf(AppTelemetry.REPORTING_KEY to "on_demand")) }
            testScheduler.advanceUntilIdle();stats.homeVisible();stats.record(ProductStatistics.Event.Dialpad);testScheduler.advanceUntilIdle()
            assertEquals(1,sent.size)
            assertFalse(java.io.File(app.noBackupFilesDir,"product-statistics-v1.json").exists())
        } finally { worker.cancel();ProductStatistics.setReportingGate(ReportingMode.Automatic);java.io.File(app.noBackupFilesDir,"product-statistics-v1.json").delete() }
    }
}
