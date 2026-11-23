package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.license.GiftLicenseManager
import dev.goodwy.rphone.controller.license.PlanAccessManager
import dev.goodwy.rphone.controller.license.PlanConfirmation
import dev.goodwy.rphone.controller.license.PlanConfirmationSource
import dev.goodwy.rphone.controller.license.PlanProducts

class PlayStoreViewModel internal constructor(
    application: Application,
    private val preferenceManager: PreferenceManager,
    override val giftLicenses: GiftLicenseManager? = null,
    private val ownershipCache: PlayStoreOwnershipCache = PlayStoreOwnershipCache(application),
) : AndroidViewModel(application), PurchaseHelper {

    private var billingClient: BillingClient? = null
    private var connectionInProgress = false
    private val cachedOwnership = ownershipCache.restore()
    private var ownershipKnown = cachedOwnership != null
    private val ownershipQueryGeneration = java.util.concurrent.atomic.AtomicLong()
    private val readyActions = mutableListOf<() -> Unit>()

    private val _iapSkuDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    private val _subSkuDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    private val _iapSkuDetailsInitialized = MutableStateFlow(false)
    override val iapSkuDetailsInitialized: StateFlow<Boolean> =
        _iapSkuDetailsInitialized.asStateFlow()
    private val _subSkuDetailsInitialized = MutableStateFlow(false)
    override val subSkuDetailsInitialized: StateFlow<Boolean> =
        _subSkuDetailsInitialized.asStateFlow()

    private val iapPurchased = MutableStateFlow(cachedOwnership?.inApps.orEmpty())
    private val subPurchased = MutableStateFlow(cachedOwnership?.subscriptions.orEmpty())
    private val _purchaseSuccess = MutableStateFlow(false)
    override val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _isBillingReady = MutableStateFlow(false)
    override val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()
    private val _isPro = MutableStateFlow(cachedOwnership?.isPro ?: false)
    private val mutableStorePlan = MutableStateFlow(cachedOwnership?.plan ?: io.github.bootika.dotdialer.core.license.AppPlan.FREE)
    override val storePlan = mutableStorePlan.asStateFlow()
    override fun productAvailable(product: String, subscription: Boolean): Boolean = if (subscription)
        _subSkuDetails.value.any { it.productId == product && it.subscriptionOfferDetails?.isNotEmpty() == true }
        else _iapSkuDetails.value.any { it.productId == product && it.oneTimePurchaseOfferDetails != null }
    override fun loadProducts(productIds: List<String>) {
        val catalog = dev.goodwy.rphone.controller.license.PlanProducts.all
        retrieveDonation(productIds.filter { id -> catalog.any { it.id == id && !it.subscription } },
            productIds.filter { id -> catalog.any { it.id == id && it.subscription } })
    }
    override val isPro: StateFlow<Boolean> = giftLicenses?.let { gifts ->
        combine(_isPro, gifts.isPro) { store, gift -> store || gift }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    } ?: _isPro.asStateFlow()
    private val _proCheckDone = MutableStateFlow(false)
    override val proCheckDone: StateFlow<Boolean> = _proCheckDone.asStateFlow()

    private var cachedIaps: List<String> = emptyList()
    private var cachedSubs: List<String> = emptyList()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _isLoading.value = false
                } else {
                    purchases.forEach(::handlePurchaseUpdate)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _isLoading.value = false
            else -> fail("Purchase failed", result)
        }
    }

    override fun initBilling() {
        ensureBillingClient()
    }

    fun retrieveDonation(iaps: List<String>, subs: List<String>) {
        cachedIaps = iaps
        cachedSubs = subs
        _isLoading.value = true
        _errorMessage.value = null
        _purchaseSuccess.value = false
        _iapSkuDetailsInitialized.value = false
        _subSkuDetailsInitialized.value = false
        runWhenBillingReady { loadProductDetails(iaps, subs) }
    }

    private fun ensureBillingClient(): BillingClient {
        billingClient?.let { return it }
        return BillingClient.newBuilder(getApplication())
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
            .also { billingClient = it }
    }

    private fun runWhenBillingReady(action: () -> Unit) {
        val client = ensureBillingClient()
        if (client.isReady) {
            _isBillingReady.value = true
            action()
            return
        }

        readyActions += action
        if (connectionInProgress) return
        connectionInProgress = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                connectionInProgress = false
                _isBillingReady.value = false
                failPendingActions("Billing service disconnected")
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                connectionInProgress = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _isBillingReady.value = false
                    failPendingActions(billingError("Billing setup failed", result))
                    return
                }

                _isBillingReady.value = true
                val actions = readyActions.toList()
                readyActions.clear()
                actions.forEach { it() }
            }
        })
    }

    private fun failPendingActions(message: String) {
        readyActions.clear()
        _errorMessage.value = message
        _isLoading.value = false
        _proCheckDone.value = true
    }

    private fun loadProductDetails(iaps: List<String>, subs: List<String>) {
        val client = billingClient ?: return failPendingActions("Billing is not initialized")
        var pendingQueries = listOf(iaps, subs).count(List<String>::isNotEmpty)

        fun finishQuery() {
            pendingQueries--
            if (pendingQueries <= 0) {
                _isLoading.value = false
                queryPurchases()
            }
        }

        if (iaps.isEmpty()) {
            _iapSkuDetails.value = emptyList()
            _iapSkuDetailsInitialized.value = true
        } else {
            queryProductDetails(
                client = client,
                productIds = iaps,
                productType = BillingClient.ProductType.INAPP,
                onResult = {
                    _iapSkuDetails.value = it
                    _iapSkuDetailsInitialized.value = true
                    finishQuery()
                },
            )
        }

        if (subs.isEmpty()) {
            _subSkuDetails.value = emptyList()
            _subSkuDetailsInitialized.value = true
        } else {
            queryProductDetails(
                client = client,
                productIds = subs,
                productType = BillingClient.ProductType.SUBS,
                onResult = {
                    _subSkuDetails.value = it
                    _subSkuDetailsInitialized.value = true
                    finishQuery()
                },
            )
        }

        if (pendingQueries == 0) {
            _isLoading.value = false
            queryPurchases()
        }
    }

    private fun queryProductDetails(
        client: BillingClient,
        productIds: List<String>,
        productType: String,
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
                    _errorMessage.value = billingError("Unable to load products", result)
                    onResult(emptyList())
                }
            }
        } catch (error: RuntimeException) {
            _errorMessage.value = error.message ?: "Unable to load products"
            onResult(emptyList())
        }
    }

    private fun handlePurchaseUpdate(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val previouslyOwned = iapPurchased.value + subPurchased.value
                if (purchase.isAcknowledged) {
                    completePurchase(purchase.products, previouslyOwned)
                    return
                }
                val client = billingClient ?: return failPendingActions("Billing is not initialized")
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(params) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        completePurchase(purchase.products, previouslyOwned)
                    } else {
                        fail("Unable to confirm purchase", result)
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> _isLoading.value = false
            else -> _isLoading.value = false
        }
    }

    private fun completePurchase(products: List<String>, previouslyOwned: Set<String>) {
        _purchaseSuccess.value = true
        queryPurchases { verified ->
            if (verified) {
                val owned = iapPurchased.value + subPurchased.value
                val plan = PlanProducts.all.filter { it.id in products && it.id in owned && it.id !in previouslyOwned }
                    .maxByOrNull { it.tier }
                if (plan != null) {
                    val source = if (plan.subscription) PlanConfirmationSource.PLAY_SUBSCRIPTION
                        else PlanConfirmationSource.PLAY_LIFETIME
                    PlanAccessManager.get(getApplication<Application>()).offerConfirmation(
                        PlanConfirmation(plan.tier, source, permanent = !plan.subscription),
                    )
                }
            }
        }
    }

    private fun queryPurchases(onComplete: (Boolean) -> Unit = {}) {
        val client = billingClient
        if (client == null || !client.isReady) {
            failPendingActions("Billing service is not ready")
            onComplete(false)
            return
        }

        val generation = ownershipQueryGeneration.incrementAndGet()
        val query = PlayStoreOwnershipQuery(
            isCurrent = { ownershipQueryGeneration.get() == generation },
            onComplete = { owned ->
                if (owned != null) {
                    iapPurchased.value = owned.inApps
                    subPurchased.value = owned.subscriptions
                    updateProStatus()
                }
                _proCheckDone.value = true
                _isLoading.value = false
                onComplete(owned != null)
            },
        )
        fun complete(type: String, result: BillingResult, purchases: List<Purchase>) {
            if (ownershipQueryGeneration.get() != generation) return
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _errorMessage.value = billingError("Unable to restore purchases", result)
                query.accept(type == BillingClient.ProductType.SUBS, null)
                return
            }
            PlayStoreRestoredPurchases(client) { ownershipQueryGeneration.get() == generation }.collect(purchases) { owned ->
                if (owned == null) _errorMessage.value = "Unable to confirm restored purchase"
                query.accept(type == BillingClient.ProductType.SUBS, owned)
            }
        }
        queryPurchases(client, BillingClient.ProductType.INAPP, ::complete)
        queryPurchases(client, BillingClient.ProductType.SUBS, ::complete)
    }

    private fun queryPurchases(
        client: BillingClient,
        productType: String,
        onResult: (String, BillingResult, List<Purchase>) -> Unit,
    ) {
        val params = QueryPurchasesParams.newBuilder().setProductType(productType).build()
        try {
            client.queryPurchasesAsync(params) { result, purchases ->
                onResult(productType, result, purchases)
            }
        } catch (error: RuntimeException) {
            onResult(
                productType,
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                    .setDebugMessage(error.message.orEmpty())
                    .build(),
                emptyList(),
            )
        }
    }

    override fun refreshAllData() {
        _errorMessage.value = null
        if (cachedIaps.isEmpty() && cachedSubs.isEmpty()) {
            _errorMessage.value = "No products to load"
            return
        }
        _isLoading.value = true
        _iapSkuDetailsInitialized.value = false
        _subSkuDetailsInitialized.value = false
        runWhenBillingReady { loadProductDetails(cachedIaps, cachedSubs) }
    }

    override fun purchaseDonation(product: String, activity: Activity) {
        val details = _iapSkuDetails.value.firstOrNull { it.productId == product }
        if (details == null) {
            _errorMessage.value = "Product not found"
            return
        }
        launchBillingFlow(
            activity,
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build(),
        )
    }

    override fun purchaseSubscription(product: String, activity: Activity, planId: String?) {
        val details = _subSkuDetails.value.firstOrNull { it.productId == product }
        if (details == null) {
            _errorMessage.value = "Subscription not found"
            return
        }
        val offer = details.planOffer(planId)
        if (offer == null) {
            _errorMessage.value = "Plan not found"
            return
        }
        launchBillingFlow(
            activity,
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build(),
        )
    }

    private fun launchBillingFlow(
        activity: Activity,
        product: BillingFlowParams.ProductDetailsParams,
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            _errorMessage.value = "Billing service is not ready"
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        val result = try {
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(product))
                    .build(),
            )
        } catch (error: RuntimeException) {
            _errorMessage.value = error.message ?: "Unable to start purchase"
            _isLoading.value = false
            return
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            fail("Unable to start purchase", result)
        }
    }

    override fun getPriceDonation(product: String): String =
        _iapSkuDetails.value.firstOrNull { it.productId == product }
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
            ?: noConnectionLabel()

    override fun getPriceSubscription(product: String, planId: String?): String {
        val details = _subSkuDetails.value.firstOrNull { it.productId == product }
            ?: return noConnectionLabel()
        val offer = details.planOffer(planId)
        return offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            ?: noConnectionLabel()
    }

    override fun isIapPurchased(product: String): Boolean = product in iapPurchased.value

    override fun isSubPurchased(product: String): Boolean = product in subPurchased.value

    override fun restorePurchases() {
        _isLoading.value = true
        _errorMessage.value = null
        runWhenBillingReady { queryPurchases() }
    }

    override fun checkProStatus() {
        _isPro.value = if (ownershipKnown) {
            PlayStoreOwnership(iapPurchased.value, subPurchased.value).isPro
        } else {
            preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false) ||
                preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        }
        _proCheckDone.value = false
        runWhenBillingReady { queryPurchases() }
    }

    private fun updateProStatus() {
        ownershipKnown = true
        val owned = PlayStoreOwnership(iapPurchased.value, subPurchased.value)
        mutableStorePlan.value = owned.plan
        val iapPro = owned.legacyInApp
        val subscriptionPro = owned.legacySubscription
        val pro = owned.isPro
        if (_isPro.value && !pro) clearProPrefsIfNeeded()
        _isPro.value = pro
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, iapPro)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, subscriptionPro)
        try {
            ownershipCache.save(owned)
        } catch (error: Exception) {
            android.util.Log.w("PlayOwnership", "save: ${error.javaClass.simpleName}")
        }
    }

    override fun clearProPrefsIfNeeded() {
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        // An empty store result must not reset personalization belonging to a gift license.
        if (giftLicenses?.let { !it.initialized || it.isPro.value } == true) return
        // Appearance preferences are retained; effective plan rights control their presentation.
    }

    override fun setProStatusImmediate(isPro: Boolean) {
        _isPro.value = isPro
        _proCheckDone.value = true
    }

    override fun clearErrors() {
        _errorMessage.value = null
    }

    override fun clearPurchaseSuccess() {
        _purchaseSuccess.value = false
    }

    private fun fail(prefix: String, result: BillingResult) {
        _errorMessage.value = billingError(prefix, result)
        _isLoading.value = false
    }

    private fun billingError(prefix: String, result: BillingResult): String {
        val detail = result.debugMessage.takeIf(String::isNotBlank)
            ?: result.responseCode.toString()
        return "$prefix: $detail"
    }

    private fun noConnectionLabel(): String =
        getApplication<Application>().getString(R.string.no_connection)

    override fun onCleared() {
        readyActions.clear()
        billingClient?.endConnection()
        billingClient = null
        super.onCleared()
    }
}
