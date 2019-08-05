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
package io.timelimit.android.ui.manage.device.manage.feature


import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.databinding.ManageDeviceFeaturesFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RealTime
import io.timelimit.android.sync.actions.UpdateNetworkTimeVerificationAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ManageDeviceFeaturesFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        fun getPreviewText(device: Device, context: Context): String {
            val featureLabels = mutableListOf<String>()

            if (device.networkTime != NetworkTime.Disabled) {
                featureLabels.add(context.getString(R.string.manage_device_network_time_verification_title))
            }

            if (device.considerRebootManipulation) {
                featureLabels.add(context.getString(R.string.manage_device_reboot_manipulation_title))
            }

            if (device.showDeviceConnected) {
                featureLabels.add(context.getString(R.string.manage_send_device_connected_title_short))
            }

            if (device.enableActivityLevelBlocking) {
                featureLabels.add(context.getString(R.string.manage_device_activity_level_blocking_title))
            }

            return if (featureLabels.isEmpty()) {
                context.getString(R.string.manage_device_feature_summary_none)
            } else {
                featureLabels.joinToString(separator = ", ")
            }
        }
    }

    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFeaturesFragmentArgs by lazy { ManageDeviceFeaturesFragmentArgs.fromBundle(arguments!!) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = ManageDeviceFeaturesFragmentBinding.inflate(inflater, container, false)

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        // handlers
        binding.handlers = object: ManageDeviceFeaturesFragmentHandlers {
            override fun changeNetworkTimeVerification(newValue: NetworkTime) {
                val device = deviceEntry.value

                if (device != null) {
                    if (device.networkTime != newValue) {
                        if (!auth.tryDispatchParentAction(
                                        UpdateNetworkTimeVerificationAction(
                                                deviceId = device.id,
                                                mode = newValue
                                        )
                                )) {
                            // revert it

                            binding.networkTimeVerification = device.networkTime
                        }
                    }
                }
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        // network time status
        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            } else {
                val now = RealTime.newInstance()
                logic.realTimeLogic.getRealTime(now)

                binding.networkTimeVerification = device.networkTime
            }
        })

        // handle reboot as manipulation
        ManageDeviceRebootManipulationView.bind(
                view = binding.deviceRebootManipulation,
                lifecycleOwner = this,
                deviceEntry = deviceEntry,
                auth = auth
        )

        // activity level blocking
        ManageDeviceActivityLevelBlocking.bind(
                view = binding.activityLevelBlocking,
                auth = auth,
                deviceEntry = deviceEntry,
                lifecycleOwner = this
        )

        // send connected
        ManageSendDeviceConnected.bind(
                binding = binding.sendDeviceConnected,
                deviceEntry = deviceEntry,
                auth = auth,
                lifecycleOwner = this
        )


        return binding.root
    }

    override fun getCustomTitle() = deviceEntry.map { it?.name }
}

interface ManageDeviceFeaturesFragmentHandlers {
    fun changeNetworkTimeVerification(newValue: NetworkTime)
    fun showAuthenticationScreen()
}
