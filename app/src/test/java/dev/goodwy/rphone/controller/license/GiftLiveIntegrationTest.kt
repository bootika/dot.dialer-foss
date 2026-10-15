package dev.goodwy.rphone.controller.license

import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import android.app.Application
import org.json.JSONObject

/** Opt-in only. Uses a disposable test-package license and the real HTTPS API/public key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class GiftLiveIntegrationTest {
    @Test fun `Android client redeems and verifies real backend grant`() = runBlocking {
        val path=System.getenv("DIALER_LIVE_FIXTURE")
        org.junit.Assume.assumeTrue(path!=null)
        val fixture=JSONObject(File(path!!).readText())
        val app=RuntimeEnvironment.getApplication();val file=File(app.noBackupFilesDir,"gift-license-v1.bin");file.delete()
        val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val manager=GiftLicenseManager("dot.dialer.test",GiftLicenseStorage(app) {key},GiftGrantVerifier(),worker)
            manager.activate(fixture.getString("code"))
            assertTrue(manager.isPro.value)
            assertTrue(file.exists())
            val restored=GiftLicenseManager("dot.dialer.test",GiftLicenseStorage(app) {key},GiftGrantVerifier(),worker)
            withTimeout(5000) { while (!restored.initialized) delay(10) }
            assertTrue(restored.isPro.value)
        } finally {worker.cancel();file.delete()}
    }
}
