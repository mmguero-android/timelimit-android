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

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentSetupDevicePermissionsBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.android.AdminReceiver
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.device.manage.InformAboutDeviceOwnerDialogFragment
import android.net.Uri


class SetupDevicePermissionsFragment : Fragment() {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private lateinit var binding: FragmentSetupDevicePermissionsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)

        binding = FragmentSetupDevicePermissionsBinding.inflate(inflater, container, false)

        binding.handlers = object: SetupDevicePermissionsHandlers {
            override fun manageDeviceAdmin() {
                val protectionLevel = logic.platformIntegration.getCurrentProtectionLevel()

                if (protectionLevel == ProtectionLevel.None) {
                    if (InformAboutDeviceOwnerDialogFragment.shouldShow) {
                        InformAboutDeviceOwnerDialogFragment().show(fragmentManager!!)
                    } else {
                        startActivity(
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                        .putExtra(
                                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                ComponentName(context!!, AdminReceiver::class.java)
                                        )
                        )
                    }
                } else {
                    startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            override fun openUsageStatsSettings() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            override fun openNotificationAccessSettings() {
                try {
                    startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ex: Exception) {
                    Toast.makeText(
                            context,
                            R.string.error_general,
                            Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun openDrawOverOtherAppsScreen() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context!!.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            override fun openAccessibilitySettings() {
                startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            override fun gotoNextStep() {
                navigation.safeNavigate(
                        SetupDevicePermissionsFragmentDirections
                                .actionSetupDevicePermissionsFragmentToSetupLocalModeFragment(),
                        R.id.setupDevicePermissionsFragment
                )
            }
        }

        refreshStatus()

        return binding.root
    }

    private fun refreshStatus() {
        val platform = logic.platformIntegration

        binding.notificationAccessPermission = platform.getNotificationAccessPermissionStatus()
        binding.protectionLevel = platform.getCurrentProtectionLevel()
        binding.usageStatsAccess = platform.getForegroundAppPermissionStatus()
        binding.overlayPermission = platform.getOverlayPermissionStatus()
        binding.accessibilityServiceEnabled = platform.isAccessibilityServiceEnabled()
    }

    override fun onResume() {
        super.onResume()

        refreshStatus()
    }
}

interface SetupDevicePermissionsHandlers {
    fun manageDeviceAdmin()
    fun openUsageStatsSettings()
    fun openNotificationAccessSettings()
    fun openDrawOverOtherAppsScreen()
    fun openAccessibilitySettings()
    fun gotoNextStep()
}
