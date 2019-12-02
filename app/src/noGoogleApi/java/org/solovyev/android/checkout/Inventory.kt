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

object Inventory {
    object Products {
        object Type {
            val purchases = emptyList<Purchase>()
        }

        operator fun get(type: String) = Type
    }

    object Request {
        fun create() = this
        fun loadAllPurchases() = this
    }

    interface Callback {
        fun onLoaded(products: Inventory.Products)
    }

    fun load(request: Inventory.Request, callback: Callback) {
        Threads.mainThreadHandler.post {
            callback.onLoaded(Products)
        }
    }
}