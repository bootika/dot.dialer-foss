package dev.goodwy.rphone.controller.license

import android.app.Application
import io.github.bootika.dotdialer.core.license.AppPlan
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class PlanAccessPersistenceTest {
    private val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
    private val verifier=PlanGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val id="7a93beef-1111-4111-8111-010203040506"
    private fun signed(request:JSONObject,terms:Map<AppPlan,Long?>,trial:Boolean=false):String {
        val paid=JSONObject();terms.forEach {(plan,end)->paid.put(plan.name.lowercase(),end?:JSONObject.NULL)}
        val claims=JSONObject().put("v",2).put("aud","dot.dialer.test").put("installation",id)
            .put("license","b".repeat(32)).put("entitlement","plan_access")
            .put("tier",(terms.keys.maxOrNull()?:AppPlan.FREE).name.lowercase())
            .put("trial_started",if(trial)1000 else 0).put("trial_ends",if(trial)2593000 else 0).put("iat",1000).put("exp",865000)
            .put("nonce",request.getString("nonce")).put("paid_terms",paid)
        val payload=Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().encodeToByteArray())
        val bytes=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
        return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    private suspend fun ready(manager:PlanAccessManager) {
        withTimeout(5000){while(manager.state.value.revision<2 || manager.state.value.busy)delay(10)}
    }
    @Test fun authoritativeSignedDowngradeMustApplyEvenWhenPersistenceFails() = runBlocking {
        var terms:Map<AppPlan,Long?> = mapOf(AppPlan.PREMIUM to null)
        var failWrite=false;var stored:JSONObject?=null
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val manager=PlanAccessManager("dot.dialer.test",{id},{stored},{if(failWrite)throw java.io.IOException();stored=it},
            verifier,worker,{JSONObject().put("grant",signed(it,terms))},{1000L},{1000L})
        try {
            ready(manager);assertEquals(AppPlan.PREMIUM,manager.effective)
            terms=emptyMap();failWrite=true
            manager.refresh(force=true)
            assertEquals(AppPlan.FREE,manager.effective)
        } finally {worker.cancel()}
    }
    @Test fun failedReductionReplacementCannotReviveOldEncryptedGrantAfterRestartOrClearOtherLicences() = runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val aes=javax.crypto.spec.SecretKeySpec(ByteArray(32){7},"AES")
        val plan=GiftLicenseStorage(app,"plan-persistence-test","plan-persistence-test.bin",keyProvider={aes})
        val other=GiftLicenseStorage(app,"unrelated-gift-test","unrelated-gift-test.bin",keyProvider={aes})
        other.write(JSONObject().put("preserved",true))
        var terms:Map<AppPlan,Long?> = mapOf(AppPlan.PREMIUM to null)
        var failWrite=false;var offline=false
        var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun manager()=PlanAccessManager("dot.dialer.test",{id},plan::read,{if(failWrite)throw java.io.IOException();plan.write(it)},
            verifier,worker,{if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,terms))},
            {1000L},{1000L},invalidate=plan::invalidate)
        try {
            var current=manager();ready(current);assertEquals(AppPlan.PREMIUM,current.effective)
            terms=emptyMap();failWrite=true
            current.refresh(force=true)
            assertEquals(AppPlan.FREE,current.effective)
            assertNull(plan.read());assertTrue(other.read()!!.getBoolean("preserved"))
            worker.cancel();worker=CoroutineScope(SupervisorJob()+Dispatchers.IO);offline=true
            current=manager();ready(current)
            assertEquals(AppPlan.FREE,current.effective)
            assertTrue(other.read()!!.getBoolean("preserved"))
        } finally {worker.cancel();plan.invalidate();other.invalidate()}
    }
    @Test fun failedUpgradeWritePreservesPreviousDurableRightsButAppliesVerifiedNewRightsInMemory() = runBlocking {
        var terms:Map<AppPlan,Long?> = mapOf(AppPlan.PLUS to null)
        var failWrite=false;var stored:JSONObject?=null;var invalidations=0
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val manager=PlanAccessManager("dot.dialer.test",{id},{stored},{if(failWrite)throw java.io.IOException();stored=it},
            verifier,worker,{JSONObject().put("grant",signed(it,terms))},{1000L},{1000L},invalidate={invalidations++;stored=null})
        try {
            ready(manager);val original=stored.toString()
            terms=mapOf(AppPlan.PREMIUM to null);failWrite=true
            manager.refresh(force=true)
            assertEquals(AppPlan.PREMIUM,manager.effective);assertEquals(0,invalidations)
            assertEquals(original,stored.toString())
        } finally {worker.cancel()}
    }
    @Test fun observedFiniteExpirySurvivesRestartRollbackAndPreservesLowerPermanentRight() = runBlocking {
        for (failMainWrite in listOf(false, true)) {
            var wall=1000L; var stored:JSONObject?=null; var journal:JSONObject?=null; var failWrite=false; var offline=false
            var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val observation=ExpiryObservation({journal},{journal=it})
            fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},
                {if(failWrite)throw java.io.IOException();stored=it},verifier,worker,
                {if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 1500L)))},
                {wall},{1000L},expiryObservation=observation)
            try {
                var current=manager();ready(current);assertEquals(AppPlan.PREMIUM,current.effective)
                failWrite=failMainWrite;wall=1600
                assertEquals(AppPlan.PLUS,current.effective)
                withTimeout(5000){while(journal?.optLong("clock_floor")!=1500L)delay(10)}
                wall=1200;assertEquals(AppPlan.PLUS,current.effective)
                worker.cancel();worker=CoroutineScope(SupervisorJob()+Dispatchers.IO);offline=true
                current=manager();ready(current)
                assertEquals(AppPlan.PLUS,current.effective)
            } finally {worker.cancel()}
        }
    }
    @Test fun totalPersistenceFailureCannotPromiseRestartDurabilityButKeepsProcessDenialAndPermanentRights() = runBlocking {
        var wall=1000L;var stored:JSONObject?=null;var fail=false
        val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val current=PlanAccessManager("dot.dialer.test",{id},{stored},{if(fail)throw java.io.IOException();stored=it},verifier,worker,
            {JSONObject().put("grant",signed(it,mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 1500L)))},{wall},{1000L},
            expiryObservation=ExpiryObservation({null},{throw java.io.IOException()}))
        try {
            ready(current);fail=true;wall=1600;assertEquals(AppPlan.PLUS,current.effective)
            delay(50);wall=1200;assertEquals(AppPlan.PLUS,current.effective)
            assertFalse(stored!!.has("clock_floor")) // Both durable channels failed; report this platform boundary.
        } finally {worker.cancel()}
    }
    @Test fun expiryJournalIsDurableBeforeReturningRightsEvenIfProcessDiesBeforeQueuedRecordWrite() = runTest {
        for (trial in listOf(false,true)) {
            var wall=1000L;var stored:JSONObject?=null;var journal:JSONObject?=null;var offline=false
            var worker=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
            fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,
                {if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,
                    if(trial)mapOf(AppPlan.PLUS to null) else mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 1500L),trial))},
                {wall},{1000L},expiryObservation=ExpiryObservation({journal},{journal=it}))
            var current=manager();runCurrent();assertEquals(AppPlan.PREMIUM,current.effective)
            wall=if(trial)2593001L else 1600L
            assertEquals(AppPlan.PLUS,current.effective)
            assertNotNull(journal)
            assertFalse(stored!!.has("clock_floor")) // Scheduled source-record IO has not run.
            worker.cancel() // Abrupt process death before the queue is drained.
            wall=1200;offline=true;worker=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
            current=manager();runCurrent();assertEquals(AppPlan.PLUS,current.effective)
            worker.cancel()
        }
    }
    @Test fun unreadableJournalDoesNotInventExpiryButReadableSourceFloorStillPreventsRevival() = runBlocking {
        var wall=1000L;var stored:JSONObject?=null;var offline=false
        var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val journal=ExpiryObservation({throw java.io.IOException()},{throw java.io.IOException()})
        fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,
            {if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 1500L)))},
            {wall},{1000L},expiryObservation=journal)
        try {
            var current=manager();ready(current);worker.cancel()
            offline=true;wall=1200;worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            current=manager();ready(current);assertEquals(AppPlan.PREMIUM,current.effective)
            wall=1600;assertEquals(AppPlan.PLUS,current.effective)
            assertEquals(1500L,stored!!.getLong("clock_floor"))
            worker.cancel();wall=1200;worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            current=manager();ready(current);assertEquals(AppPlan.PLUS,current.effective)
        } finally {worker.cancel()}
    }
    @Test fun oldExpiryFallbackWriterCannotOverwriteConcurrentAuthoritativeSignedReduction() = runBlocking {
        var wall=1000L;var stored:JSONObject?=null;var reduce=false;var block=false
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val requested=java.util.concurrent.CountDownLatch(1)
        val responseReady=java.util.concurrent.CountDownLatch(1)
        var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val current=PlanAccessManager("dot.dialer.test",{id},{stored},{
            if(block && it.has("clock_floor")){entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))};stored=it
        },verifier,worker,{if(reduce){requested.countDown();check(responseReady.await(5,java.util.concurrent.TimeUnit.SECONDS))};JSONObject().put("grant",signed(it,
            if(reduce)emptyMap() else mapOf(AppPlan.PLUS to null,AppPlan.PREMIUM to 1500L)))},{wall},{1000L},invalidate={stored=null})
        try {
            ready(current)
            reduce=true
            val reduction=async(Dispatchers.IO){current.refresh(force=true)}
            assertTrue(requested.await(5,java.util.concurrent.TimeUnit.SECONDS))
            wall=1600;block=true
            val expiry=async(Dispatchers.IO){current.effective}
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            responseReady.countDown()
            delay(50);assertFalse(reduction.isCompleted)
            release.countDown();expiry.await();assertTrue(reduction.await())
            assertEquals(AppPlan.FREE,current.effective)
            worker.cancel();worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val restarted=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,
                {throw java.net.SocketTimeoutException()},{1200L},{1000L})
            ready(restarted);assertEquals(AppPlan.FREE,restarted.effective)
        } finally {responseReady.countDown();release.countDown();worker.cancel()}
    }
    @Test fun closelySpacedFiniteTermsEachCheckpointBeforeDenialWithoutSharingRetryThrottle() = runTest {
        var wall=1000L;var stored:JSONObject?=null;var journal:JSONObject?=null;var offline=false
        var worker=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},{stored=it},verifier,worker,
            {if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,mapOf(AppPlan.PLUS to 1510L,AppPlan.PREMIUM to 1500L)))},
            {wall},{1000L},expiryObservation=ExpiryObservation({journal},{journal=it}))
        var current=manager();runCurrent();assertEquals(AppPlan.PREMIUM,current.effective)
        wall=1501;assertEquals(AppPlan.PLUS,current.effective);assertEquals(1500L,journal!!.getLong("clock_floor"))
        wall=1511;assertEquals(AppPlan.FREE,current.effective);assertEquals(1510L,journal.getLong("clock_floor"))
        worker.cancel();wall=1505;offline=true;worker=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        current=manager();runCurrent();assertEquals(AppPlan.FREE,current.effective);worker.cancel()
    }
    @Test fun dirtyApprovedGrantRetriesLocallyWithoutNetworkAndSurvivesRestart() = runBlocking {
        var stored:JSONObject?=null;var failWrite=false;var offline=false;var tick=1000L;var requests=0
        var terms:Map<AppPlan,Long?> = mapOf(AppPlan.PLUS to null)
        var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},{if(failWrite)throw java.io.IOException();stored=it},verifier,worker,
            {requests++;if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,terms))},{1000L},{tick})
        try {
            var current=manager();ready(current);terms=mapOf(AppPlan.PREMIUM to null);failWrite=true
            current.refresh(force=true);assertEquals(AppPlan.PREMIUM,current.effective)
            failWrite=false;offline=true;tick+=30001;val count=requests
            assertFalse(current.refresh());assertEquals(count,requests) // Server throttle still active; only local IO ran.
            worker.cancel();worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            current=manager();ready(current);assertEquals(AppPlan.PREMIUM,current.effective)
        } finally {worker.cancel()}
    }
    @Test fun newerReductionSupersedesDirtyUpgradeBeforeStorageRecovery() = runBlocking {
        var stored:JSONObject?=null;var failWrite=false;var tick=1000L;var offline=false
        var terms:Map<AppPlan,Long?> = mapOf(AppPlan.PLUS to null)
        var worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun manager()=PlanAccessManager("dot.dialer.test",{id},{stored},{if(failWrite)throw java.io.IOException();stored=it},verifier,worker,
            {if(offline)throw java.net.SocketTimeoutException();JSONObject().put("grant",signed(it,terms))},{1000L},{tick},invalidate={stored=null})
        try {
            var current=manager();ready(current);failWrite=true;terms=mapOf(AppPlan.PREMIUM to null);current.refresh(force=true)
            terms=emptyMap();current.refresh(force=true);assertEquals(AppPlan.FREE,current.effective)
            failWrite=false;offline=true;tick+=30001;current.refresh()
            worker.cancel();worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            current=manager();ready(current);assertEquals(AppPlan.FREE,current.effective)
        } finally {worker.cancel()}
    }
}
