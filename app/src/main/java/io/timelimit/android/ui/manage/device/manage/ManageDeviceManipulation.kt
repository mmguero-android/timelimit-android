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

import android.view.ViewGroup
import android.widget.CheckBox
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.HadManipulationFlag
import io.timelimit.android.databinding.ManageDeviceManipulationViewBinding
import io.timelimit.android.livedata.map
import io.timelimit.android.sync.actions.IgnoreManipulationAction
import io.timelimit.android.ui.main.ActivityViewModel

object ManageDeviceManipulation {
    fun bindView(
            binding: ManageDeviceManipulationViewBinding,
            deviceEntry: LiveData<Device?>,
            lifecycleOwner: LifecycleOwner,
            activityViewModel: ActivityViewModel,
            status: ManageDeviceManipulationStatus
    ) {
        val selectedCurrent = status.selectedCurrent
        val selectedPast = status.selectedPast

        val currentWarnings = deviceEntry.map { device ->
            if (device == null) {
                ManipulationWarnings.empty
            } else {
                ManipulationWarnings.getFromDevice(device)
            }
        }

        currentWarnings.observe(lifecycleOwner, Observer { warnings ->
            binding.hasAnyManipulation = !warnings.isEmpty

            binding.currentManipulations.removeAllViews()
            binding.pastManipulations.removeAllViews()

            fun createCheckbox() = CheckBox(binding.root.context)
            fun bindList(container: ViewGroup, entries: List<ManipulationWarningType>, selection: MutableList<ManipulationWarningType>) {
                container.removeAllViews()

                entries.forEach { warning ->
                    container.addView(
                            createCheckbox().apply {
                                setText(ManipulationWarningTypeLabel.getLabel(warning))
                                isChecked = selection.contains(warning)

                                setOnCheckedChangeListener { _, newIsChecked ->
                                    if (newIsChecked) {
                                        if (activityViewModel.requestAuthenticationOrReturnTrue()) {
                                            selection.add(warning)
                                        } else {
                                            isChecked = false
                                        }
                                    } else {
                                        selection.remove(warning)
                                    }
                                }
                            }
                    )
                }

                selection.removeAll { !entries.contains(it) }
            }

            val visiblePastWarnings = warnings.past.filterNot { warnings.current.contains(it) }

            bindList(binding.currentManipulations, warnings.current, selectedCurrent)
            bindList(binding.pastManipulations, visiblePastWarnings, selectedPast)

            binding.hasCurrentlyManipulation = warnings.current.isNotEmpty()
            binding.hadManipulationInPast = visiblePastWarnings.isNotEmpty()
        })

        binding.ignoreWarningsBtn.setOnClickListener {
            val device = deviceEntry.value ?: return@setOnClickListener
            val warnings = ManipulationWarnings.getFromDevice(device)

            val action = ManipulationWarnings(
                    current = selectedCurrent,
                    past = warnings.both.intersect(selectedCurrent).toList() + selectedPast
            ).buildIgnoreAction(device.id)

            if (action.isEmpty) {
                Snackbar.make(
                        binding.root,
                        R.string.manage_device_manipulation_toast_nothing_selected,
                        Snackbar.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            activityViewModel.tryDispatchParentAction(action)
        }
    }
}

data class ManipulationWarnings(val current: List<ManipulationWarningType>, val past: List<ManipulationWarningType>) {
    companion object {
        val empty = ManipulationWarnings(current = emptyList(), past = emptyList())

        fun getFromDevice(device: Device): ManipulationWarnings {
            val current = mutableListOf<ManipulationWarningType>()
            val past = mutableListOf<ManipulationWarningType>()

            val manipulationFlags = device.hadManipulationFlags
            fun isFlagSet(flag: Long) = (manipulationFlags and flag) == flag

            if (device.manipulationTriedDisablingDeviceAdmin) {
                current.add(ManipulationWarningType.TriedDisablingDeviceAdmin)
            }

            if (device.manipulationOfAppVersion) {
                current.add(ManipulationWarningType.AppDowngrade)
            }
            if (isFlagSet(HadManipulationFlag.APP_VERSION)) {
                past.add(ManipulationWarningType.AppDowngrade)
            }

            if (device.manipulationOfProtectionLevel) {
                current.add(ManipulationWarningType.DeviceAdmin)
            }
            if (isFlagSet(HadManipulationFlag.PROTECTION_LEVEL)) {
                past.add(ManipulationWarningType.DeviceAdmin)
            }

            if (device.manipulationOfUsageStats) {
                current.add(ManipulationWarningType.UsageStats)
            }
            if (isFlagSet(HadManipulationFlag.USAGE_STATS_ACCESS)) {
                past.add(ManipulationWarningType.UsageStats)
            }

            if (device.manipulationOfNotificationAccess) {
                current.add(ManipulationWarningType.NotificationAccess)
            }
            if (isFlagSet(HadManipulationFlag.NOTIFICATION_ACCESS)) {
                past.add(ManipulationWarningType.NotificationAccess)
            }

            if (device.manipulationOfOverlayPermission) {
                current.add(ManipulationWarningType.OverlayPermission)
            }
            if (isFlagSet(HadManipulationFlag.OVERLAY_PERMISSION)) {
                past.add(ManipulationWarningType.OverlayPermission)
            }

            if (device.wasAccessibilityServiceEnabled) {
                if (!device.accessibilityServiceEnabled) {
                    current.add(ManipulationWarningType.AccessibilityService)
                }
            }
            if (isFlagSet(HadManipulationFlag.ACCESSIBILITY_SERVICE)) {
                past.add(ManipulationWarningType.AccessibilityService)
            }

            if (device.manipulationDidReboot) {
                current.add(ManipulationWarningType.DidReboot)
            }

            if (device.hadManipulation) {
                if (past.isEmpty()) {
                    past.add(ManipulationWarningType.Unknown)
                }
            }

            return ManipulationWarnings(
                    current = current,
                    past = past
            )
        }
    }

    val both = current.intersect(past)
    val isEmpty = current.isEmpty() and past.isEmpty()

    fun buildIgnoreAction(deviceId: String): IgnoreManipulationAction {
        var ignoreHadManipulationFlags = 0L

        past.forEach { type ->
            ignoreHadManipulationFlags = ignoreHadManipulationFlags or when(type) {
                ManipulationWarningType.TriedDisablingDeviceAdmin -> throw IllegalArgumentException()
                ManipulationWarningType.AppDowngrade -> HadManipulationFlag.APP_VERSION
                ManipulationWarningType.DeviceAdmin -> HadManipulationFlag.PROTECTION_LEVEL
                ManipulationWarningType.UsageStats -> HadManipulationFlag.USAGE_STATS_ACCESS
                ManipulationWarningType.NotificationAccess -> HadManipulationFlag.NOTIFICATION_ACCESS
                ManipulationWarningType.OverlayPermission -> HadManipulationFlag.OVERLAY_PERMISSION
                ManipulationWarningType.AccessibilityService -> HadManipulationFlag.ACCESSIBILITY_SERVICE
                ManipulationWarningType.DidReboot -> throw IllegalArgumentException()
                ManipulationWarningType.Unknown -> 0L   // handled at an other location
            }
        }

        return IgnoreManipulationAction(
                deviceId = deviceId,
                ignoreUsageStatsAccessManipulation = current.contains(ManipulationWarningType.UsageStats),
                ignoreNotificationAccessManipulation = current.contains(ManipulationWarningType.NotificationAccess),
                ignoreDeviceAdminManipulationAttempt = current.contains(ManipulationWarningType.TriedDisablingDeviceAdmin),
                ignoreDeviceAdminManipulation = current.contains(ManipulationWarningType.DeviceAdmin),
                ignoreOverlayPermissionManipulation = current.contains(ManipulationWarningType.OverlayPermission),
                ignoreAccessibilityServiceManipulation = current.contains(ManipulationWarningType.AccessibilityService),
                ignoreAppDowngrade = current.contains(ManipulationWarningType.AppDowngrade),
                ignoreReboot = current.contains(ManipulationWarningType.DidReboot),
                ignoreHadManipulation = past.contains(ManipulationWarningType.Unknown),
                ignoreHadManipulationFlags = ignoreHadManipulationFlags
        )
    }
}

enum class ManipulationWarningType {
    TriedDisablingDeviceAdmin,
    AppDowngrade,
    DeviceAdmin,
    UsageStats,
    NotificationAccess,
    OverlayPermission,
    AccessibilityService,
    DidReboot,
    Unknown
}

object ManipulationWarningTypeLabel {
    fun getLabel(type: ManipulationWarningType) = when (type) {
        ManipulationWarningType.TriedDisablingDeviceAdmin -> R.string.manage_device_manipulation_device_admin_disable_attempt
        ManipulationWarningType.AppDowngrade -> R.string.manage_device_manipulation_app_version
        ManipulationWarningType.DeviceAdmin -> R.string.manage_device_manipulation_device_admin_disabled
        ManipulationWarningType.UsageStats -> R.string.manage_device_manipulation_usage_stats_access
        ManipulationWarningType.NotificationAccess -> R.string.manage_device_manipulation_notification_access
        ManipulationWarningType.OverlayPermission -> R.string.manage_device_manipulation_overlay_permission
        ManipulationWarningType.AccessibilityService -> R.string.manage_device_manipulation_accessibility_service
        ManipulationWarningType.DidReboot -> R.string.manage_device_manipulation_reboot
        ManipulationWarningType.Unknown -> R.string.manage_device_manipulation_existed
    }
}

class ManageDeviceManipulationStatus {
    val selectedCurrent = mutableListOf<ManipulationWarningType>()
    val selectedPast = mutableListOf<ManipulationWarningType>()
}

class ManageDeviceManipulationStatusModel: ViewModel() {
    val data = ManageDeviceManipulationStatus()
}