package com.wikirandomizerp.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.DebugLog

/** One purchasable support option, already localised and priced by Google Play. */
data class DonationProduct(
    val id: String,
    val title: String,
    val description: String,
    val price: String
)

/**
 * Everything the donation UI needs. [error] is a short, user-facing reason the store is not
 * usable right now (for example "Google Play is not available on this device").
 */
data class DonationState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val products: List<DonationProduct> = emptyList(),
    val supporter: Boolean = false,
    val busyProductId: String? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * Thin, well-contained wrapper around Google Play Billing for optional donations.
 *
 * Design notes:
 *  - Donations are one-time, consumable in-app products, so the same amount can be given again.
 *  - [PRODUCT_SUPPORTER] is an optional non-consumable product that permanently unlocks the
 *    "supporter" badge. It is acknowledged, never consumed.
 *  - The manager never crashes when Play Billing is missing (sideloaded APK, emulator, account
 *    without Play Store): it simply reports [DonationState.error] and the UI falls back to the
 *    project's GitHub page.
 *  - Nothing here collects data. Play Billing is only used to complete the purchase.
 */
class DonationBilling(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        DonationState(supporter = prefs.getBoolean(KEY_SUPPORTER, false))
    )
    val state: StateFlow<DonationState> = _state.asStateFlow()

    private var client: BillingClient? = null
    private var connecting = false
    private val detailsById = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach(::handlePurchase)
                if (purchases.isNullOrEmpty()) {
                    _state.update { it.copy(busyProductId = null) }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(busyProductId = null, message = null) }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // A previous purchase was never acknowledged/consumed: recover it instead of
                // leaving the user stuck.
                queryPurchases()
                _state.update { it.copy(busyProductId = null) }
            }

            else -> {
                DebugLog.w("Donation", "purchase failed: ${result.responseCode} ${result.debugMessage}")
                _state.update {
                    it.copy(
                        busyProductId = null,
                        error = result.debugMessage.ifBlank { "Purchase failed (${result.responseCode})" }
                    )
                }
            }
        }
    }

    /** Connects once. Safe to call from every screen that shows the donation UI. */
    fun start() {
        if (client != null || connecting) return
        connecting = true
        _state.update { it.copy(connecting = true, error = null) }
        val billingClient = try {
            BillingClient.newBuilder(appContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .enableAutoServiceReconnection()
                .build()
        } catch (t: Throwable) {
            connecting = false
            DebugLog.w("Donation", "could not create BillingClient: ${t.message}")
            _state.update {
                it.copy(connecting = false, error = "Google Play Billing is not available")
            }
            return
        }
        client = billingClient
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(connected = true, connecting = false, error = null) }
                    queryProducts()
                    queryPurchases()
                } else {
                    DebugLog.w(
                        "Donation",
                        "billing setup failed: ${result.responseCode} ${result.debugMessage}"
                    )
                    _state.update {
                        it.copy(
                            connected = false,
                            connecting = false,
                            error = billingErrorMessage(result)
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.update { it.copy(connected = false, connecting = false) }
            }
        })
    }

    fun refresh() {
        val billingClient = client
        if (billingClient == null) {
            start()
            return
        }
        if (billingClient.isReady) {
            queryProducts()
            queryPurchases()
        } else {
            // A previous connection was lost: recreate the client instead of silently doing
            // nothing, so the Retry button in the dialog actually retries.
            runCatching { billingClient.endConnection() }
            client = null
            start()
        }
    }

    private fun queryProducts() {
        val billingClient = client ?: return
        val products = ALL_PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                DebugLog.w(
                    "Donation",
                    "product query failed: ${result.responseCode} ${result.debugMessage}"
                )
                _state.update { it.copy(error = billingErrorMessage(result)) }
                return@queryProductDetailsAsync
            }
            val list = queryResult.productDetailsList
            detailsById.clear()
            list.forEach { detailsById[it.productId] = it }
            val ordered = ALL_PRODUCT_IDS.mapNotNull { id ->
                detailsById[id]?.let { details ->
                    DonationProduct(
                        id = details.productId,
                        title = details.name,
                        description = details.description,
                        price = details.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                            ?: ""
                    )
                }
            }
            _state.update {
                it.copy(
                    products = ordered,
                    error = if (ordered.isEmpty()) {
                        "No donation products are configured in Play Console yet"
                    } else null
                )
            }
        }
    }

    private fun queryPurchases() {
        val billingClient = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
    }

    /** Starts the Play purchase sheet for [productId]. */
    fun donate(activity: Activity, productId: String) {
        val billingClient = client
        val details = detailsById[productId]
        if (billingClient == null || details == null) {
            _state.update {
                it.copy(error = "This donation option is not available right now")
            }
            return
        }
        _state.update { it.copy(busyProductId = productId, error = null, message = null) }
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(busyProductId = null, error = billingErrorMessage(result))
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            // Pending (for example cash payment): do not grant anything yet.
            return
        }
        val products = purchase.products
        if (products.contains(PRODUCT_SUPPORTER)) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client?.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        DebugLog.w(
                            "Donation",
                            "could not acknowledge supporter purchase: ${result.responseCode}"
                        )
                    }
                }
            }
            markSupporter()
            _state.update { it.copy(busyProductId = null, message = appContext.getString(R.string.donationThanks)) }
            return
        }

        // Consumable donation: consume so the same tier can be bought again later.
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client?.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                DebugLog.w("Donation", "could not consume donation: ${result.responseCode}")
            }
        }
        markSupporter()
        _state.update {
            it.copy(
                busyProductId = null,
                message = appContext.getString(R.string.donationThanks)
            )
        }
    }

    private fun markSupporter() {
        if (!_state.value.supporter) {
            prefs.edit().putBoolean(KEY_SUPPORTER, true).apply()
        }
        _state.update { it.copy(supporter = true) }
    }

    /** Debug-menu helper: pretend a purchase completed, without contacting Play. */
    fun debugSimulatePurchase() {
        markSupporter()
        _state.update {
            it.copy(message = "Debug: purchase simulated", busyProductId = null, error = null)
        }
    }

    /** Debug-menu helper: forget the local supporter flag and all debug messages. */
    fun debugReset() {
        prefs.edit().remove(KEY_SUPPORTER).apply()
        _state.update {
            it.copy(supporter = false, message = null, error = null, busyProductId = null)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun billingErrorMessage(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play Billing is not available on this device"
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            "Google Play is not reachable right now"
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This donation option is not available in your country"
        BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
            "The donation products are not configured correctly in Play Console"
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            "Network error while contacting Google Play"
        else -> result.debugMessage.ifBlank { "Google Play error (${result.responseCode})" }
    }

    companion object {
        /** Optional permanent supporter product (non-consumable). */
        const val PRODUCT_SUPPORTER = "supporter"

        /** Consumable one-time donation tiers. Create these in Play Console. */
        val DONATION_PRODUCT_IDS = listOf("donation_small", "donation_medium", "donation_large")
        private val ALL_PRODUCT_IDS = DONATION_PRODUCT_IDS + PRODUCT_SUPPORTER

        private const val PREFS = "donations"
        private const val KEY_SUPPORTER = "supporter"
    }
}
