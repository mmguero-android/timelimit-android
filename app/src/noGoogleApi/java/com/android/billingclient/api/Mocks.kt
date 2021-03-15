/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.android.billingclient.api

import android.app.Activity
import android.app.Application
import io.timelimit.android.async.Threads

object BillingClient {
    fun newBuilder(application: Application) = Builder

    fun startConnection(listener: BillingClientStateListener) {
        Threads.mainThreadHandler.post { listener.onBillingSetupFinished(BillingResult) }
    }

    fun endConnection() {}

    fun querySkuDetails(param: SkuDetailsParams) = QuerySkuDetailsResult.instance
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams) = BillingResult
    fun acknowledgePurchase(params: AcknowledgePurchaseParams) = BillingResult
    fun consumePurchase(params: ConsumeParams) = BillingResult
    fun queryPurchases(type: String) = QueryPurchasesResult

    object BillingResponseCode {
        const val OK = 0
        const val ERR = 1
    }

    object SkuType {
        const val INAPP = ""
    }

    object Builder {
        fun enablePendingPurchases() = this
        fun setListener(listener: PurchasesUpdatedListener) = this
        fun build() = BillingClient
    }
}

object BillingResult {
    const val responseCode = BillingClient.BillingResponseCode.ERR
    const val debugMessage = "only mock linked"
}

object SkuDetails {
    const val sku = ""
    const val price = ""
    const val description = ""
}

object SkuDetailsParams {
    fun newBuilder() = Builder

    object Builder {
        fun setSkusList(list: List<String>) = this
        fun setType(type: String) = this
        fun build() = SkuDetailsParams
    }
}

object Purchase {
    const val purchaseState = PurchaseState.PURCHASED
    const val isAcknowledged = true
    const val sku = ""
    const val purchaseToken = ""
    const val originalJson = ""
    const val signature = ""

    object PurchaseState {
        const val PURCHASED = 0
    }
}

object AcknowledgePurchaseParams {
    fun newBuilder() = Builder

    object Builder {
        fun setPurchaseToken(token: String) = this
        fun build() = AcknowledgePurchaseParams
    }
}

object ConsumeParams {
    fun newBuilder() = Builder

    object Builder {
        fun setPurchaseToken(token: String) = this
        fun build() = ConsumeParams
    }
}

object BillingFlowParams {
    fun newBuilder() = Builder

    object Builder {
        fun setSkuDetails(details: SkuDetails) = this
        fun build() = BillingFlowParams
    }
}

object QueryPurchasesResult {
    val billingResult = BillingResult
    val purchasesList: List<Purchase>? = emptyList()
}

data class QuerySkuDetailsResult(val billingResult: BillingResult, val details: List<SkuDetails>?) {
    companion object {
        val instance = QuerySkuDetailsResult(BillingResult, emptyList())
    }
}

interface BillingClientStateListener {
    fun onBillingSetupFinished(billingResult: BillingResult)
    fun onBillingServiceDisconnected()
}

interface PurchasesUpdatedListener {
    fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?)
}