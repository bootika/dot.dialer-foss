package dev.goodwy.rphone.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams

internal fun queryPlayProductDetails(
    client: BillingClient,
    productIds: List<String>,
    productType: String,
    onError: (String) -> Unit,
    onResult: (List<ProductDetails>) -> Unit,
) {
    val products = productIds.map { productId ->
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(productType)
            .build()
    }
    val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
    try {
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(details.productDetailsList)
            } else {
                onError(billingError("Unable to load products", result))
                onResult(emptyList())
            }
        }
    } catch (error: RuntimeException) {
        onError(error.message ?: "Unable to load products")
        onResult(emptyList())
    }
}

internal fun billingError(prefix: String, result: BillingResult): String {
    val detail = result.debugMessage.takeIf(String::isNotBlank)
        ?: result.responseCode.toString()
    return "$prefix: $detail"
}
