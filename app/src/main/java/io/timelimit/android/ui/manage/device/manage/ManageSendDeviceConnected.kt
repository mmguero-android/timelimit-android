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
import io.timelimit.android.data.model.Device
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.sync.actions.SetSendDeviceConnected
import io.timelimit.android.ui.main.ActivityViewModel

object ManageSendDeviceConnected {
    fun bind(
            binding: io.timelimit.android.databinding.ManageSendDeviceConnectedBinding,
            auth: ActivityViewModel,
            deviceEntry: LiveData<Device?>,
            lifecycleOwner: LifecycleOwner
    ) {
        val ownDeviceIdLive = auth.logic.deviceId

        mergeLiveData(ownDeviceIdLive, deviceEntry).observe(lifecycleOwner, Observer { (ownDeviceId, device) ->
            binding.isThisDevice = ownDeviceId == device?.id && device?.id != null
        })

        auth.database.config().getDeviceAuthTokenAsync().observe(lifecycleOwner, Observer {
            binding.isConnectedMode = it.isNotEmpty()
        })

        deviceEntry.observe(lifecycleOwner, Observer {
            val currentStatus = it?.showDeviceConnected ?: false

            binding.checkbox.setOnCheckedChangeListener { _, _ -> /* ignore */ }
            binding.checkbox.isChecked = currentStatus

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentStatus) {
                    if (
                            it != null &&
                            auth.tryDispatchParentAction(
                                    SetSendDeviceConnected(
                                            deviceId = it.id,
                                            enable = isChecked
                                    )
                            )
                    ) {
                        // nothing to do
                    } else {
                        binding.checkbox.isChecked = currentStatus
                    }
                }
            }
        })
    }
}