/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.extensions.consumeAsync
import io.timelimit.android.extensions.startAsync
import io.timelimit.android.extensions.waitUntilReady
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.livedata.setTemporarily
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.solovyev.android.checkout.*
import java.io.IOException

class ActivityPurchaseModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ActivityPurchaseModel"
    }

    private val billing = (application as io.timelimit.android.Application).billing
    private val logic = DefaultAppLogic.with(application)
    private val lock = Mutex()
    private val isWorkingInternal = MutableLiveData<Boolean>().apply { value = false }
    private val hadErrorInternal = MutableLiveData<Boolean>().apply { value = false }
    private val processPurchaseSuccessInternal = MutableLiveData<Boolean>().apply { value = false }

    val isWorking = isWorkingInternal.castDown()
    val hadError = hadErrorInternal.castDown()
    val processPurchaseSuccess = processPurchaseSuccessInternal.castDown()
    val status = mergeLiveData(isWorking, hadError, processPurchaseSuccess).map {
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

    private var activityCheckout: ActivityCheckout? = null

    fun setActivityCheckout(checkout: ActivityCheckout) {
        checkout.start()

        checkout.createPurchaseFlow(object: RequestListener<Purchase> {
            override fun onError(response: Int, e: Exception) {
                // ignored
            }

            override fun onSuccess(result: Purchase) {
                if (PurchaseIds.SKUS.contains(result.sku)) {
                    runAsync {
                        lock.withLock {
                            isWorkingInternal.setTemporarily(true).use { _ ->
                                handlePurchase(result)
                            }
                        }
                    }
                }
            }
        })

        activityCheckout = checkout
    }

    fun forgetActivityCheckout() {
        activityCheckout?.stop()
        activityCheckout = null
    }

    fun queryAndProcessPurchasesAsync() {
        runAsync {
            lock.withLock {
                isWorkingInternal.setTemporarily(true).use { _ ->
                    val checkout = activityCheckout

                    if (checkout != null) {
                        val inventory = checkout.makeInventory()

                        inventory.load(
                                Inventory.Request.create()
                                        .loadAllPurchases(),
                                object: Inventory.Callback {
                                    override fun onLoaded(products: Inventory.Products) {
                                        products[ProductTypes.IN_APP].purchases.forEach { purchase ->
                                            if (PurchaseIds.SKUS.contains(purchase.sku)) {
                                                runAsync {
                                                    handlePurchase(purchase)
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "handlePurchase()")
        }

        try {
            val server = logic.serverLogic.getServerConfigCoroutine()

            if (server.hasAuthToken) {
                server.api.finishPurchaseByGooglePlay(
                        receipt = purchase.data,
                        signature = purchase.signature,
                        deviceAuthToken = server.deviceAuthToken
                )
            }

            processPurchaseSuccessInternal.value = true
            consumePurchaseAsync(purchase)
        } catch (ex: Exception) {
            hadErrorInternal.value = true

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "server rejected purchase", ex)
            }
        }
    }

    private fun consumePurchaseAsync(purchase: Purchase) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "consumePurchaseAsync()")
        }

        runAsync {
            lock.withLock {
                Checkout.forApplication(billing).startAsync().use {
                    it.requests.consumeAsync(purchase.token)
                }
            }
        }
    }

    fun startPurchase(sku: String) {
        runAsync {
            lock.withLock {
                isWorkingInternal.setTemporarily(true).use {
                    _ ->

                    try {
                        val server = logic.serverLogic.getServerConfigCoroutine()

                        if (!server.hasAuthToken) {
                            Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                            return@runAsync
                        }

                        if (server.api.canDoPurchase(server.deviceAuthToken) != CanDoPurchaseStatus.Yes) {
                            throw IOException("can not do purchase right now")
                        }

                        // start the purchase
                        val activityCheckout = activityCheckout

                        if (activityCheckout == null) {
                            Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                            return@runAsync
                        }

                        activityCheckout.waitUntilReady().requests.purchase(
                                ProductTypes.IN_APP,
                                sku,
                                null,
                                activityCheckout.purchaseFlow
                        )
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
}

enum class ActivityPurchaseModelStatus {
    Idle, Working, Error, Done
}
