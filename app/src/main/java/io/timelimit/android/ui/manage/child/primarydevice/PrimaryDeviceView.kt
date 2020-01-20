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
package io.timelimit.android.ui.manage.child.primarydevice

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.databinding.PrimaryDeviceViewBinding
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.SetRelaxPrimaryDeviceAction
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel

object PrimaryDeviceView {
    fun bind(
            view: PrimaryDeviceViewBinding,
            childId: String,
            logic: AppLogic,
            fragmentManager: FragmentManager,
            lifecycleOwner: LifecycleOwner,
            auth: ActivityViewModel
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.primary_device_title,
                    text = R.string.primary_device_description
            ).show(fragmentManager)
        }

        val userEntry = logic.database.user().getUserByIdLive(childId)
        val ownDeviceId = logic.deviceId
        val ownDeviceUser = logic.deviceUserId
        val childDevices = logic.database.device().getDevicesByUserId(childId)

        ownDeviceUser.observe(lifecycleOwner, Observer { view.canAssignThisDevice = it == childId && childId.isNotEmpty() })

        mergeLiveData(ownDeviceId, childDevices, userEntry, logic.fullVersion.isLocalMode).observe(lifecycleOwner, Observer {
            (ownDeviceId, childDevices, userEntry, isLocalMode) ->

            if (ownDeviceId != null && childDevices != null && userEntry != null && isLocalMode != null) {
                if (isLocalMode) {
                    view.status = PrimaryDeviceStatus.LocalMode
                } else {
                    val currentDeviceEntry = childDevices.find { device -> device.id == userEntry.currentDevice }

                    if (currentDeviceEntry == null) {
                        view.status = PrimaryDeviceStatus.NoDeviceSelected
                    } else if (currentDeviceEntry.id == ownDeviceId) {
                        view.status = PrimaryDeviceStatus.ThisDeviceSelected
                    } else {
                        view.status = PrimaryDeviceStatus.OtherDeviceSelected
                        view.primaryDeviceTitle = currentDeviceEntry.name
                    }
                }
            }
        })

        userEntry.observe(lifecycleOwner, Observer {
            val checkboxStatus = it?.relaxPrimaryDevice ?: false

            view.relaxPrimaryDeviceCheckbox.setOnCheckedChangeListener { _, _ ->  }
            view.relaxPrimaryDeviceCheckbox.isChecked = checkboxStatus
            view.relaxPrimaryDeviceCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != checkboxStatus) {
                    if (!auth.tryDispatchParentAction(
                                    SetRelaxPrimaryDeviceAction(
                                            userId = childId,
                                            relax = isChecked
                                    )
                            )) {
                        view.relaxPrimaryDeviceCheckbox.isChecked = checkboxStatus
                    }
                }
            }
        })

        view.btnAssign.setOnClickListener { _ ->
            UpdatePrimaryDeviceDialogFragment.newInstance(UpdatePrimaryDeviceRequestType.SetThisDevice).show(fragmentManager)
        }

        view.btnUnassign.setOnClickListener { _ ->
            UpdatePrimaryDeviceDialogFragment.newInstance(UpdatePrimaryDeviceRequestType.UnsetThisDevice).show(fragmentManager)
        }
    }
}

enum class PrimaryDeviceStatus {
    LocalMode,
    NoDeviceSelected,
    OtherDeviceSelected,
    ThisDeviceSelected
}
