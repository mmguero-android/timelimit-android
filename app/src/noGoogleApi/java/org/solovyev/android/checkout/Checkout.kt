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
package org.solovyev.android.checkout

import android.app.Activity
import io.timelimit.android.async.Threads

open class Checkout {
    companion object {
        private val instance = Checkout()

        fun forActivity(activity: Activity, billing: Billing) = ActivityCheckout
        fun forApplication(billing: Billing) = instance
    }

    fun start(listener: EmptyListener) {
        Threads.mainThreadHandler.post {
            listener.onReady(
                    billingSupported = false,
                    product = "",
                    requests = BillingRequests
            )
        }
    }

    fun whenReady(listener: EmptyListener) = start(listener)

    fun stop() {
        // nothing to do
    }

    fun makeInventory() = Inventory

    abstract class EmptyListener {
        abstract fun onReady(requests: BillingRequests, product: String, billingSupported: Boolean)
    }
}