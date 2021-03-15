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

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.extensions.BillingNotSupportedException
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.CanDoPurchaseStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PurchaseModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }
    private val statusInternal = MutableLiveData<PurchaseFragmentStatus>()
    private val lock = Mutex()

    val status = statusInternal.castDown()

    fun retry(activityPurchaseModel: ActivityPurchaseModel) {
        if (this.status.value is PurchaseFragmentRecoverableError || this.status.value == null) {
            prepare(activityPurchaseModel)
        }
    }

    private fun prepare(activityPurchaseModel: ActivityPurchaseModel) {
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

                        if (canDoPurchase is CanDoPurchaseStatus.Yes) {
                            if (canDoPurchase.publicKey?.contentEquals(Base64.decode(BuildConfig.googlePlayKey, 0)) == false) {
                                statusInternal.value = PurchaseFragmentServerHasDifferentPublicKey
                            } else {
                                val skus = activityPurchaseModel.querySkus(PurchaseIds.BUY_SKUS)

                                statusInternal.value = PurchaseFragmentReady(
                                        monthPrice = skus.find { it.sku == PurchaseIds.SKU_MONTH }?.price.toString(),
                                        yearPrice = skus.find { it.sku == PurchaseIds.SKU_YEAR }?.price.toString()
                                )
                            }
                        } else if (canDoPurchase == CanDoPurchaseStatus.NotDueToOldPurchase) {
                            statusInternal.value = PurchaseFragmentExistingPaymentError
                        } else {
                            statusInternal.value = PurchaseFragmentServerRejectedError
                        }
                    }
                } catch (ex: BillingNotSupportedException) {
                    statusInternal.value = PurchaseFragmentErrorBillingNotSupportedByDevice
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
object PurchaseFragmentServerHasDifferentPublicKey: PurchaseFragmentUnrecoverableError()