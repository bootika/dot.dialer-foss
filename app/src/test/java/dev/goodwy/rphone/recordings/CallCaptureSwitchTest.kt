package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CallCaptureSwitchTest {
    private lateinit var root: File
    @Before fun setup(){root=Files.createTempDirectory("capture-switch-").toFile()}
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun newInstallIsOffAndExistingOptInMigrates() {
        assertFalse(CaptureOptIn(root).state.value)
        File(root,"enabled").writeText("1")
        assertTrue(CaptureOptIn(root).state.value)
    }
    @Test fun offPersistsAcrossRecreationAndOverridesLegacyMarker() {
        File(root,"enabled").writeText("1")
        val state=CaptureOptIn(root);state.set(false)
        assertFalse(CaptureOptIn(root).state.value)
        state.set(true);assertTrue(CaptureOptIn(root).state.value)
        state.set(false);assertFalse(CaptureOptIn(root).state.value)
    }
    @Test fun offInvalidatesInFlightSetupEvenAfterOnAgain() {
        val state=CaptureOptIn(root);state.set(true);val ticket=state.ticket()
        assertTrue(state.accepts(ticket));state.set(false);assertFalse(state.accepts(ticket))
        state.set(true);assertFalse(state.accepts(ticket));assertTrue(state.accepts(state.ticket()))
    }
    @Test fun corruptIntentFailsClosedAndDoesNotTouchSavedAssets() {
        val saved=File(root,"saved.wav").apply {writeText("retained audio")}
        File(root,"feature-enabled").writeText("invalid")
        assertFalse(CaptureOptIn(root).state.value)
        CaptureOptIn(root).set(false);assertEquals("retained audio",saved.readText())
    }
    @Test fun previousExplicitOffStillExposesWirelessCleanupAfterUpgrade() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        File(context.noBackupFilesDir,"call-capture").apply {mkdirs();File(this,"feature-enabled").writeText("0")}
        android.provider.Settings.Global.putInt(context.contentResolver,"adb_wifi_enabled",1)
        val backend=IntegratedCallCapture.get(context)
        backend.refreshWirelessOffStatus()
        assertFalse(backend.enabled());assertTrue(backend.wirelessShutdownPending.value)
        android.provider.Settings.Global.putInt(context.contentResolver,"adb_wifi_enabled",0)
        backend.refreshWirelessOffStatus();assertFalse(backend.wirelessShutdownPending.value)
    }
    @Test fun disabledBackendRejectsCaptureAndLateReplyWithoutDiscoveryOrNetwork()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val backend=IntegratedCallCapture.get(context)
        assertFalse(backend.enabled());val ticket=backend.generation()
        assertFalse(backend.accepts(ticket));assertFalse(backend.available())
        backend.discover();backend.activate();backend.pair("123456",35000)
        assertEquals(CaptureSetupState(),backend.state.value)
        assertThrows(IllegalStateException::class.java){backend.open("START")}
        backend.enable();assertTrue(backend.enabled());val enabledTicket=backend.generation()
        backend.disable();assertFalse(backend.enabled());assertFalse(backend.accepts(enabledTicket))
        assertFalse(backend.available());assertEquals(CaptureSetupState(),backend.state.value)
        backend.enable()
        backend.activate(ticket=enabledTicket);backend.pair("123456",35000,ticket=enabledTicket)
        assertEquals(CaptureSetupState(),backend.state.value)
        backend.disable()
    }
}
