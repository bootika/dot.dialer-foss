package dev.goodwy.rphone.controller.license

import android.app.Application
import io.github.bootika.dotdialer.core.license.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class PlanAccessTest {
    private val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
    private val verifier=PlanGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val id="6c43ee4d-1111-4111-8111-010203040506"
    private var clock=1000L
    private var elapsed=1000L
    private var trial=0L
    private var tier="free"
    private fun sign(request: JSONObject, change: (JSONObject)->Unit = {}): String {
        val body=JSONObject().put("v",1).put("aud","dot.dialer.test").put("installation",id).put("license","b".repeat(32))
            .put("entitlement","plan_access").put("tier",tier).put("trial_started",trial).put("trial_ends",if(trial==0L)0L else trial+30*86400L)
            .put("iat",clock).put("exp",clock+10*86400).put("nonce",request.getString("nonce"))
        change(body)
        val encoder=Base64.getUrlEncoder().withoutPadding()
        val payload=encoder.encodeToString(body.toString().encodeToByteArray())
        val signature=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
        return payload+"."+encoder.encodeToString(signature)
    }
    private val request get()=JSONObject().put("nonce","a".repeat(32))
    private fun rejected(block: ()->Unit) {assertTrue(runCatching(block).isFailure)}
    @Test fun `signed schema identity tamper nonce trial window and expiration checked`() {
        val valid=sign(request)
        assertEquals(AppPlan.FREE,verifier.verify(valid,"dot.dialer.test",id,clock).entitlements.paid)
        rejected {verifier.verify(valid,"dot.dialer",id,clock)}
        rejected {verifier.verify(valid,"dot.dialer.test","another",clock)}
        rejected {verifier.verify(valid,"dot.dialer.test",id,clock,"c".repeat(32))}
        rejected {verifier.verify(valid.dropLast(10)+"aaaaaaaaaa","dot.dialer.test",id,clock)}
        for(change in listOf<(JSONObject)->Unit>({it.put("tier","owner")},{it.put("extra",true)},{it.put("exp",clock+10*86400+1)},
            {it.put("trial_started",clock).put("trial_ends",clock+100)},{it.put("iat",clock+301)},{it.put("exp",clock)})) {
            rejected {verifier.verify(sign(request,change),"dot.dialer.test",id,clock)}
        }
    }
    @Test fun `real signed state explicit trial offline expiry and base plus rights`() = runBlocking {
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        var stored: JSONObject?=null;var calls=0;var offline=false
        val manager=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,{body->
            assertEquals("10",body.getString("offline_days"))
            calls++;if(offline)throw java.io.IOException()
            if(body.getString("start_trial")=="1" && trial==0L)trial=clock
            JSONObject().put("grant",sign(body))
        },{clock},{elapsed})
        try {
            withTimeout(5000) {while(manager.state.value.revision<2 || manager.state.value.busy)delay(10)}
            assertEquals(AppPlan.FREE,manager.effective);assertEquals(0L,trial)
            manager.refresh(startTrial=true,force=true)
            assertEquals(AppPlan.PREMIUM,manager.effective)
            val started=trial
            manager.refresh(startTrial=true,force=true);assertEquals(started,trial)
            val count=calls;repeat(100) {assertTrue(manager.allows(PlanFeature.AUTOMATIC_OPTIMIZER))};assertEquals(count,calls)
            tier="plus";manager.refresh(force=true)
            offline=true;manager.refresh(force=true);assertEquals(AppPlan.PREMIUM,manager.effective)
            elapsed+=10*86400_000L+1;assertEquals(AppPlan.FREE,manager.effective)
            offline=false;clock=trial+30*86400;manager.refresh(force=true)
            assertEquals(AppPlan.PLUS,manager.effective);assertEquals(AppPlan.PLUS,manager.paid)
            assertFalse(manager.allows(PlanFeature.AUTOMATIC_OPTIMIZER));assertTrue(manager.allows(PlanFeature.MANUAL_OPTIMIZER))
            assertTrue(stored!!.has("grant"))
        } finally {worker.cancel()}
    }
    @Test fun `signed ten day grant survives failures restart and expires exactly at deadline`() = runBlocking {
        var stored: JSONObject? = null
        var fail = false
        var malformed = false
        tier = "premium"
        fun manager(worker: CoroutineScope) = PlanAccessManager("dot.dialer.test", { id }, { stored }, { stored = it }, verifier, worker, { body ->
            if (fail) throw java.io.IOException()
            JSONObject().put("grant", if (malformed) "invalid" else sign(body))
        }, { clock }, { elapsed })
        suspend fun ready(m: PlanAccessManager) { withTimeout(5000) { while (m.state.value.revision < 2 || m.state.value.busy) delay(10) } }
        var worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var m = manager(worker); ready(m)
            assertEquals(AppPlan.PREMIUM, m.effective)
            val original = stored.toString()
            fail = true
            clock += 2*86400; elapsed += 2*86400_000L
            m.refresh(force = true)
            assertEquals(AppPlan.PREMIUM, m.effective)
            assertEquals(original, stored.toString())
            worker.cancel()
            worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            elapsed = 0 // device reboot: restore must use signed wall deadline, never restart ten days
            m = manager(worker); ready(m)
            assertEquals(AppPlan.PREMIUM, m.effective)
            clock += 8*86400-1; elapsed += 8*86400_000L-1000
            m.refresh(force = true)
            assertEquals(AppPlan.PREMIUM, m.effective)
            clock++; elapsed += 1000
            assertEquals(AppPlan.FREE, m.effective)
            fail = false; m.refresh(force = true)
            assertEquals(AppPlan.PREMIUM, m.effective)
            malformed = true; tier = "free"; m.refresh(force = true)
            assertEquals(AppPlan.PREMIUM, m.effective)
            malformed = false; m.refresh(force = true)
            assertEquals(AppPlan.FREE, m.effective)
        } finally { worker.cancel() }
    }
    @Test fun `trial expiry remains local inside valid ten day paid grant`() = runBlocking {
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            clock = 40*86400L; trial = clock-29*86400; tier = "plus"
            val m = PlanAccessManager("dot.dialer.test", { id }, { null }, {}, verifier, worker, { JSONObject().put("grant", sign(it)) }, { clock }, { elapsed })
            withTimeout(5000) { while (m.state.value.revision < 2 || m.state.value.busy) delay(10) }
            assertEquals(AppPlan.PREMIUM, m.effective)
            clock += 86400; elapsed += 86400_000
            assertEquals(AppPlan.PLUS, m.effective)
        } finally { worker.cancel() }
    }
    @Test fun `six product identifiers independent of locale distinguish plus from legacy premium`() {
        val original=Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(6,PlanProducts.all.map {it.id}.toSet().size)
            assertEquals(AppPlan.PLUS,PlanProducts.purchased(setOf("dot_plus_lifetime")))
            assertEquals(AppPlan.PREMIUM,PlanProducts.purchased(setOf("dot_premium_yearly")))
            assertEquals(AppPlan.FREE,PlanProducts.purchased(setOf("unrelated")))
        } finally {Locale.setDefault(original)}
    }
}
