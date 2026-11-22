package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayStoreViewModel internal constructor(
    application: Application,
    private val preferenceManager: PreferenceManager,
    override val giftLicenses: GiftLicenseManager? = null,
    private val ownershipCache: PlayStoreOwnershipCache = PlayStoreOwnershipCache(application),
    private val purchaseAuthority: PlayPurchaseAuthority? = null,
    private val serverAuthorityRequired: Boolean = false,
) : AndroidViewModel(application), PurchaseHelper {

    private var billingClient: BillingClient? = null
    private var connectionInProgress = false
    private val cachedOwnership = purchaseAuthority?.ownership() ?: ownershipCache.restore()
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
    override val storePlan: StateFlow<io.github.bootika.dotdialer.core.license.AppPlan> = PlayReadThroughFlow(mutableStorePlan.asStateFlow()) { publishVerifiedAuthority();mutableStorePlan.value }
    private val mutableSubscriptionPlan = MutableStateFlow(cachedOwnership?.subscriptionPlan ?: io.github.bootika.dotdialer.core.license.AppPlan.FREE)
    override val subscriptionPlan: StateFlow<io.github.bootika.dotdialer.core.license.AppPlan> = PlayReadThroughFlow(mutableSubscriptionPlan.asStateFlow()) { publishVerifiedAuthority();mutableSubscriptionPlan.value }
    override val ownedSubscriptions: StateFlow<Set<String>> = PlayReadThroughFlow(subPurchased.asStateFlow()) { publishVerifiedAuthority();subPurchased.value }
    override val supportsSubscriptionManagement = true
    private val foregroundRefresh by lazy {
        ForegroundOwnershipRefresh(viewModelScope, {
            val network = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
            network?.getNetworkCapabilities(network.activeNetwork)?.let {
                it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }, { _isLoading.value }, android.os.SystemClock::elapsedRealtime) {
            runWhenBillingReady { queryPurchases() }
        }
    }
    override fun onAppForeground() = foregroundRefresh.onForeground()
    override fun manageSubscription(activity: Activity) = PlaySubscriptionManagement.open(activity, subPurchased.value) {
        _errorMessage.value = "Unable to open subscription management"
    }
    override fun productAvailable(product: String, subscription: Boolean): Boolean = if (subscription)
        _subSkuDetails.value.any { it.productId == product && it.subscriptionOfferDetails?.isNotEmpty() == true }
        else _iapSkuDetails.value.any { it.productId == product && it.oneTimePurchaseOfferDetails != null }
    override fun loadProducts(productIds: List<String>) {
        val catalog = dev.goodwy.rphone.controller.license.PlanProducts.all
        retrieveDonation(productIds.filter { id -> catalog.any { it.id == id && !it.subscription } },
            productIds.filter { id -> catalog.any { it.id == id && it.subscription } })
    }
    private val proFlow: StateFlow<Boolean> = giftLicenses?.let { gifts ->
        combine(_isPro, gifts.isPro) { store, gift -> store || gift }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    } ?: _isPro.asStateFlow()
    override val isPro: StateFlow<Boolean> = PlayReadThroughFlow(proFlow) { publishVerifiedAuthority();_isPro.value || giftLicenses?.isPro?.value==true }
    private val _proCheckDone = MutableStateFlow(false)
    override val proCheckDone: StateFlow<Boolean> = _proCheckDone.asStateFlow()
    private var cachedIaps: List<String> = emptyList()
    private var cachedSubs: List<String> = emptyList()

    init { if (serverAuthorityRequired) PlayAuthorityLifecycle.observe(viewModelScope, purchaseAuthority,
        ownershipQueryGeneration::get, {_isLoading.value}, ::updateProStatus) }
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
        PlayCatalogueLoader { ids, type, done -> queryProductDetails(client,ids,type,done) }.load(iaps,subs,
            { _iapSkuDetails.value=it;_iapSkuDetailsInitialized.value=true },
            { _subSkuDetails.value=it;_subSkuDetailsInitialized.value=true },
            { _isLoading.value=false;queryPurchases() })
    }

    private fun queryProductDetails(
        client: BillingClient,
        productIds: List<String>,
        productType: String,
        onResult: (List<ProductDetails>) -> Unit,
    ) {
        PlayProductDetailsQuery(client) { result ->
            _errorMessage.value = if(result == null) "Unable to load products" else billingError("Unable to load products", result)
        }.read(productIds, productType, onResult)
    }

    private fun handlePurchaseUpdate(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (serverAuthorityRequired) {
                    queryPurchases(restore = false) { verified -> _purchaseSuccess.value = verified &&
                        purchase.products.any { it in iapPurchased.value || it in subPurchased.value } }
                    return
                }
                PlayLegacyPurchaseUpdates.accept(billingClient, purchase, ::completePurchase) {
                    failPendingActions("Unable to confirm purchase")
                }
            }
            Purchase.PurchaseState.PENDING -> _isLoading.value = false
            else -> _isLoading.value = false
        }
    }

    private fun completePurchase() {
        _purchaseSuccess.value = true
        queryPurchases()
    }

    private var ownershipSnapshot: PlayPurchaseSnapshot? = null
    private var ownershipCompletion: ((Boolean) -> Unit)? = null
    private fun queryPurchases(restore: Boolean = true, onComplete: (Boolean) -> Unit = {}) {
        val superseded = ownershipCompletion
        ownershipCompletion = null
        ownershipQueryGeneration.incrementAndGet()
        superseded?.invoke(false)
        ownershipSnapshot = null
        val client = billingClient
        if (client == null || !client.isReady) {
            failPendingActions("Billing service is not ready")
            onComplete(false)
            return
        }
        val generation = ownershipQueryGeneration.incrementAndGet()
        ownershipCompletion = onComplete
        PlayStoreOwnershipRefresh(client, { ownershipQueryGeneration.get() == generation }, { result ->
            _errorMessage.value = billingError("Unable to restore purchases", result)
        }, serverAuthorityRequired).read { snapshot ->
            if (ownershipQueryGeneration.get() != generation) return@read
            if(serverAuthorityRequired && snapshot?.pending==true) _errorMessage.value=getApplication<Application>().getString(R.string.plan_purchase_pending)
            viewModelScope.launch {
                val accepted = PlayServerSnapshot.reconcile(snapshot, serverAuthorityRequired, purchaseAuthority, restore) {
                    ownershipQueryGeneration.get() == generation
                }
                if (ownershipQueryGeneration.get() != generation) return@launch
                if (accepted != null) {
                    ownershipSnapshot = accepted
                    iapPurchased.value = accepted.ownership.inApps
                    subPurchased.value = accepted.ownership.subscriptions
                    updateProStatus()
                } else if (_errorMessage.value == null) _errorMessage.value = "Unable to confirm restored purchase"
                _proCheckDone.value = true
                _isLoading.value = false
                val completion = ownershipCompletion
                ownershipCompletion = null
                completion?.invoke(accepted != null)
            }
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
            activity, product,
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
            activity, product,
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build(),
        )
    }
    private val checkoutRequest = PlayCheckoutRequest()
    override fun onPurchaseScreenClosed() {
        if (checkoutRequest.cancel()) _isLoading.value = false
    }
    private fun launchBillingFlow(activity: Activity, productId: String, product: BillingFlowParams.ProductDetailsParams) {
        if (_isLoading.value) return
        val client = billingClient
        if (client == null || !client.isReady) {
            _errorMessage.value = "Billing service is not ready"
            return
        }
        val target = dev.goodwy.rphone.controller.license.PlanProducts.all.find { it.id == productId }
            ?: return
        val request = checkoutRequest.begin()
        _isLoading.value = true
        _errorMessage.value = null
        // Never fall back to a token from another account/query when preparing a new charge.
        queryPurchases { fresh ->
            if (!checkoutRequest.isCurrent(request)) return@queryPurchases
            val snapshot = ownershipSnapshot
            if (!fresh || snapshot == null || activity.isFinishing || activity.isDestroyed) {
                checkoutRequest.cancel(); _isLoading.value = false
                return@queryPurchases
            }
            viewModelScope.launch {
                val generation = ownershipQueryGeneration.get()
                _isLoading.value = true
                PlayBillingFlowLauncher(
                    principal = { if(serverAuthorityRequired) withContext(Dispatchers.IO) {
                        checkNotNull(purchaseAuthority).prepareCheckout()
                    } else null },
                    current = { checkoutRequest.isCurrent(request) && ownershipQueryGeneration.get() == generation && !activity.isFinishing && !activity.isDestroyed },
                    decide = {
                        val external = dev.goodwy.rphone.controller.license.PlanAccessManager.get(activity).state.value
                        PlayPlanCheckout.decide(target, snapshot.ownership.inApps, snapshot.subscriptions,
                            snapshot.pending, external.entitlements.paid, giftLicenses?.isPro?.value == true,
                            external.ready && giftLicenses?.initialized != false)
                    },
                    finish = { checkoutRequest.finish(request); _isLoading.value = true },
                    launch = { params -> client.launchBillingFlow(activity, params) },
                    blocked = { decision ->
                        checkoutRequest.cancel(); _isLoading.value = false
                        _errorMessage.value = when(decision) {
                            PlayCheckoutDecision.AlreadyOwned -> null
                            PlayCheckoutDecision.ManageSubscription -> getApplication<Application>().getString(
                                if(target.subscription) R.string.plan_subscription_check_required else R.string.plan_manage_before_purchase)
                            PlayCheckoutDecision.Pending -> getApplication<Application>().getString(R.string.plan_purchase_pending)
                            else -> "Unable to confirm independent access"
                        }
                    },
                    failure = { checkoutRequest.cancel(); _isLoading.value = false; _errorMessage.value = "Unable to start purchase" },
                ).start(product)
            }
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

    override fun isIapPurchased(product: String): Boolean { publishVerifiedAuthority();return product in iapPurchased.value }

    override fun isSubPurchased(product: String): Boolean { publishVerifiedAuthority();return product in subPurchased.value }
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
        val owned = purchaseAuthority?.ownership() ?: PlayStoreOwnership(iapPurchased.value, subPurchased.value)
        iapPurchased.value = owned.inApps
        subPurchased.value = owned.subscriptions
        mutableStorePlan.value = owned.plan
        mutableSubscriptionPlan.value = owned.subscriptionPlan
        val iapPro = owned.legacyInApp
        val subscriptionPro = owned.legacySubscription
        val pro = owned.isPro
        if (_isPro.value && !pro) clearProPrefsIfNeeded()
        _isPro.value = pro
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, iapPro)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, subscriptionPro)
        try {
            val authority = purchaseAuthority
            if(authority?.ownership() == null) ownershipCache.save(owned)
            else PlayMigrationCache.after(ownershipCache.restore(), owned, authority.durable)?.let(ownershipCache::save)
        } catch (error: Exception) {
            android.util.Log.w("PlayOwnership", "save: ${error.javaClass.simpleName}")
        }
    }

    private fun publishVerifiedAuthority() {
        val owned=purchaseAuthority?.ownership() ?: return
        if(owned.inApps!=iapPurchased.value || owned.subscriptions!=subPurchased.value)updateProStatus()
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
        ownershipQueryGeneration.incrementAndGet()
        checkoutRequest.cancel()
        ownershipSnapshot = null
        ownershipCompletion = null
        billingClient?.endConnection()
        billingClient = null
        super.onCleared()
    }
}
