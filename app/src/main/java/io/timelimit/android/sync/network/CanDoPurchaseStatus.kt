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
package io.timelimit.android.sync.network

enum class CanDoPurchaseStatus {
    Yes, NotDueToOldPurchase, NoForUnknownReason
}

object CanDoPurchaseParser {
    private const val YES = "yes"
    private const val NO_DUE_TO_OLD_PURCHASE = "no due to old purchase"

    fun parse(value: String) = when(value) {
        YES -> CanDoPurchaseStatus.Yes
        NO_DUE_TO_OLD_PURCHASE -> CanDoPurchaseStatus.NotDueToOldPurchase
        else -> CanDoPurchaseStatus.NoForUnknownReason
    }
}