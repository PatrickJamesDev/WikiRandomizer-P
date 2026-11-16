package com.wikirandomizerp.app.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point for the app's premium entitlement.
 *
 * Premium is deliberately tied to support: any completed donation, the supporter product or the
 * sync subscription unlocks every premium feature. [DonationBilling] still owns the Play Billing
 * client and receipt handling; this class only adds the premium checks that the UI and background
 * work use, so there is exactly one source of truth for "is this user premium?".
 */
class BillingManager(private val donations: DonationBilling) {

    /** Live Play Billing state (products, connection state, purchases). */
    val state: StateFlow<DonationState> = donations.state

    /** True when the user has donated, bought supporter, has an active subscription, or unlocked
     *  premium through the developer options switch. */
    fun isPremium(supporterMode: Boolean): Boolean {
        val current = donations.state.value
        return supporterMode || current.supporter || current.subscription
    }

    fun start() = donations.start()

    fun refresh() = donations.refresh()

    fun donate(activity: Activity, productId: String) = donations.donate(activity, productId)

    fun clearMessage() = donations.clearMessage()

    companion object {
        /**
         * Premium check for code that has no access to the live flow (for example a WorkManager
         * worker). Reads the persisted entitlement written by [DonationBilling].
         */
        fun isPremiumActive(context: Context, supporterMode: Boolean): Boolean =
            supporterMode ||
                DonationBilling.isSupporterActive(context) ||
                DonationBilling.isSubscriptionActive(context)
    }
}
