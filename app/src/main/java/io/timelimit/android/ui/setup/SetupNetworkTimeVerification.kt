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
package io.timelimit.android.ui.setup

import io.timelimit.android.R
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.databinding.SetupNetworkTimeVerificationBinding

object SetupNetworkTimeVerification {
    fun readSelection(view: SetupNetworkTimeVerificationBinding) = when (view.networkTimeVerificationRadioGroup.checkedRadioButtonId) {
        R.id.network_time_verification_disabled -> NetworkTime.Disabled
        R.id.network_time_verification_if_possible -> NetworkTime.IfPossible
        R.id.network_time_verification_enabled -> NetworkTime.Enabled
        else -> throw IllegalStateException()
    }
}
