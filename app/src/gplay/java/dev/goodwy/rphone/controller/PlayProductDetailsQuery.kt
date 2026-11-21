package dev.goodwy.rphone.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams

internal class PlayProductDetailsQuery(
    private val client: BillingClient,
    private val error: (BillingResult?) -> Unit,
) {
    fun read(ids: List<String>, type: String, complete: (List<ProductDetails>) -> Unit) {
        val products = ids.map { id -> QueryProductDetailsParams.Product.newBuilder()
            .setProductId(id).setProductType(type).build() }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        try {
            client.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) complete(details.productDetailsList)
                else { error(result); complete(emptyList()) }
            }
        } catch (_: RuntimeException) { error(null); complete(emptyList()) }
    }
}
