package dev.goodwy.rphone.controller

import com.android.billingclient.api.*
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.license.PlanProducts
import io.github.bootika.dotdialer.core.license.AppPlan

internal data class PlayPurchaseSnapshot(
    val ownership:PlayStoreOwnership,val subscriptions:List<ActivePlaySubscription>,val pending:Boolean,
    val receipts:List<PlayPurchaseReceipt> = emptyList(),
)
/** One paired authoritative read. An incomplete/failed/superseded query never supplies checkout tokens. */
internal class PlayStoreOwnershipRefresh(
    private val query:(String,(BillingResult,List<Purchase>)->Unit)->Unit,
    private val restore:(List<Purchase>,(Set<String>?)->Unit)->Unit,
    private val isCurrent:()->Boolean,
    private val error:(BillingResult)->Unit,
) {
    constructor(client:BillingClient,isCurrent:()->Boolean,error:(BillingResult)->Unit,serverVerification:Boolean=false):this({type,done->
        val params=QueryPurchasesParams.newBuilder().setProductType(type).build()
        try {client.queryPurchasesAsync(params){result,purchases->done(result,purchases)}}
        catch(failure:RuntimeException){done(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ERROR).build(),emptyList())}
    },{purchases,done->
        if(serverVerification)done(purchases.filter {it.purchaseState==Purchase.PurchaseState.PURCHASED}.flatMap {it.products}.toSet())
        else PlayStoreRestoredPurchases(client,isCurrent).collect(purchases,done)
    },isCurrent,error)

    fun read(complete:(PlayPurchaseSnapshot?)->Unit) {
        var subscriptions=emptyList<ActivePlaySubscription>()
        var pending=false
        val receipts=mutableListOf<PlayPurchaseReceipt>()
        val lock=Any()
        val claimed=mutableSetOf<String>()
        val paired=PlayStoreOwnershipQuery(isCurrent){owned->
            complete(synchronized(lock){owned?.let {PlayPurchaseSnapshot(it,subscriptions,pending,receipts.toList())}})
        }
        for(type in listOf(BillingClient.ProductType.INAPP,BillingClient.ProductType.SUBS)) {
            query(type){result,purchases->
                if(!isCurrent())return@query
                if(!synchronized(lock){claimed.add(type)})return@query
                if(result.responseCode!=BillingClient.BillingResponseCode.OK){error(result);paired.accept(type==BillingClient.ProductType.SUBS,null);return@query}
                restore(purchases){owned->
                    if(!isCurrent())return@restore
                    if(owned!=null)synchronized(lock){
                        purchases.filter { it.purchaseState==Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                            purchase.products.filter { it in owned }.forEach { product ->
                                receipts.add(PlayPurchaseReceipt(product,purchase.purchaseToken))
                            }
                        }
                        pending=pending||purchases.any {it.purchaseState==Purchase.PurchaseState.PENDING}
                        if(type==BillingClient.ProductType.SUBS)subscriptions=purchases.filter {
                            it.purchaseState==Purchase.PurchaseState.PURCHASED
                        }.map {purchase->
                            val products=purchase.products.toSet()
                            val plan=when {
                                products.any {it==BuildConfig.SUBSCRIPTION_ID_X1 || it==BuildConfig.SUBSCRIPTION_YEAR_ID_X1}->AppPlan.PREMIUM
                                products.size==1 && PlanProducts.all.any {it.subscription && it.id in products}->PlanProducts.purchased(products)
                                else->null
                            }
                            ActivePlaySubscription(products,purchase.purchaseToken,plan)
                        }
                    }
                    paired.accept(type==BillingClient.ProductType.SUBS,owned)
                }
            }
        }
    }
}
