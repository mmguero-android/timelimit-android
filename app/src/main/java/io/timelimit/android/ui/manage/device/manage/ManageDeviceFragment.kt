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

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.FragmentManageDeviceBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RealTime
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.device.manage.feature.ManageDeviceFeaturesFragment
import io.timelimit.android.ui.manage.device.manage.permission.ManageDevicePermissionsFragment

class ManageDeviceFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFragmentArgs by lazy { ManageDeviceFragmentArgs.fromBundle(arguments!!) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = FragmentManageDeviceBinding.inflate(inflater, container, false)
        val userEntries = logic.database.user().getAllUsersLive()

        DeviceUninstall.bind(
                view = binding.uninstall,
                deviceEntry = deviceEntry,
                lifecycleOwner = this
        )

        ManageDeviceManipulation.bindView(
                binding = binding.manageManipulation,
                deviceEntry = deviceEntry,
                lifecycleOwner = this,
                activityViewModel = auth
        )

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        binding.handlers = object: ManageDeviceFragmentHandlers {
            override fun showUserScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceUserFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showPermissionsScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDevicePermissionsFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showFeaturesScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceFeaturesFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showManageScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceAdvancedFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack()
            } else {
                val now = RealTime.newInstance()
                logic.realTimeLogic.getRealTime(now)

                binding.modelString = device.model
                binding.addedAtString = getString(R.string.manage_device_added_at, DateUtils.getRelativeTimeSpanString(
                        device.addedAt,
                        now.timeInMillis,
                        DateUtils.HOUR_IN_MILLIS

                ))
                binding.didAppDowngrade = device.currentAppVersion < device.highestAppVersion
                binding.permissionCardText = ManageDevicePermissionsFragment.getPreviewText(device, context!!)
                binding.featureCardText = ManageDeviceFeaturesFragment.getPreviewText(device, context!!)
            }
        })

        val isThisDevice = logic.deviceId.map { ownDeviceId -> ownDeviceId == args.deviceId }.ignoreUnchanged()

        isThisDevice.observe(this, Observer {
            binding.isThisDevice = it
        })

        ManageDeviceIntroduction.bind(
                view = binding.introduction,
                database = logic.database,
                lifecycleOwner = this
        )

        val userEntry = deviceEntry.switchMap {
            device ->

            userEntries.map { users ->
                users.find { user -> user.id == device?.currentUserId }
            }
        }

        UsageStatsAccessRequiredAndMissing.bind(
                view = binding.usageStatsAccessMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        userEntry.observe(this, Observer {
            binding.userCardText = it?.name ?: getString(R.string.manage_device_current_user_none)
        })

        return binding.root
    }

    override fun getCustomTitle() = deviceEntry.map { it?.name }
}

interface ManageDeviceFragmentHandlers {
    fun showUserScreen()
    fun showPermissionsScreen()
    fun showFeaturesScreen()
    fun showManageScreen()
    fun showAuthenticationScreen()
}
