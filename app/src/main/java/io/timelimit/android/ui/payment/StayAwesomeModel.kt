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
import io.timelimit.android.extensions.loadAsync
import io.timelimit.android.extensions.startAsync
import io.timelimit.android.livedata.castDown
import org.solovyev.android.checkout.Checkout
import org.solovyev.android.checkout.Inventory
import org.solovyev.android.checkout.ProductTypes

class StayAwesomeModel(application: Application): AndroidViewModel(application) {
    private val application: io.timelimit.android.Application by lazy { application as io.timelimit.android.Application }
    private val statusInternal = MutableLiveData<StayAwesomeStatus>()

    val status = statusInternal.castDown()

    fun load() {
        runAsync {
            statusInternal.value = LoadingStayAwesomeStatus

            if (!BuildConfig.storeCompilant) {
                statusInternal.value = NotSupportedByAppStayAwesomeStatus

                return@runAsync
            }

            val checkout = Checkout.forApplication(application.billing)

            checkout.startAsync().use {
                if (!it.billingSupported) {
                    statusInternal.value = NotSupportedByDeviceStayAwesomeStatus
                } else {
                    val skus = it.requests.getSkusAsync(
                            ProductTypes.IN_APP,
                            PurchaseIds.SLA_SKUS
                    )

                    val inventory = checkout.makeInventory()
                    val products = inventory.loadAsync(Inventory.Request.create().loadAllPurchases())
                    val purchasedSkus = products[ProductTypes.IN_APP].purchases.map { it.sku }.toSet()

                    statusInternal.value = ReadyStayAwesomeStatus(
                            PurchaseIds.SLA_SKUS.map { skuId ->
                                val sku = skus.getSku(skuId)

                                StayAwesomeItem(
                                        id = skuId,
                                        title = sku?.description ?: skuId,
                                        price = sku?.price ?: "???",
                                        bought = purchasedSkus.contains(skuId)
                                )
                            }
                    )
                }
            }
        }
    }

    init {
        load()
    }
}

sealed class StayAwesomeStatus
object LoadingStayAwesomeStatus: StayAwesomeStatus()
object NotSupportedByDeviceStayAwesomeStatus: StayAwesomeStatus()
object NotSupportedByAppStayAwesomeStatus: StayAwesomeStatus()
data class ReadyStayAwesomeStatus(val items: List<StayAwesomeItem>): StayAwesomeStatus()

data class StayAwesomeItem(val id: String, val title: String, val price: String, val bought: Boolean)