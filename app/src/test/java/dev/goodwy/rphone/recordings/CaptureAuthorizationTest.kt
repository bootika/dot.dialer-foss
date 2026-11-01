package dev.goodwy.rphone.recordings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.bootika.dotdialer.core.recordings.DictateCapabilities
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class CaptureAuthorizationTest {
    private lateinit var authorization: CaptureAuthorization
    private val valid=DictateCapabilities(connected=true,licenseState="valid",apiKeyConfigured=true,transcription=true,separateSpeakerChannels=true)
    @Before fun setup(){authorization=CaptureAuthorization(ApplicationProvider.getApplicationContext<Application>());authorization.clear()}
    @After fun cleanup(){authorization.clear()}
    @Test fun failedChecksNeverCreateRights(){authorization.observe(DictateCapabilities());assertFalse(authorization.valid())}
    @Test fun transientFailureRetainsPreviouslyVerifiedRights(){authorization.observe(valid);authorization.observe(DictateCapabilities(connected=true,licenseState="unavailable"));assertTrue(authorization.valid(true))}
    @Test fun confirmedDenialAndRemovedKeyClearRights(){
        authorization.observe(valid);authorization.observe(valid.copy(licenseState="required"));assertFalse(authorization.valid())
        authorization.observe(valid);authorization.observe(valid.copy(apiKeyConfigured=false));assertFalse(authorization.valid())
    }
    @Test fun legacyCompanionDoesNotAuthorizeSeparateChannels(){authorization.observe(valid.copy(separateSpeakerChannels=false));assertTrue(authorization.valid());assertFalse(authorization.valid(true))}
    @Test fun offlineWindowRejectsExpiryClockRollbackAndMonotonicExpiry(){
        assertTrue(CaptureAuthorization.fresh(0,0));assertTrue(CaptureAuthorization.fresh(CaptureAuthorization.MAX_AGE,CaptureAuthorization.MAX_AGE))
        assertFalse(CaptureAuthorization.fresh(-1,0));assertFalse(CaptureAuthorization.fresh(0,-1))
        assertFalse(CaptureAuthorization.fresh(CaptureAuthorization.MAX_AGE+1,0));assertFalse(CaptureAuthorization.fresh(0,CaptureAuthorization.MAX_AGE+1))
    }
    @Test fun corruptOrOversizedReceiptFailsClosedWithoutLoggingItsContents() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val file=java.io.File(context.noBackupFilesDir,"call-capture/authorization.json")
        file.parentFile?.mkdirs()
        file.writeText("{\"account\":\"test.user@example.test\"}")
        assertFalse(authorization.valid(true))
        file.writeText("x".repeat(1025))
        assertFalse(authorization.valid())
        val messages=org.robolectric.shadows.ShadowLog.getLogsForTag("CallCaptureFailure").map{it.msg}
        assertTrue(messages.none{it.contains("test.user") || it.contains("account") || it.contains("xxx")})
    }

    @Test fun interruptedAtomicWriteCanRecoverPreviouslyVerifiedGrant() {
        authorization.observe(valid)
        val context=ApplicationProvider.getApplicationContext<Application>()
        val file=java.io.File(context.noBackupFilesDir,"call-capture/authorization.json")
        val backup=java.io.File(file.path+".bak")
        assertTrue(file.renameTo(backup))
        assertTrue(authorization.valid(true))
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

}
