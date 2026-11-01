package dev.goodwy.rphone.recordings

import java.io.File
import java.security.KeyPairGenerator
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import org.conscrypt.TrustManagerImpl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureTlsTest {
    @get:Rule val folder=TemporaryFolder()
    private fun pin()=File(folder.root,"peer.pin")
    private fun manager(file:File=pin(),boot:Int=7,pairing:Boolean=false)=
        CaptureAdbTrustPolicy(file,boot,pairing).trustManager() as TrustManagerImpl
    private fun accept(manager:TrustManagerImpl,cert:X509Certificate=certificate,host:String="127.0.0.1") {
        manager.checkServerTrusted(arrayOf(cert),"RSA",host)
    }
    @Test fun jcaCertificateHasValidSignatureAndStablePublicKey() {
        certificate.verify(keys.public)
        certificate.checkValidity()
        assertArrayEquals(keys.public.encoded,certificate.publicKey.encoded)
        assertEquals(certificate.subjectX500Principal,certificate.issuerX500Principal)
        assertTrue(certificate.serialNumber.signum()>0)
        assertThrows(java.security.SignatureException::class.java){certificate.verify(otherKeys.public)}
        assertThrows(java.security.cert.CertificateExpiredException::class.java){certificate.checkValidity(Date(certificate.notAfter.time+1000))}
    }
    @Test fun standardEngineAcceptsLocalCertificateAndRetainsPin() {
        val file=pin();accept(manager(file));val original=file.readText()
        accept(manager(file));assertEquals(original,file.readText())
    }
    @Test fun cachedAnchorCannotBypassRemoteHostOrChangedKey() {
        val m=manager();accept(m)
        assertThrows(CertificateException::class.java){accept(m,host="192.0.2.1")}
        assertThrows(CertificateException::class.java){accept(m,otherCertificate)}
    }
    @Test fun changedDaemonKeyFailsUntilSuccessfulPairingReset() {
        val file=pin();accept(manager(file))
        val failure=assertThrows(CertificateException::class.java){accept(manager(file),otherCertificate)}
        assertTrue(CaptureAdbTrustPolicy.identityChanged(failure))
        val old=file.readText();accept(manager(file,pairing=true),otherCertificate)
        assertEquals(old,file.readText()) // TLS alone must never reset or overwrite the daemon pin.
        CaptureAdbTrustPolicy.pairingSucceeded(file)
        accept(manager(file),otherCertificate)
        assertNotEquals(old,file.readText())
    }
    @Test fun newBootAllowsNewLocalDaemonKey() {
        val file=pin();accept(manager(file,7));accept(manager(file,8),otherCertificate)
        assertTrue(file.readText().startsWith("8\n"))
    }
    @Test fun corruptedPinFailsClosedIncludingOverflow() {
        for(value in listOf("broken","9999999999\n"+"a".repeat(64),"7\n"+"g".repeat(64),"x".repeat(129))) {
            val file=pin();file.writeText(value)
            assertThrows(CertificateException::class.java){accept(manager(file))}
            assertEquals(value,file.readText())
        }
    }
    @Test fun missingSocketHostAndNonLoopbackRejectWithoutCreatingPin() {
        val file=pin();val m=manager(file)
        assertThrows(CertificateException::class.java){m.checkServerTrusted(arrayOf(certificate),"RSA")}
        assertThrows(CertificateException::class.java){accept(m,host="localhost.evil.example")}
        assertFalse(file.exists())
    }
    @Test fun expiredCertificateRejectedBeforePinPersistence() {
        val file=pin();val expired=captureCertificate(keys,System.currentTimeMillis()-11L*365*86400000)
        assertThrows(CertificateException::class.java){accept(manager(file),expired)}
        assertFalse(file.exists())
    }
    @Test fun weakKeyRejectedBeforePinPersistence() {
        val file=pin();val weak=KeyPairGenerator.getInstance("RSA").apply{initialize(1024)}.generateKeyPair()
        assertThrows(CertificateException::class.java){accept(manager(file),captureCertificate(weak))}
        assertFalse(file.exists())
    }
    @Test fun emptyChainRejected() {
        val file=pin()
        assertThrows(IllegalArgumentException::class.java){manager(file).checkServerTrusted(emptyArray(),"RSA","127.0.0.1")}
        assertFalse(file.exists())
    }
    @Test fun storageUsesConservativeWritableQuotaAndAvoidsOverflow() {
        assertEquals(300L,captureStorageBudget(10,100,300))
        assertEquals(1000L,captureStorageBudget(10,100,3000))
        assertEquals(0L,captureStorageBudget(0,100,3000))
        assertEquals(3000L,captureStorageBudget(Long.MAX_VALUE,4096,3000))
        assertThrows(IllegalArgumentException::class.java){captureStorageBudget(-1,100,3000)}
    }
    companion object {
        private val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        private val otherKeys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        private val certificate=captureCertificate(keys)
        private val otherCertificate=captureCertificate(otherKeys)
    }
}
