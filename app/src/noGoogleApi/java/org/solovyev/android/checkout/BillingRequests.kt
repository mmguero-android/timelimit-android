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

import io.timelimit.android.async.Threads

object BillingRequests {
    fun consume(token: String, listener: RequestListener<Any>) {
        Threads.mainThreadHandler.post {
            listener.onSuccess(0)
        }
    }

    fun getSkus(product: String, skus: List<String>, listener: RequestListener<Skus>) {
        Threads.mainThreadHandler.post {
            listener.onSuccess(Skus)
        }
    }

    fun purchase(productType: String, sku: String, something: Unit?, purchaseFlow: PurchaseFlow) {
        // do nothing
    }
}