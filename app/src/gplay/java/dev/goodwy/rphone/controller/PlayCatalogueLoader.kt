package dev.goodwy.rphone.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails

internal class PlayCatalogueLoader(private val read: (List<String>,String,(List<ProductDetails>)->Unit)->Unit) {
    fun load(iaps: List<String>,subs: List<String>,inApps: (List<ProductDetails>)->Unit,
        subscriptions: (List<ProductDetails>)->Unit,complete: ()->Unit) {
        val replies=mutableSetOf<String>()
        fun accept(type:String,values:List<ProductDetails>) {
            synchronized(replies) {
                if(!replies.add(type))return
                if(type==BillingClient.ProductType.INAPP)inApps(values) else subscriptions(values)
                if(replies.size==2)complete()
            }
        }
        listOf(BillingClient.ProductType.INAPP to iaps,BillingClient.ProductType.SUBS to subs).forEach { (type,ids) ->
            if(ids.isEmpty())accept(type,emptyList()) else read(ids,type){accept(type,it)}
        }
    }
}
