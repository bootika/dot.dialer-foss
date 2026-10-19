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
    private fun install(signature: ByteArray): String {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val signing=ReflectionHelpers.callConstructor(SigningInfo::class.java)
        shadowOf(signing).setSignatures(arrayOf(Signature(signature)))
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName=DictateProtocol.PACKAGE;signingInfo=signing
            applicationInfo=ApplicationInfo().apply {packageName=DictateProtocol.PACKAGE;uid=12345}
        })
        return MessageDigest.getInstance("SHA-256").digest(signature).joinToString("") {"%02x".format(it)}
    }
    @Test fun installationAndSetupIntentAloneNeverGrantTrust() {
        val app=ApplicationProvider.getApplicationContext<Application>();install(byteArrayOf(1,2,3))
        val client=DictateBridgeClient(app)
        assertTrue(client.installed());assertFalse(client.trusted())
        assertEquals(DictateProtocol.SETUP,client.setupIntent().component?.className)
        assertFalse(client.trusted())
    }
    @Test fun approvalRejectsAnAppReplacedWhileSetupWasOpen()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();install(byteArrayOf(1,2,3))
        val client=DictateBridgeClient(app);client.setupIntent();install(byteArrayOf(4,5,6))
        try {client.approveSetup();fail("Changed signer accepted")}catch(e: DictateFailure) {assertEquals("CERTIFICATE_CHANGED",e.code)}
        assertFalse(client.trusted())
    }
    @Test fun pinnedCertificateIsComparedWithActualInstalledSigner() {
        val app=ApplicationProvider.getApplicationContext<Application>();val digest=install(byteArrayOf(1,2,3))
        app.getSharedPreferences("dictate_pairing",0).edit().putString("certificate",digest).commit()
        val client=DictateBridgeClient(app);assertTrue(client.trusted())
        install(byteArrayOf(8,9,10));assertFalse(client.trusted())
    }
}
