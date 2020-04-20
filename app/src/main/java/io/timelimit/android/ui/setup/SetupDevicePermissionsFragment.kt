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
package io.timelimit.android.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
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
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.FragmentSetupDevicePermissionsBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.android.AdminReceiver
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.AdbDeviceAdminDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.AdbUsageStatsDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.InformAboutDeviceOwnerDialogFragment


class SetupDevicePermissionsFragment : Fragment() {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private lateinit var binding: FragmentSetupDevicePermissionsBinding

    lateinit var refreshStatusRunnable: Runnable

    init {
        refreshStatusRunnable = Runnable {
            refreshStatus()

            Threads.mainThreadHandler.postDelayed(refreshStatusRunnable, 2000L)
        }
    }

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
                        try {
                            startActivity(
                                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                            .putExtra(
                                                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                    ComponentName(context!!, AdminReceiver::class.java)
                                            )
                            )
                        } catch (ex: Exception) {
                            AdbDeviceAdminDialogFragment().show(parentFragmentManager)
                        }
                    }
                } else {
                    try {
                        startActivity(
                                Intent(Settings.ACTION_SECURITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (ex: Exception) {
                        AdbDeviceAdminDialogFragment().show(parentFragmentManager)
                    }
                }
            }

            override fun openUsageStatsSettings() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        // According to user reports, some devices open the wrong screen
                        // with the Settings.ACTION_USAGE_ACCESS_SETTINGS
                        // but using an activity launcher to open this intent works for them.
                        // This intent works at regular android too, so try this first
                        // and use the "correct" one as fallback.

                        startActivity(
                                Intent()
                                        .setClassName("com.android.settings", "com.android.settings.Settings\$UsageAccessSettingsActivity")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (ex: Exception) {
                        try {
                            startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (ex: Exception) {
                            AdbUsageStatsDialogFragment().show(parentFragmentManager)
                        }
                    }
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
                    try {
                        startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context!!.packageName))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (ex: Exception) {
                        Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun openAccessibilitySettings() {
                try {
                    startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ex: Exception) {
                    Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }

            override fun gotoNextStep() {
                navigation.safeNavigate(
                        SetupDevicePermissionsFragmentDirections
                                .actionSetupDevicePermissionsFragmentToSetupLocalModeFragment(),
                        R.id.setupDevicePermissionsFragment
                )
            }

            override fun helpUsageStatsAccess() {
                HelpDialogFragment.newInstance(
                        title = R.string.manage_device_permissions_usagestats_title,
                        text = R.string.manage_device_permissions_usagestats_text
                ).show(fragmentManager!!)
            }

            override fun helpNotificationAccess() {
                HelpDialogFragment.newInstance(
                        title = R.string.manage_device_permission_notification_access_title,
                        text = R.string.manage_device_permission_notification_access_text
                ).show(fragmentManager!!)
            }

            override fun helpDrawOverOtherApps() {
                HelpDialogFragment.newInstance(
                        title = R.string.manage_device_permissions_overlay_title,
                        text = R.string.manage_device_permissions_overlay_text
                ).show(fragmentManager!!)
            }

            override fun helpAccesibility() {
                HelpDialogFragment.newInstance(
                        title = R.string.manage_device_permission_accessibility_title,
                        text = R.string.manage_device_permission_accessibility_text
                ).show(fragmentManager!!)
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

        // this additionally schedules it
        refreshStatusRunnable.run()
    }

    override fun onPause() {
        super.onPause()

        Threads.mainThreadHandler.removeCallbacks(refreshStatusRunnable)
    }
}

interface SetupDevicePermissionsHandlers {
    fun manageDeviceAdmin()
    fun openUsageStatsSettings()
    fun openNotificationAccessSettings()
    fun openDrawOverOtherAppsScreen()
    fun openAccessibilitySettings()
    fun gotoNextStep()
    fun helpUsageStatsAccess()
    fun helpNotificationAccess()
    fun helpDrawOverOtherApps()
    fun helpAccesibility()
}
