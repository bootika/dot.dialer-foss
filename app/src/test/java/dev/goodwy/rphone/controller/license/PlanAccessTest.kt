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
            .put("iat",clock).put("exp",clock+86400).put("nonce",request.getString("nonce"))
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
        for(change in listOf<(JSONObject)->Unit>({it.put("tier","owner")},{it.put("extra",true)},{it.put("exp",clock+86401)},
            {it.put("trial_started",clock).put("trial_ends",clock+100)},{it.put("iat",clock+301)},{it.put("exp",clock)})) {
            rejected {verifier.verify(sign(request,change),"dot.dialer.test",id,clock)}
        }
    }
    @Test fun `real signed state explicit trial offline expiry and base plus rights`() = runBlocking {
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        var stored: JSONObject?=null;var calls=0;var offline=false
        val manager=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,{body->
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
            elapsed+=86400_001;assertEquals(AppPlan.FREE,manager.effective)
            offline=false;clock=trial+30*86400;manager.refresh(force=true)
            assertEquals(AppPlan.PLUS,manager.effective);assertEquals(AppPlan.PLUS,manager.paid)
            assertFalse(manager.allows(PlanFeature.AUTOMATIC_OPTIMIZER));assertTrue(manager.allows(PlanFeature.MANUAL_OPTIMIZER))
            assertTrue(stored!!.has("grant"))
        } finally {worker.cancel()}
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
