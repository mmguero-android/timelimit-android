/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.sync.network

import android.util.Base64
import android.util.JsonReader

sealed class CanDoPurchaseStatus {
    class Yes(val publicKey: ByteArray?): CanDoPurchaseStatus()
    object NoForUnknownReason: CanDoPurchaseStatus()
    object NotDueToOldPurchase: CanDoPurchaseStatus()
}

object CanDoPurchaseParser {
    private const val CAN_DO_PURCHASE = "canDoPurchase"
    private const val GPLAY_PUBLIC_KEY = "googlePlayPublicKey"
    private const val YES = "yes"
    private const val NO_DUE_TO_OLD_PURCHASE = "no due to old purchase"

    fun parse(reader: JsonReader): CanDoPurchaseStatus {
        reader.beginObject()

        var canDoPurchaseStatus: String? = null
        var publicKey: ByteArray? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                CAN_DO_PURCHASE -> canDoPurchaseStatus = reader.nextString()
                GPLAY_PUBLIC_KEY -> publicKey = Base64.decode(reader.nextString(), 0)
                else -> reader.skipValue()
            }
        }

        reader.endObject()

        return when (canDoPurchaseStatus!!) {
            YES -> CanDoPurchaseStatus.Yes(publicKey)
            NO_DUE_TO_OLD_PURCHASE -> CanDoPurchaseStatus.NotDueToOldPurchase
            else -> CanDoPurchaseStatus.NoForUnknownReason
        }
    }
}