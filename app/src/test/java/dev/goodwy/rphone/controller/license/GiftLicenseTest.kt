package dev.goodwy.rphone.controller.license

import android.app.Application
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class GiftLicenseTest {
    private val keys=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier=GiftGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val install="6c43ee4d-1111-4111-8111-010203040506"
    private val nonce="a".repeat(32)
    private fun body()=JSONObject().put("v",1).put("aud","dot.dialer.test").put("installation",install)
        .put("license","b".repeat(32)).put("entitlement","pro").put("iat",1000).put("exp",2000).put("nonce",nonce)
    private fun sign(body: JSONObject): String {
        val encoder=Base64.getUrlEncoder().withoutPadding()
        val payload=encoder.encodeToString(body.toString().toByteArray())
        val signature=Signature.getInstance("SHA256withRSA").apply { initSign(keys.private);update(payload.toByteArray()) }.sign()
        return payload+"."+encoder.encodeToString(signature)
    }
    private fun denied(block: ()->Unit) { try { block();fail("Grant accepted") } catch (_: IllegalArgumentException) {} }
    @Test fun `grant validates signature audience device nonce and bounded expiry`() {
        val grant=sign(body());assertEquals(2000L,verifier.verify(grant,"dot.dialer.test",install,1500,nonce).expires)
        denied { verifier.verify(grant,"dot.dialer",install,1500,nonce) }
        denied { verifier.verify(grant,"dot.dialer.test","other",1500,nonce) }
        denied { verifier.verify(grant,"dot.dialer.test",install,1500,"c".repeat(32)) }
        denied { verifier.verify(grant,"dot.dialer.test",install,2000,nonce) }
        denied { verifier.verify(sign(body().put("exp",605801)),"dot.dialer.test",install,1500,nonce) }
        denied { verifier.verify(sign(body().put("iat",1900)),"dot.dialer.test",install,1500,nonce) }
        denied { verifier.verify(sign(body().put("iat",1000.5)),"dot.dialer.test",install,1500,nonce) }
        denied { verifier.verify(sign(body().put("extra","field")),"dot.dialer.test",install,1500,nonce) }
        denied { verifier.verify(grant.replaceRange(1,2,if(grant[1]=='a') "b" else "a"),"dot.dialer.test",install,1500,nonce) }
    }
    @Test fun `license storage is encrypted no backup and tampering is denied`() {
        val app=RuntimeEnvironment.getApplication()
        val key=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage=GiftLicenseStorage(app) { key }
        val file=File(app.noBackupFilesDir,"gift-license-v1.bin")
        file.delete()
        try {
            assertNull(storage.read())
            val token="sensitive-token-test";storage.write(JSONObject().put("refresh_token",token))
            assertFalse(String(file.readBytes()).contains(token));assertEquals(token,storage.read()!!.getString("refresh_token"))
            val bytes=file.readBytes(); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            try { storage.read();fail("Tampered storage read") } catch (_:javax.crypto.AEADBadTagException) {}
        } finally { file.delete() }
    }
    @Test fun `activation verifies response before durable entitlement and restart restores it`() = runBlocking {
        val app=RuntimeEnvironment.getApplication();val file=File(app.noBackupFilesDir,"gift-license-v1.bin");file.delete()
        val key=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage=GiftLicenseStorage(app) { key };val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val manager=GiftLicenseManager("dot.dialer.test",storage,verifier,worker) { action,request,token ->
                assertEquals("redeem",action);assertNull(token)
                assertEquals(setOf("package","installation","nonce","code"),request.keys().asSequence().toSet())
                val now=System.currentTimeMillis()/1000
                JSONObject().put("grant",sign(body().put("installation",request.getString("installation"))
                    .put("nonce",request.getString("nonce")).put("iat",now).put("exp",now+604800)))
                    .put("refresh_token","a".repeat(43))
            }
            manager.activate("DD-"+"A".repeat(40));assertTrue(manager.isPro.value)
            assertTrue(file.exists());assertFalse(String(file.readBytes()).contains("refresh_token"))
            val restored=GiftLicenseManager("dot.dialer.test",storage,verifier,worker)
            withTimeout(5000) { while (!restored.initialized) delay(10) };assertTrue(restored.isPro.value)
        } finally { worker.cancel();file.delete() }
    }
}
