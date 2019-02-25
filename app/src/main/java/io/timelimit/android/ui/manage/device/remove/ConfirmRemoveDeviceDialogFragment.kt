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
package io.timelimit.android.ui.manage.device.remove

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.util.ConfirmDeleteDialogFragment

class ConfirmRemoveDeviceDialogFragment: ConfirmDeleteDialogFragment() {
    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val DIALOG_TAG = "ConfirmRemoveDeviceDialogFragment"

        fun newInstance(deviceId: String) = ConfirmRemoveDeviceDialogFragment().apply {
            arguments = Bundle().apply {
                putString(DEVICE_ID, deviceId)
            }
        }
    }

    private val deviceId: String by lazy { arguments!!.getString(DEVICE_ID) }
    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val deviceEntry: LiveData<Device?> by lazy {
        appLogic.database.device().getDeviceById(deviceId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        deviceEntry.observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceEntry.observe(this, Observer {
            binding.text = getString(R.string.remove_device_confirmation, it?.name)
        })
    }

    override fun onConfirmDeletion() {
        RemoveDeviceProgressDialog
                .newInstance(deviceId)
                .show(fragmentManager!!)

        dismiss()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}