package dev.goodwy.rphone.controller.license

import android.app.Application
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TestAccessTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier = GiftGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded), "test_access", 86400)
    private val install = "6c43ee4d-1111-4111-8111-010203040506"
    private var clock = 1000L
    private var monotonic = 1000L
    private fun sign(request: JSONObject, entitlement: String = "test_access", seconds: Long = 86400): String {
        val body = JSONObject().put("v",1).put("aud","dot.dialer.test").put("installation",request.getString("installation"))
            .put("license","b".repeat(32)).put("entitlement",entitlement).put("iat",clock).put("exp",clock+seconds).put("nonce",request.getString("nonce"))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = encoder.encodeToString(body.toString().toByteArray())
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload.toByteArray()) }.sign()
        return payload + "." + encoder.encodeToString(sig)
    }
    private suspend fun waitFor(manager: TestAccessManager, expected: TestAccessState) {
        withTimeout(5000) { while (manager.state.value != expected) delay(10) }
    }
    @Test fun `cached access performs no request per action and expiration uses monotonic clock`() = runBlocking {
        val worker = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        var stored: JSONObject? = null; var requests = 0
        try {
            val manager = TestAccessManager(true,{install},{stored},{stored=it},verifier,worker,{clock},{monotonic}) {
                requests++; JSONObject().put("grant",sign(it))
            }
            waitFor(manager,TestAccessState.ACTIVE)
            repeat(100) { assertTrue(manager.allowed) }; assertEquals(1,requests)
            manager.refresh(); assertEquals(1,requests)
            monotonic += 86400_001; assertFalse(manager.allowed)
        } finally { worker.cancel() }
    }
    @Test fun `revocation persists on restart offline and reactivation restores access`() = runBlocking {
        var stored: JSONObject? = null; var deny=false; var offline=false
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun manager()=TestAccessManager(true,{install},{stored},{stored=it},verifier,worker,{clock},{monotonic}) {
            if(offline) throw java.io.IOException()
            if(deny) throw TestAccessManager.AccessDenied()
            JSONObject().put("grant",sign(it))
        }
        try {
            val first=manager(); waitFor(first,TestAccessState.ACTIVE)
            deny=true; monotonic+=300_001; first.refresh(); assertFalse(first.allowed)
            assertEquals(TestAccessState.BLOCKED,first.state.value); assertFalse(stored!!.has("grant"))
            offline=true; val restored=manager(); waitFor(restored,TestAccessState.BLOCKED); assertFalse(restored.allowed)
            offline=false; deny=false; monotonic+=300_001; restored.refresh(); assertTrue(restored.allowed)
        } finally { worker.cancel() }
    }
    @Test fun `offline retains valid bounded grant but first installation fails closed`() = runBlocking {
        var stored: JSONObject?=null; var offline=false; val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun manager()=TestAccessManager(true,{install},{stored},{stored=it},verifier,worker,{clock},{monotonic}) {
            if(offline) throw java.io.IOException(); JSONObject().put("grant",sign(it))
        }
        try {
            val active=manager(); waitFor(active,TestAccessState.ACTIVE); offline=true
            monotonic+=300_001; active.refresh(); assertTrue(active.allowed)
            val restored=manager(); waitFor(restored,TestAccessState.ACTIVE); assertTrue(restored.allowed)
            clock+=86401; monotonic+=86401_000; restored.refresh(); assertFalse(restored.allowed)
            assertEquals(TestAccessState.UNAVAILABLE,restored.state.value)
            stored=null; val fresh=manager(); waitFor(fresh,TestAccessState.UNAVAILABLE); assertFalse(fresh.allowed)
        } finally { worker.cancel() }
    }
    @Test fun `Pro grant excessive lease wrong nonce and failed persistence cannot activate test access`() = runBlocking {
        for(mode in 0..3) {
            val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            try {
                val manager=TestAccessManager(true,{install},{null},{if(mode==3) throw java.io.IOException()},verifier,worker,{clock},{monotonic}) {
                    JSONObject().put("grant",sign(if(mode==2) JSONObject(it.toString()).put("nonce","c".repeat(32)) else it,
                        if(mode==0) "pro" else "test_access",if(mode==1) 86401 else 86400))
                }
                waitFor(manager,TestAccessState.UNAVAILABLE); assertFalse(manager.allowed)
            } finally { worker.cancel() }
        }
    }
    @Test fun `release access never registers or waits for network`() {
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val manager=TestAccessManager(false,{error("identity")},{error("read")},{error("write")},verifier,worker) { error("network") }
            assertTrue(manager.allowed); assertEquals(TestAccessState.ACTIVE,manager.state.value)
        } finally { worker.cancel() }
    }
    @Test fun `installation identity survives local data deletion and is package scoped`() {
        val one=TestAccessManager.installationId("12345678","dot.dialer.test")
        assertEquals(one,TestAccessManager.installationId("12345678","dot.dialer.test"))
        assertNotEquals(one,TestAccessManager.installationId("87654321","dot.dialer.test"))
        assertNotEquals(one,TestAccessManager.installationId("12345678","dot.dialer"))
        assertEquals(4,java.util.UUID.fromString(one).version())
    }
}
