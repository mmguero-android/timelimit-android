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
package io.timelimit.android.ui.manage.device.manage

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.data.Database
import io.timelimit.android.databinding.RemoveDeviceViewBinding
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.manage.device.remove.ConfirmRemoveDeviceDialogFragment
import io.timelimit.android.ui.manage.device.remove.RemoveDeviceProgressDialog

object RemoveDevice {
    fun bind(
            view: RemoveDeviceViewBinding,
            activityViewModel: ActivityViewModel,
            fragmentManager: FragmentManager,
            deviceId: String,
            database: Database,
            lifecycleOwner: LifecycleOwner
    ) {
        view.removeBtn.setOnClickListener {
            if (activityViewModel.requestAuthenticationOrReturnTrue()) {
                ConfirmRemoveDeviceDialogFragment.newInstance(deviceId).show(fragmentManager)
            }
        }

        database.config().getDeviceAuthTokenAsync().observe(lifecycleOwner, Observer {
            view.isLocalMode = it.isEmpty()
        })
    }
}
