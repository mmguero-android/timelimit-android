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
package io.timelimit.android.ui.payment

import android.app.Activity
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.extensions.*
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.livedata.setTemporarily
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ActivityPurchaseModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ActivityPurchaseModel"
    }

    private val logic = DefaultAppLogic.with(application)
    private val clientMutex = Mutex()
    private val processMutex = Mutex()
    private var _billingClient: BillingClient? = null

    private val isWorkingInternal = MutableLiveData<Boolean>().apply { value = false }
    private val hadErrorInternal = MutableLiveData<Boolean>().apply { value = false }
    private val processPurchaseSuccessInternal = MutableLiveData<Boolean>().apply { value = false }

    val status = mergeLiveData(isWorkingInternal, hadErrorInternal, processPurchaseSuccessInternal).map {
        (working, error, success) ->

        if (success != null && success) {
            ActivityPurchaseModelStatus.Done
        } else if (working != null && working) {
            ActivityPurchaseModelStatus.Working
        } else if (error != null && error) {
            ActivityPurchaseModelStatus.Error
        } else {
            ActivityPurchaseModelStatus.Idle
        }
    }

    fun resetProcessPurchaseSuccess() {
        processPurchaseSuccessInternal.value = false
    }

    private suspend fun <R> initAndUseClient(block: suspend (client: BillingClient) -> R): R {
        clientMutex.withLock {
            if (_billingClient == null) {
                _billingClient = BillingClient.newBuilder(getApplication())
                        .enablePendingPurchases()
                        .setListener(purchaseUpdatedListener)
                        .build()
            }

            val initBillingClient = _billingClient!!

            suspendCoroutine<Unit?> { continuation ->
                initBillingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        try {
                            billingResult.assertSuccess()

                            continuation.resume(null)
                        } catch (ex: BillingClientException) {
                            _billingClient = null

                            if (BuildConfig.DEBUG) {
                                Log.w(LOG_TAG, "error during connecting", ex)
                            }

                            continuation.resumeWithException(BillingNotSupportedException())
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "client disconnected")
                        }

                        runAsync {
                            clientMutex.withLock {
                                if (_billingClient === initBillingClient) { _billingClient = null }
                            }
                        }
                    }
                })
            }

            return block(initBillingClient)
        }
    }

    suspend fun querySkus(skuIds: List<String>): List<SkuDetails> = initAndUseClient { client ->
        val (billingResult, data) = client.querySkuDetails(
                SkuDetailsParams.newBuilder()
                        .setSkusList(skuIds)
                        .setType(BillingClient.SkuType.INAPP)
                        .build()
        )

        billingResult.assertSuccess()

        data ?: throw BillingClientException("empty response")
    }

    suspend fun queryPurchases() = initAndUseClient { client ->
        val response = client.queryPurchases(BillingClient.SkuType.INAPP)

        response.billingResult.assertSuccess()

        response.purchasesList!!.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
    }

    private suspend fun queryAndProcessPurchases() {
        processMutex.withLock {
            isWorkingInternal.setTemporarily(true).use {
                try {
                    initAndUseClient { client ->
                        val result = client.queryPurchases(BillingClient.SkuType.INAPP)

                        result.billingResult.assertSuccess()

                        for (purchase in result.purchasesList!!) {
                            handlePurchase(purchase, client)
                        }
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "queryAndProcessPurchases() failed", ex)
                    }

                    hadErrorInternal.value = true
                }
            }
        }
    }

    fun queryAndProcessPurchasesAsync() {
        runAsync { queryAndProcessPurchases() }
    }

    private val purchaseUpdatedListener: PurchasesUpdatedListener = object: PurchasesUpdatedListener {
        override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
            runAsync {
                processMutex.withLock {
                    isWorkingInternal.setTemporarily(true).use {
                        initAndUseClient { client ->
                            try {
                                p0.assertSuccess()

                                for (purchase in p1!!) {
                                    handlePurchase(purchase, client)
                                }
                            } catch (ex: Exception) {
                                if (BuildConfig.DEBUG) {
                                    Log.w(LOG_TAG, "onPurchasesUpdated() failed", ex)
                                }

                                hadErrorInternal.value = true
                            }
                        }
                    }
                }
            }
        }
    }

    fun forgetActivityCheckout() {
        runAsync {
            clientMutex.withLock {
                _billingClient?.endConnection()
                _billingClient = null
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, billingClient: BillingClient) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) {
            // we are not interested in it
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "handlePurchase($purchase)")
        }

        if (PurchaseIds.SAL_SKUS.contains(purchase.sku)) {
            // just acknowledge

            billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
            ).assertSuccess()
        } else if (PurchaseIds.BUY_SKUS.contains(purchase.sku)) {
            // send and consume

            val server = logic.serverLogic.getServerConfigCoroutine()

            if (server.hasAuthToken) {
                server.api.finishPurchaseByGooglePlay(
                        receipt = purchase.originalJson,
                        signature = purchase.signature,
                        deviceAuthToken = server.deviceAuthToken
                )

                billingClient.consumePurchase(
                        ConsumeParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                )

                processPurchaseSuccessInternal.value = true
            } else {
                Log.w(LOG_TAG, "purchase for the premium version but no server available")
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "don't know how to handle ${purchase.sku}")
            }
        }
    }

    fun startPurchase(sku: String, checkAtBackend: Boolean, activity: Activity) {
        runAsync {
            try {
                val skuDetails = querySkus(listOf(sku)).single()

                if (skuDetails.sku != sku) throw IllegalStateException()

                startPurchase(skuDetails, checkAtBackend, activity)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not start purchase", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startPurchase(skuDetails: SkuDetails, checkAtBackend: Boolean, activity: Activity) {
        runAsync {
            initAndUseClient { client ->
                try {
                    if (checkAtBackend) {
                        val server = logic.serverLogic.getServerConfigCoroutine()

                        if (!server.hasAuthToken) {
                            Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                            return@initAndUseClient
                        }

                        if (!(server.api.canDoPurchase(server.deviceAuthToken) is CanDoPurchaseStatus.Yes)) {
                            throw IOException("can not do purchase right now")
                        }
                    }

                    client.launchBillingFlow(
                            activity,
                            BillingFlowParams.newBuilder()
                                    .setSkuDetails(skuDetails)
                                    .build()
                    ).assertSuccess()
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "could not start purchase", ex)
                    }

                    Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

enum class ActivityPurchaseModelStatus {
    Idle, Working, Error, Done
}
