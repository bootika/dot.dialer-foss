package dev.goodwy.rphone.controller

import com.android.billingclient.api.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=android.app.Application::class)
class PlayStoreOwnershipRefreshTest {
    private val ok=BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
    private val failure=BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED).build()
    private fun purchase(id:String="dot_plus_monthly",state:Int=0,ack:Boolean=true)=Purchase(JSONObject()
        .put("productId",id).put("purchaseToken","private-test-token").put("purchaseState",state).put("acknowledged",ack).toString(),"")
    @Test fun duplicateQueryReplyCannotReplaceFirstAcceptedSubscriptionTokenOrPendingState() {
        val replies=mutableMapOf<String,(BillingResult,List<Purchase>)->Unit>()
        val results=mutableListOf<PlayPurchaseSnapshot?>()
        PlayStoreOwnershipRefresh({type,done->replies[type]=done},{p,done->done(p.flatMap{it.products}.toSet())},{true},{})
            .read(results::add)
        replies.getValue(BillingClient.ProductType.SUBS)(ok,listOf(purchase()))
        replies.getValue(BillingClient.ProductType.SUBS)(ok,listOf(purchase(id="dot_premium_monthly",state=4)))
        replies.getValue(BillingClient.ProductType.INAPP)(ok,emptyList())
        val snapshot=results.single()!!
        assertEquals(setOf("dot_plus_monthly"),snapshot.subscriptions.single().products)
        assertFalse(snapshot.pending)
    }
    @Test fun snapshotWaitsForBothQueriesAndAcknowledgementBeforeExposingCheckoutCapability() {
        val replies=mutableMapOf<String,(BillingResult,List<Purchase>)->Unit>()
        var acknowledgement:((Boolean)->Unit)?=null
        val results=mutableListOf<PlayPurchaseSnapshot?>()
        val reader=PlayStoreOwnershipRefresh({type,done->replies[type]=done},{purchases,done->
            PlayStoreRestoredPurchases({_,accepted->acknowledgement=accepted},{true}).collect(purchases,done)
        },{true},{error("Unexpected query error")})
        reader.read(results::add)
        replies.getValue(BillingClient.ProductType.SUBS)(ok,listOf(purchase(ack=false)))
        replies.getValue(BillingClient.ProductType.INAPP)(ok,emptyList())
        assertTrue(results.isEmpty())
        acknowledgement!!(true)
        val snapshot=results.single()!!
        assertEquals(setOf("dot_plus_monthly"),snapshot.ownership.subscriptions)
        assertEquals("private-test-token",snapshot.subscriptions.single().purchaseToken())
        assertFalse(snapshot.toString().contains("private-test-token"))
    }
    @Test fun partialFailuresAndSupersededRepliesNeverProvideASnapshot() {
        for(stale in listOf(false,true)) {
            var current=true
            val replies=mutableMapOf<String,(BillingResult,List<Purchase>)->Unit>()
            val results=mutableListOf<PlayPurchaseSnapshot?>()
            PlayStoreOwnershipRefresh({type,done->replies[type]=done},{p,done->done(p.flatMap {it.products}.toSet())},{current},{})
                .read(results::add)
            replies.getValue(BillingClient.ProductType.SUBS)(ok,listOf(purchase()))
            if(stale)current=false
            replies.getValue(BillingClient.ProductType.INAPP)(failure,emptyList())
            if(stale)assertTrue(results.isEmpty()) else assertEquals(listOf<PlayPurchaseSnapshot?>(null),results)
        }
    }
    @Test fun pendingPurchasesBlockNewCheckoutWithoutGrantingRightsRegardlessOfReplyOrder() {
        for(pendingType in listOf(BillingClient.ProductType.INAPP,BillingClient.ProductType.SUBS)){
            val results=mutableListOf<PlayPurchaseSnapshot?>()
            PlayStoreOwnershipRefresh({type,done->done(ok,if(type==pendingType)listOf(purchase(state=4)) else emptyList())},
                {p,done->PlayStoreRestoredPurchases({_,_->error("Pending must not be acknowledged")},{true}).collect(p,done)},{true},{})
                .read(results::add)
            val snapshot=results.single()!!
            assertTrue(snapshot.pending);assertTrue(snapshot.ownership.inApps.isEmpty());assertTrue(snapshot.ownership.subscriptions.isEmpty())
            assertTrue(snapshot.subscriptions.isEmpty())
        }
    }
}
