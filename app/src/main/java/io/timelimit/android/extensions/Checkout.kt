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
package io.timelimit.android.extensions

import org.solovyev.android.checkout.BillingRequests
import org.solovyev.android.checkout.Checkout
import org.solovyev.android.checkout.RequestListener
import org.solovyev.android.checkout.Skus
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun Checkout.startAsync(): CheckoutStartResponse {
    val checkout = this
    var resumed = false

    return suspendCoroutine<CheckoutStartResponse> {
        continuation ->

        checkout.start(object: Checkout.EmptyListener() {
            override fun onReady(requests: BillingRequests, product: String, billingSupported: Boolean) {
                if (!resumed) {
                    resumed = true

                    continuation.resume(CheckoutStartResponse(
                            requests = requests,
                            product = product,
                            billingSupported = billingSupported,
                            checkout = checkout
                    ))
                }
            }
        })
    }
}

suspend fun Checkout.waitUntilReady(): CheckoutStartResponse {
    val checkout = this
    var resumed = false

    return suspendCoroutine {
        continuation ->

        checkout.whenReady(object: Checkout.EmptyListener() {
            override fun onReady(requests: BillingRequests, product: String, billingSupported: Boolean) {
                if (!resumed) {
                    resumed = true

                    continuation.resume(CheckoutStartResponse(
                            requests = requests,
                            product = product,
                            billingSupported = billingSupported,
                            checkout = checkout
                    ))
                }
            }
        })
    }
}

suspend fun BillingRequests.getSkusAsync(product: String, skus: List<String>): Skus {
    val requests = this

    return suspendCoroutine {
        continuation ->

        requests.getSkus(product, skus, object: RequestListener<Skus> {
            override fun onError(response: Int, e: Exception) {
                continuation.resumeWithException(e)
            }

            override fun onSuccess(result: Skus) {
                continuation.resume(result)
            }
        })
    }
}

suspend fun BillingRequests.consumeAsync(token: String) {
    val requests = this

    suspendCoroutine<Any> {
        continuation ->

        requests.consume(token, object: RequestListener<Any> {
            override fun onError(response: Int, e: java.lang.Exception) {
                continuation.resumeWithException(e)
            }

            override fun onSuccess(result: Any) {
                continuation.resume(result)
            }
        })
    }
}

class CheckoutStartResponse (
        val requests: BillingRequests,
        val product: String,
        val billingSupported: Boolean,
        private val checkout: Checkout
): Closeable {
    override fun close() {
        checkout.stop()
    }
}
