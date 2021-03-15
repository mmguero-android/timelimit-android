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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown

class StayAwesomeModel(application: Application): AndroidViewModel(application) {
    private val statusInternal = MutableLiveData<StayAwesomeStatus>()
    val status = statusInternal.castDown()

    fun load(activityPurchaseModel: ActivityPurchaseModel) {
        runAsync {
            statusInternal.value = LoadingStayAwesomeStatus

            if (!BuildConfig.storeCompilant) {
                statusInternal.value = NotSupportedByAppStayAwesomeStatus

                return@runAsync
            }

            try {
                val skus = activityPurchaseModel.querySkus(PurchaseIds.SAL_SKUS)
                val purchases = activityPurchaseModel.queryPurchases()

                statusInternal.value = ReadyStayAwesomeStatus(
                        PurchaseIds.SAL_SKUS.map { skuId ->
                            val sku = skus.find { it.sku == skuId }

                            StayAwesomeItem(
                                    id = skuId,
                                    title = sku?.description ?: skuId,
                                    price = sku?.price ?: "???",
                                    bought = purchases.find { it.sku == skuId } != null
                            )
                        }
                )
            } catch (ex: Exception) {
                statusInternal.value = NotSupportedByDeviceStayAwesomeStatus
            }
        }
    }
}

sealed class StayAwesomeStatus
object LoadingStayAwesomeStatus: StayAwesomeStatus()
object NotSupportedByDeviceStayAwesomeStatus: StayAwesomeStatus()
object NotSupportedByAppStayAwesomeStatus: StayAwesomeStatus()
data class ReadyStayAwesomeStatus(val items: List<StayAwesomeItem>): StayAwesomeStatus()

data class StayAwesomeItem(val id: String, val title: String, val price: String, val bought: Boolean)