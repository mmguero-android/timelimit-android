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

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.apply.ApplyActionParentDeviceAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.ui.main.ActivityViewModel

class RemoveDeviceModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "RemoveDeviceModel"
    }

    private val isDoneInternal = MutableLiveData<Boolean>().apply { value = false }
    private var hasStarted = false
    private val logic = DefaultAppLogic.with(application)
    val isDone = isDoneInternal.castDown()

    fun start(deviceId: String, activityViewModel: ActivityViewModel) {
        if (hasStarted) {
            return
        }

        hasStarted = true

        runAsync {
            try {
                val server = logic.serverLogic.getServerConfigCoroutine()

                if (!server.hasAuthToken) {
                    Toast.makeText(getApplication(), R.string.remove_device_local_mode, Toast.LENGTH_LONG).show()
                } else {
                    val parent = activityViewModel.authenticatedUser.value?.first

                    if (parent != null) {
                        try {
                            when (parent) {
                                ApplyActionParentDeviceAuthentication -> server.api.removeDevice(
                                        deviceAuthToken = server.deviceAuthToken,
                                        parentUserId = "",
                                        parentPasswordSecondHash = "device",
                                        deviceId = deviceId
                                )
                                is ApplyActionParentPasswordAuthentication -> server.api.removeDevice(
                                        deviceAuthToken = server.deviceAuthToken,
                                        parentUserId = parent.parentUserId,
                                        parentPasswordSecondHash = parent.secondPasswordHash,
                                        deviceId = deviceId
                                )
                            }
                        } catch (ex: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.w(LOG_TAG, "removing device failed", ex)
                            }

                            Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        activityViewModel.requestAuthentication()
                    }
                }
            } finally {
                isDoneInternal.value = true
            }
        }
    }
}
