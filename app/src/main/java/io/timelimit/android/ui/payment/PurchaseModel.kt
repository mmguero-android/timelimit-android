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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.extensions.getSkusAsync
import io.timelimit.android.extensions.startAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.solovyev.android.checkout.Checkout
import org.solovyev.android.checkout.ProductTypes

class PurchaseModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }
    private val application: io.timelimit.android.Application by lazy { application as io.timelimit.android.Application }
    private val statusInternal = MutableLiveData<PurchaseFragmentStatus>()
    private val lock = Mutex()

    val status = statusInternal.castDown()

    init {
        prepare()
    }

    fun retry() {
        if (this.status.value is PurchaseFragmentRecoverableError) {
            prepare()
        }
    }

    private fun prepare() {
        runAsync {
            lock.withLock {
                try {
                    statusInternal.value = PurchaseFragmentPreparing

                    if (!BuildConfig.storeCompilant) {
                        statusInternal.value = PurchaseFragmentErrorBillingNotSupportedByAppVariant
                    } else {
                        val server = logic.serverLogic.getServerConfigCoroutine()

                        val canDoPurchase = if (server.hasAuthToken)
                            server.api.canDoPurchase(server.deviceAuthToken)
                        else
                            CanDoPurchaseStatus.NoForUnknownReason

                        if (canDoPurchase == CanDoPurchaseStatus.Yes) {
                            val checkout = Checkout.forApplication(application.billing)

                            checkout.startAsync().use {
                                if (!it.billingSupported) {
                                    statusInternal.value = PurchaseFragmentErrorBillingNotSupportedByDevice
                                } else {
                                    val skus = it.requests.getSkusAsync(
                                            ProductTypes.IN_APP,
                                            PurchaseIds.SKUS
                                    )

                                    statusInternal.value = PurchaseFragmentReady(
                                            monthPrice = skus.getSku(PurchaseIds.SKU_MONTH)?.price.toString(),
                                            yearPrice = skus.getSku(PurchaseIds.SKU_YEAR)?.price.toString()
                                    )
                                }
                            }
                        } else if (canDoPurchase == CanDoPurchaseStatus.NotDueToOldPurchase) {
                            statusInternal.value = PurchaseFragmentExistingPaymentError
                        } else {
                            statusInternal.value = PurchaseFragmentServerRejectedError
                        }
                    }
                } catch (ex: Exception) {
                    statusInternal.value = PurchaseFragmentNetworkError
                }
            }
        }
    }
}

sealed class PurchaseFragmentStatus
sealed class PurchaseFragmentError: PurchaseFragmentStatus()
class PurchaseFragmentReady(val monthPrice: String, val yearPrice: String): PurchaseFragmentStatus()
object PurchaseFragmentPreparing: PurchaseFragmentStatus()

sealed class PurchaseFragmentUnrecoverableError: PurchaseFragmentError()
sealed class PurchaseFragmentRecoverableError: PurchaseFragmentError()

object PurchaseFragmentErrorBillingNotSupportedByDevice: PurchaseFragmentUnrecoverableError()
object PurchaseFragmentErrorBillingNotSupportedByAppVariant: PurchaseFragmentUnrecoverableError()
object PurchaseFragmentNetworkError: PurchaseFragmentRecoverableError()
object PurchaseFragmentExistingPaymentError: PurchaseFragmentUnrecoverableError()
object PurchaseFragmentServerRejectedError: PurchaseFragmentUnrecoverableError()
