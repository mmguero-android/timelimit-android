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
package io.timelimit.android.ui.manage.device.manage.advanced

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.ManageDeviceBackgroundSyncViewBinding
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.work.PeriodicSyncInBackgroundWorker

object ManageDeviceBackgroundSync {
    fun bind(
            view: ManageDeviceBackgroundSyncViewBinding,
            isThisDevice: LiveData<Boolean>,
            lifecycleOwner: LifecycleOwner,
            activityViewModel: ActivityViewModel,
            fragmentManager: FragmentManager
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.device_background_sync_title,
                    text = R.string.device_background_sync_text
            ).show(fragmentManager)
        }

        isThisDevice.observe(lifecycleOwner, Observer { view.isThisDevice = it })
        activityViewModel.logic.fullVersion.isLocalMode.observe(lifecycleOwner, Observer { view.isUsingLocalMode = it })

        activityViewModel.logic.database.config().getEnableBackgroundSyncAsync().observe(lifecycleOwner, Observer { enable ->
            view.checkbox.setOnCheckedChangeListener { _, _ ->  }
            view.checkbox.isChecked = enable
            view.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != enable) {
                    if (activityViewModel.requestAuthenticationOrReturnTrue()) {
                        Threads.database.execute {
                            activityViewModel.logic.database.config().setEnableBackgroundSync(isChecked)
                        }

                        // for some reason, the observing of the config value does not work correctly -> do it manually here
                        if (isChecked) {
                            PeriodicSyncInBackgroundWorker.enable()
                        } else {
                            PeriodicSyncInBackgroundWorker.disable()
                        }
                    } else {
                        view.checkbox.isChecked = enable
                    }
                }
            }
        })
    }
}