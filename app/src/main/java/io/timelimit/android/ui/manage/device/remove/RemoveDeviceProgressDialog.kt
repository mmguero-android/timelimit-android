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

import android.app.ProgressDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.ActivityViewModelHolder

class RemoveDeviceProgressDialog: DialogFragment() {
    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val DIALOG_TAG = "RemoveDeviceProgressDialog"

        fun newInstance(deviceId: String) = RemoveDeviceProgressDialog().apply {
            arguments = Bundle().apply {
                putString(DEVICE_ID, deviceId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = arguments!!.getString(DEVICE_ID)!!
        val model = ViewModelProviders.of(this).get(RemoveDeviceModel::class.java)
        val activityModel = (activity as ActivityViewModelHolder).getActivityViewModel()

        model.start(deviceId, activityModel)

        model.isDone.observe(this, Observer {
            dismissAllowingStateLoss()
        })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = ProgressDialog(context, theme).apply {
        setMessage(getString(R.string.remove_device_progress))
        isIndeterminate = true
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}
