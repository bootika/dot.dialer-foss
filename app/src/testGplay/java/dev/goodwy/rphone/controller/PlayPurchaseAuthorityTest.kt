package dev.goodwy.rphone.controller

import android.app.Application
import dev.goodwy.rphone.controller.license.ExpiryObservation
import io.github.bootika.dotdialer.core.license.AppPlan
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlayPurchaseAuthorityTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val audience = "ro.dotdesign.dotdialer"
    private val installation = "test-installation-1234"
    private val principal = "b".repeat(64)
    private val nonce = "a".repeat(64)
    private val issued = 1_800_000_000L
    private var now = issued
    private var tick = 0L
    private var record: JSONObject? = null
    private var journal: JSONObject? = null
    private var failWrite = false
    private var failJournal = false
    private var invalidations = 0
    private var replyBody = body()
    private var request: JSONObject? = null
    private var auth = ""
    private var nextPrincipal = principal
    private var sequenceCounter=0L
    private var currentBoot=3
    private var completeOverride:Boolean?=null
    private val verifier get() = PlayPurchaseGrantVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
    private fun body(products: List<JSONObject> = emptyList()) = JSONObject().put("v",1).put("source","google_play")
        .put("aud",audience).put("installation",installation).put("nonce",nonce).put("iat",issued)
        .put("principal",principal).put("purchase_available",true)
        .put("operation","verify").put("authoritative",true).put("ownership_complete",true)
        .put("sequence",2)
        .put("exp",issued + 604800).put("products",JSONArray(products))
    private fun product(id: String = "dot_plus_monthly", until: Long? = issued+100) = JSONObject()
        .put("id",id).put("type",if (id.endsWith("lifetime")) "inapp" else "subs")
        .put("entitlement",if(id.contains("premium"))"premium" else "plus")
        .put("access_until",until ?: JSONObject.NULL)
    private fun signed(body: JSONObject): JSONObject {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload.toByteArray(Charsets.US_ASCII)) }.sign()
        return JSONObject().put("payload",payload).put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(signature))
    }
    private fun client(): PlayPurchaseAuthority {
        val service = PlayPurchaseService(audience,PlayPurchaseIdentity(installation,"c".repeat(64)),{ action, secret, input ->
            auth=secret; request=input
            val routed=JSONObject(replyBody.toString()).put("operation",action).put("authoritative",action!="register")
                .put("ownership_complete",if(action=="verify")completeOverride?:true else false)
                .put("sequence",input.getLong("sequence"))
            JSONObject().put("principal",nextPrincipal).put("grant",signed(routed))
        },{ nonce },{++sequenceCounter})
        return PlayPurchaseAuthority(service,audience,installation,verifier,{ record },{
            if(failWrite)throw java.io.IOException("Unavailable")
            record=JSONObject(it.toString())
        },{ invalidations++;record=null },ExpiryObservation({ journal },{
            if(failJournal)throw java.io.IOException("Unavailable")
            journal=JSONObject(it.toString())
        }),{ now },{ tick },{currentBoot})
    }
    @Test fun registerBootstrapsOnlyAndDoesNotInventAccess() {
        val client=client()
        assertEquals(principal,client.prepareCheckout())
        assertNull(client.ownership());assertNull(record)
        assertEquals("c".repeat(64),auth)
        assertFalse(request!!.toString().contains(auth))
        assertEquals(nonce,request!!.getString("nonce"))
        assertThrows(Exception::class.java) { client.refresh() }
        assertNull(client.ownership())
    }
    @Test fun paidThroughDateIsIndependentOfRefreshLeaseAndSurvivesRebootClockRollback() {
        replyBody=body(listOf(product()))
        val client=client()
        assertEquals(AppPlan.PLUS,client.reconcile(emptyList()).plan)
        now=issued+101; tick=101000
        assertEquals(AppPlan.FREE,client.ownership()!!.plan)
        now=issued-10000;tick=0;currentBoot++
        assertEquals(AppPlan.FREE,client().ownership()!!.plan)
    }
    @Test fun permanentLowerTierSurvivesHigherFiniteExpiryAndLeaseOutage() {
        replyBody=body(listOf(product("dot_plus_lifetime",null),product("dot_premium_monthly",issued+100)))
        val client=client();assertEquals(AppPlan.PREMIUM,client.reconcile(emptyList()).plan)
        now=issued+604801;tick=604801000
        assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
        assertEquals(AppPlan.PLUS,client().ownership()!!.plan)
    }
    @Test fun signedEmptyIsSourceOnlyRevocationAndFailedWriteCannotResurrectOldCache() {
        replyBody=body(listOf(product("dot_premium_lifetime",null)))
        val client=client();client.reconcile(emptyList());assertNotNull(record)
        replyBody=body();failWrite=true
        assertEquals(AppPlan.FREE,client.reconcile(emptyList()).plan)
        assertEquals(1,invalidations);assertNull(record)
        assertNull(client().ownership())
    }
    @Test fun invalidBindingNonceSignatureOrProductNeverReplacesPreviousGrant() {
        replyBody=body(listOf(product("dot_plus_lifetime",null)))
        val client=client();client.reconcile(emptyList())
        val cases=listOf(body().put("aud","other.package"),body().put("installation","other-installation"),
            body().put("nonce","d".repeat(64)),body().put("source","manual"),
            body(listOf(product("unknown_monthly"))),body(listOf(product().put("access_until",JSONObject.NULL))),
            body(listOf(product(),product())),body().put("extra",true))
        cases.forEach { invalid ->
            replyBody=invalid
            assertThrows(Exception::class.java) {client.reconcile(emptyList())}
            assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
        }
        assertEquals(0,invalidations)
    }
    @Test fun staleGenerationCannotReduceButFreshSequenceMayCorrectServerClock() {
        replyBody=body(listOf(product("dot_plus_lifetime",null))).put("iat",issued+1)
        val client=client();client.reconcile(emptyList())
        replyBody=body()
        assertThrows(Exception::class.java) { client.reconcile(emptyList()) { false } }
        assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
        assertEquals(AppPlan.FREE,client.reconcile(emptyList()).plan)
    }
    @Test fun changingUnsignedPrincipalCannotSwitchExistingDeviceIdentity() {
        val client=client();client.prepareCheckout()
        nextPrincipal="d".repeat(64)
        assertThrows(Exception::class.java) { client.prepareCheckout() }
        assertEquals(principal,client.service.principal)
    }
    @Test fun purchaseUnavailablePreventsCheckoutWithoutErasingExistingPaidRights() {
        replyBody=body(listOf(product("dot_plus_lifetime",null))).put("purchase_available",false)
        val client=client();client.reconcile(emptyList())
        assertThrows(Exception::class.java) { client.prepareCheckout() }
        assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
    }
    @Test fun signedNewReceiptPrincipalCanBeRestoredWithoutChangingTheDeviceCredential() {
        replyBody=body(listOf(product()))
        val client=client();client.reconcile(emptyList())
        nextPrincipal="d".repeat(64)
        replyBody=body(listOf(product())).put("principal",nextPrincipal)
        client.reconcile(emptyList())
        assertEquals(nextPrincipal,client.service.principal)
        assertEquals("c".repeat(64),auth)
    }
    @Test fun onlyPrivateRequestBodyContainsPurchaseTokenAndDiagnosticRepresentationsAreRedacted() {
        val receipt=PlayPurchaseReceipt("dot_plus_monthly","secret-receipt-123")
        val identity=PlayPurchaseIdentity(installation,"c".repeat(64))
        client().reconcile(listOf(receipt))
        assertEquals("secret-receipt-123",request!!.getJSONArray("purchases").getJSONObject(0).getString("token"))
        assertFalse(receipt.toString().contains("secret-receipt"));assertFalse(identity.toString().contains(identity.secret))
        assertFalse(record.toString().contains("secret-receipt"))
    }
    @Test fun failedExpiryWriteAtOneBoundaryDoesNotSuppressAnotherBoundaryCheckpoint() {
        replyBody=body(listOf(product("dot_plus_monthly",issued+10),product("dot_premium_monthly",issued+20)))
        val client=client();client.reconcile(emptyList())
        failWrite=true;failJournal=true
        now=issued+11;tick=11000;client.ownership()
        failJournal=false
        now=issued+21;tick=21000
        assertEquals(AppPlan.FREE,client.ownership()!!.plan)
        assertEquals(issued+20,journal!!.getLong("clock_floor"))
    }
    @Test fun invalidSignatureAndMalformedTypesAreRejected() {
        val envelope=signed(body(listOf(product())))
        val payload=envelope.getString("payload")
        envelope.put("payload",payload.dropLast(1)+if(payload.last()=='A')"B" else "A")
        assertThrows(Exception::class.java) {verifier.verify(envelope,audience,installation,nonce,2)}
        listOf(body().put("iat","1800000000"),body().put("exp",issued+604801),
            body(listOf(product().put("type","inapp"))),body(listOf(product().put("entitlement","full"))))
            .forEach { invalid -> assertThrows(Exception::class.java) {verifier.verify(signed(invalid),audience,installation,nonce,2)} }
    }
    @Test fun sameBootWallClockJumpCannotFalselyExpireARealPaidGrant() {
        replyBody=body(listOf(product()))
        now=issued+1_000_000
        val client=client();assertEquals(AppPlan.PLUS,client.reconcile(emptyList()).plan)
        now+=1_000_000;tick=50000
        assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
        assertNull(journal)
        assertEquals(AppPlan.PLUS,client().ownership()!!.plan)
        tick=101000
        assertEquals(AppPlan.FREE,client.ownership()!!.plan)
    }
    @Test fun incompleteSignedSnapshotNeverMigratesOrRevokesPriorOwnership() {
        replyBody=body(listOf(product("dot_plus_lifetime",null)))
        val client=client();client.reconcile(emptyList())
        replyBody=body();completeOverride=false
        assertThrows(Exception::class.java) { client.reconcile(emptyList()) }
        assertEquals(AppPlan.PLUS,client.ownership()!!.plan)
    }
    @Test fun failedDurableSequenceReservationPreventsAnyHttpRequest() {
        var called=false
        val service=PlayPurchaseService(audience,PlayPurchaseIdentity(installation,"c".repeat(64)),{_,_,_->called=true;error("Not expected")},
            {nonce},{throw java.io.IOException("Unavailable")})
        assertThrows(Exception::class.java) {service.register()}
        assertFalse(called)
    }
    @Test fun requestSequenceCommitsBeforeUseAndNeverResetsOrReusesAtCounterLimit() {
        val identity=PlayPurchaseIdentity(installation,"c".repeat(64))
        var saved=JSONObject().put("installation",installation).put("secret",identity.secret).put("sequence",0)
        assertEquals(1L,PlayPurchaseService.reserveSequence(identity,saved){saved=it})
        assertEquals(2L,PlayPurchaseService.reserveSequence(identity,saved){saved=it})
        assertThrows(Exception::class.java) {PlayPurchaseService.reserveSequence(identity,saved){throw java.io.IOException("Unavailable")}}
        assertEquals(2L,saved.getLong("sequence"))
        saved.put("sequence",9_007_199_254_740_991L)
        assertThrows(Exception::class.java) {PlayPurchaseService.reserveSequence(identity,saved){error("Must not write")}}
    }
    @Test fun directFeatureReadsEnforceFiniteExpiryWithoutWaitingForUiTickerOrCopyingToLegacyCache() {
        replyBody=body(listOf(product("dot_premium_monthly",issued+100)))
        val authority=client();authority.reconcile(emptyList())
        var legacy:JSONObject?=null
        val cache=PlayStoreOwnershipCache({legacy},{legacy=it},{legacy=null})
        val app=org.robolectric.RuntimeEnvironment.getApplication()
        val helper=PlayStoreViewModel(app,dev.goodwy.rphone.controller.util.PreferenceManager(app),
            ownershipCache=cache,purchaseAuthority=authority)
        assertEquals(AppPlan.PREMIUM,helper.storePlan.value);assertTrue(helper.isPro.value)
        tick=101000
        assertEquals(AppPlan.FREE,helper.storePlan.value);assertFalse(helper.isPro.value)
        assertFalse(helper.isSubPurchased("dot_premium_monthly"));assertNull(legacy)
    }
    @Test fun pendingSnapshotDoesNotDetachOrReplaceEarlierVerifiedPaidAccess() = kotlinx.coroutines.runBlocking {
        replyBody=body(listOf(product("dot_plus_lifetime",null)))
        val authority=client();authority.reconcile(emptyList())
        val previousSequence=sequenceCounter
        val pending=PlayPurchaseSnapshot(PlayStoreOwnership(emptySet(),emptySet()),emptyList(),true)
        assertNull(PlayServerSnapshot.reconcile(pending,true,authority,true){true})
        assertEquals(previousSequence,sequenceCounter)
        assertEquals(AppPlan.PLUS,authority.ownership()!!.plan)
    }
}
