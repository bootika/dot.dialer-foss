package dev.goodwy.rphone.recordings

import android.app.Application
import android.content.pm.*
import androidx.test.core.app.ApplicationProvider
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class DictateTrustTest {
    private fun install(signature: ByteArray, uid: Int = 12345): String {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val signing=ReflectionHelpers.callConstructor(SigningInfo::class.java)
        shadowOf(signing).setSignatures(arrayOf(Signature(signature)))
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName=DictateProtocol.PACKAGE;signingInfo=signing
            applicationInfo=ApplicationInfo().apply {packageName=DictateProtocol.PACKAGE;this.uid=uid}
        })
        return MessageDigest.getInstance("SHA-256").digest(signature).joinToString("") {"%02x".format(it)}
    }
    @Test fun installationAndSetupIntentAloneNeverGrantTrust() {
        val app=ApplicationProvider.getApplicationContext<Application>();install(byteArrayOf(1,2,3))
        val client=DictateBridgeClient(app)
        assertTrue(client.installed());assertFalse(client.trusted())
        val component = client.setupIntent().component
        assertEquals(DictateProtocol.PACKAGE, component?.packageName)
        assertEquals("ro.dotdesign.dotdictation.integration.DialerBridgeSetupActivity", component?.className)
        assertFalse(client.trusted())
    }
    @Test fun approvalRejectsAnAppReplacedWhileSetupWasOpen()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();install(byteArrayOf(1,2,3))
        val client=DictateBridgeClient(app);client.setupIntent();install(byteArrayOf(4,5,6))
        try {client.approveSetup();fail("Changed signer accepted")}catch(e: DictateFailure) {assertEquals("CERTIFICATE_CHANGED",e.code)}
        assertFalse(client.trusted())
    }
    @Test fun approvalRejectsDifferentUidEvenWithTheSameSigner() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val signature = byteArrayOf(1, 2, 3)
        install(signature)
        val client = DictateBridgeClient(app)
        client.setupIntent()
        install(signature, uid = 23456)

        try {
            client.approveSetup()
            fail("Changed UID accepted")
        } catch (error: DictateFailure) {
            assertEquals("CERTIFICATE_CHANGED", error.code)
        }
        assertFalse(client.trusted())
    }

    @Test fun pinnedCertificateIsComparedWithActualInstalledSigner() {
        val app=ApplicationProvider.getApplicationContext<Application>();val digest=install(byteArrayOf(1,2,3))
        app.getSharedPreferences(DictateProtocol.PAIRING_PREFERENCES,0).edit().putString("certificate",digest).commit()
        val client=DictateBridgeClient(app);assertTrue(client.trusted())
        install(byteArrayOf(8,9,10));assertFalse(client.trusted())
    }
}
