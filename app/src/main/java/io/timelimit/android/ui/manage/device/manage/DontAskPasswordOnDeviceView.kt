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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Device
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.sync.actions.SetKeepSignedInAction
import io.timelimit.android.ui.main.ActivityViewModel

object DontAskPasswordOnDeviceView {
    fun bind(
            view: io.timelimit.android.databinding.DontAskPasswordOnDeviceViewBinding,
            lifecycleOwner: LifecycleOwner,
            deviceEntry: LiveData<Device?>,
            activityViewModel: ActivityViewModel
    ) {
        activityViewModel.database.config().getDeviceAuthTokenAsync().map { it.isNotEmpty() }.observe(lifecycleOwner, Observer {
            view.showCard = it
        })

        deviceEntry.observe(lifecycleOwner, Observer {
            view.arePasswordPromptsEnabled = !(it?.isUserKeptSignedIn ?: false)
        })

        view.btnEnablePromptsAgain.setOnClickListener {
            runAsync {
                val device = deviceEntry.waitForNullableValue()

                if (device != null) {
                    activityViewModel.tryDispatchParentAction(SetKeepSignedInAction(
                            deviceId = device.id,
                            keepSignedIn = false
                    ))
                }
            }
        }
    }
}