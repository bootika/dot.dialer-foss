package dev.goodwy.rphone.controller.license

import android.app.Application
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class GiftLicensePersistenceTest {
    private val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
    private val verifier=GiftGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val id="7a93beef-1111-4111-8111-010203040506"
    private fun grant(version:Int,end:Long?,nonce:String="a".repeat(32)):String {
        val body=JSONObject().put("v",version).put("aud","dot.dialer.test").put("installation",id)
            .put("license","b".repeat(32)).put("entitlement","pro").put("iat",1000).put("exp",1100).put("nonce",nonce)
        if(version==2)body.put("access_until",end?:JSONObject.NULL)
        val enc=Base64.getUrlEncoder().withoutPadding()
        val payload=enc.encodeToString(body.toString().encodeToByteArray())
        val sig=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(payload.encodeToByteArray())}.sign()
        return payload+"."+enc.encodeToString(sig)
    }
    private fun storage()=GiftLicenseStorage(RuntimeEnvironment.getApplication(),"gift-persistence-test","gift-persistence-test.bin") {
        SecretKeySpec(ByteArray(32){9},"AES")
    }
    private fun seed(storage:GiftLicenseStorage,signed:String,refreshed:Long=1000) {
        storage.write(JSONObject().put("installation",id).put("grant",signed).put("refresh_token","a".repeat(43))
            .put("last_clock",1000).put("refreshed",refreshed))
    }
    @Test fun v2StillRequiresFreshLeaseAndConsistentFiniteEnvelope() {
        assertTrue(runCatching {verifier.verify(grant(2,null),"dot.dialer.test",id,2000)}.isFailure)
        assertTrue(runCatching {verifier.verify(grant(2,1050),"dot.dialer.test",id,1000)}.isFailure)
        assertTrue(runCatching {verifier.verify(grant(3,null),"dot.dialer.test",id,1000)}.isFailure)
        assertNull(verifier.verify(grant(2,null),"dot.dialer.test",id,1000).accessUntil)
        assertEquals(1100L,verifier.verify(grant(1,null),"dot.dialer.test",id,1000).accessUntil)
    }
    @Test fun v2PerpetualSurvivesLeaseOutageWhileV1NeverBecomesPerpetual() = runTest {
        val store=storage()
        try { for(version in listOf(1,2)) {
            seed(store,grant(version,null))
            val manager=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={2000L},elapsedClock={1000L}) {
                _,_,_->throw java.net.SocketTimeoutException()
            }
            runCurrent();assertTrue(manager.initialized);assertEquals(version==2,manager.isPro.value)
        } } finally {store.invalidate()}
    }
    @Test fun finiteExpiryJournalSurvivesFailedMainWriteAndRestartRollback() = runTest {
        val store=storage();var wall=1000L;var journal:JSONObject?=null
        val observation=ExpiryObservation({journal},{journal=it})
        try {
            seed(store,grant(2,1500))
            val manager=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={wall},elapsedClock={1000L},
                expiryObservation=observation,writeRecord={throw java.io.IOException()}) {_,_,_->throw java.net.SocketTimeoutException()}
            runCurrent();assertTrue(manager.isPro.value)
            wall=1600;advanceTimeBy(60001);runCurrent();assertFalse(manager.isPro.value)
            assertEquals(1500L,journal!!.getLong("clock_floor"))
            wall=1200
            val restarted=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={wall},elapsedClock={1000L},expiryObservation=observation) {
                _,_,_->throw java.net.SocketTimeoutException()
            }
            runCurrent();assertFalse(restarted.isPro.value)
        } finally {store.invalidate()}
    }
    @Test fun authoritativeInvalidationCannotRestoreOldGiftWhenDenialWriteFails() = runTest {
        val store=storage()
        try {
            seed(store,grant(2,null),-100000)
            val manager=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={1000L},elapsedClock={1000L},
                writeRecord={throw java.io.IOException()}) {_,_,_->throw GiftLicenseException(GiftLicenseError.INVALID)}
            runCurrent();assertFalse(manager.isPro.value);assertNull(store.read())
            val restarted=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={1000L}) {_,_,_->throw java.net.SocketTimeoutException()}
            runCurrent();assertFalse(restarted.isPro.value)
        } finally {store.invalidate()}
    }
    @Test fun signedPerpetualToFiniteReductionCannotKeepOldDurableGiftWhenWriteFails() = runTest {
        val store=storage();var wall=1000L
        try {
            seed(store,grant(2,null),-100000)
            val manager=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={wall},elapsedClock={1000L},
                writeRecord={throw java.io.IOException()}) {_,request,_->JSONObject().put("grant",grant(2,1500,request.getString("nonce")))}
            runCurrent();assertTrue(manager.isPro.value);assertNull(store.read())
            wall=1600;advanceTimeBy(60001);runCurrent();assertFalse(manager.isPro.value)
            val restarted=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={1200L}) {_,_,_->throw java.net.SocketTimeoutException()}
            runCurrent();assertFalse(restarted.isPro.value)
        } finally {store.invalidate()}
    }
    @Test fun perpetualClockRollbackStillAttemptsAuthoritativeRefresh() = runTest {
        val store=storage();var requests=0
        try {
            seed(store,grant(2,null))
            val manager=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={900L},elapsedClock={1000L}) {
                _,_,_->requests++;throw java.net.SocketTimeoutException()
            }
            runCurrent();assertTrue(manager.isPro.value);assertEquals(1,requests)
        } finally {store.invalidate()}
    }
    @Test fun unreadableJournalDoesNotRevokeFiniteGiftButReadableSourceExpiryStillWins() = runTest {
        val store=storage();var wall=1200L
        val journal=ExpiryObservation({throw java.io.IOException()},{throw java.io.IOException()})
        try {
            seed(store,grant(2,1500))
            val current=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={wall},elapsedClock={1000L},expiryObservation=journal) {
                _,_,_->throw java.net.SocketTimeoutException()
            }
            runCurrent();assertTrue(current.isPro.value)
            wall=1600;advanceTimeBy(60001);runCurrent();assertFalse(current.isPro.value)
            assertEquals(1500L,store.read()!!.getLong("clock_floor"))
            wall=1200
            val restarted=GiftLicenseManager("dot.dialer.test",store,verifier,backgroundScope,wallClock={wall},elapsedClock={1000L},expiryObservation=journal) {
                _,_,_->throw java.net.SocketTimeoutException()
            }
            runCurrent();assertFalse(restarted.isPro.value)
        } finally {store.invalidate()}
    }
}
