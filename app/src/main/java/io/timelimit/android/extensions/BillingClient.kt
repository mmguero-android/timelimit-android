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
package io.timelimit.android.extensions

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import java.lang.RuntimeException

fun BillingResult.assertSuccess() {
    if (this.responseCode != BillingClient.BillingResponseCode.OK) {
        throw BillingClientException("error during processing billing request: ${this.debugMessage}")
    }
}

open class BillingClientException(message: String): RuntimeException(message)
class BillingNotSupportedException(): BillingClientException("billing not supported")