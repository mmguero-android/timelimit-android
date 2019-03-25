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

import android.widget.CompoundButton
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.ManageDeviceManipulationViewBinding
import io.timelimit.android.sync.actions.IgnoreManipulationAction
import io.timelimit.android.ui.main.ActivityViewModel

object ManageDeviceManipulation {
    fun bindView(
            binding: ManageDeviceManipulationViewBinding,
            deviceEntry: LiveData<Device?>,
            lifecycleOwner: LifecycleOwner,
            activityViewModel: ActivityViewModel
    ) {
        deviceEntry.observe(lifecycleOwner, Observer {
            device ->

            binding.hasTriedManipulatingDeviceAdmin = device?.manipulationTriedDisablingDeviceAdmin ?: false
            binding.hasManipulatedAppVersion = device?.manipulationOfAppVersion ?: false
            binding.hasManipulatedDeviceAdmin = device?.manipulationOfProtectionLevel ?: false
            binding.hasManipulatedUsageStatsAccess = device?.manipulationOfUsageStats ?: false
            binding.hasManipulatedNotificationAccess = device?.manipulationOfNotificationAccess ?: false
            binding.hasManipulatedOverlayPermission = device?.manipulationOfOverlayPermission ?: false
            binding.hasManipulationReboot = device?.manipulationDidReboot ?: false
            binding.hasHadManipulation = (device?.hadManipulation ?: false) and (! (device?.hasActiveManipulationWarning ?: false))
            binding.hasAnyManipulation = device?.hasAnyManipulation ?: false
        })

        val revertCheckedListener = object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(checkbox: CompoundButton, isChecked: Boolean) {
                if (isChecked) {
                    if (!activityViewModel.requestAuthenticationOrReturnTrue()) {
                        checkbox.isChecked = false
                    }
                }
            }
        }

        val checkboxes = listOf(
                binding.appVersionCheckbox,
                binding.deviceAdminDisableAttemptCheckbox,
                binding.deviceAdminDisabledCheckbox,
                binding.usageAccessCheckbox,
                binding.notificationAccessCheckbox,
                binding.overlayPermissionCheckbox,
                binding.rebootCheckbox,
                binding.hadManipulationCheckbox
        )

        checkboxes.forEach { checkbox -> checkbox.setOnCheckedChangeListener(revertCheckedListener) }

        binding.ignoreWarningsBtn.setOnClickListener {
            val device = deviceEntry.value

            if (device == null) {
                return@setOnClickListener
            }

            val action = IgnoreManipulationAction(
                    ignoreUsageStatsAccessManipulation = binding.usageAccessCheckbox.isChecked && binding.hasManipulatedUsageStatsAccess == true,
                    ignoreNotificationAccessManipulation = binding.notificationAccessCheckbox.isChecked && binding.hasManipulatedNotificationAccess == true,
                    ignoreDeviceAdminManipulationAttempt = binding.deviceAdminDisableAttemptCheckbox.isChecked && binding.hasTriedManipulatingDeviceAdmin == true,
                    ignoreDeviceAdminManipulation = binding.deviceAdminDisabledCheckbox.isChecked && binding.hasManipulatedDeviceAdmin == true,
                    ignoreOverlayPermissionManipulation = binding.overlayPermissionCheckbox.isChecked && binding.hasManipulatedOverlayPermission == true,
                    ignoreAppDowngrade = binding.appVersionCheckbox.isChecked && binding.hasManipulatedAppVersion == true,
                    ignoreReboot = binding.rebootCheckbox.isChecked && binding.hasManipulationReboot == true,
                    ignoreHadManipulation = binding.hadManipulationCheckbox.isChecked || (
                            device.hadManipulation and device.hasActiveManipulationWarning
                            ),
                    deviceId = device.id
            )

            if (action.isEmpty) {
                Snackbar.make(
                        binding.root,
                        R.string.manage_device_manipulation_toast_nothing_selected,
                        Snackbar.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (activityViewModel.tryDispatchParentAction(action)) {
                checkboxes.forEach { checkbox -> checkbox.isChecked = false }
            }
        }
    }
}
