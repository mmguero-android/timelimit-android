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
package io.timelimit.android.ui.manage.device.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.livedata.waitUntilValueMatches
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.apply.ApplyActionChildAddLimitAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentDeviceAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.RuntimeException

class AddDeviceModel(application: Application): AndroidViewModel(application) {
    private val statusInternal = MutableLiveData<Status>()
    private val logic = DefaultAppLogic.with(application)
    private var model: ActivityViewModel? = null
    private val currentDeviceList = logic.database.device().getAllDevicesLive()
    private val job = Job()
    private var initialized = false

    val status = statusInternal.castDown()

    fun init(model: ActivityViewModel) {
        if (this.model != null) {
            return
        }

        this.model = model

        if (!initialized) {
            initialized = true
            tryQuery()
        }
    }

    private fun tryQuery() {
        runAsync {
            statusInternal.value = Initializing

            val server = logic.serverLogic.getServerConfigCoroutine()
            val user = model!!.authenticatedUser.value

            if ((!server.hasAuthToken) || user == null) {
                statusInternal.value = Failed
            } else {
                try {
                    val auth = user.first

                    val response = when (auth) {
                        ApplyActionParentDeviceAuthentication -> server.api.createAddDeviceToken(
                                deviceAuthToken = server.deviceAuthToken,
                                parentUserId = "",
                                parentPasswordSecondHash = "device"
                        )
                        is ApplyActionParentPasswordAuthentication -> server.api.createAddDeviceToken(
                                deviceAuthToken = server.deviceAuthToken,
                                parentUserId = auth.parentUserId,
                                parentPasswordSecondHash = auth.secondPasswordHash
                        )
                        is ApplyActionChildAddLimitAuthentication -> throw RuntimeException("child can not do that")
                    }

                    statusInternal.value = ShowingToken(response.token)

                    withContext(job) {
                        val initialDeviceList = currentDeviceList.waitForNonNullValue()
                        val initialDeviceIdList = initialDeviceList.map { it.id }.toSet()

                        // the dialog is hidden after 5 minutes
                        val newDeviceList = withTimeoutOrNull(1000 * 60 * 5) {
                            currentDeviceList.waitUntilValueMatches { deviceList ->
                                deviceList?.find { !initialDeviceIdList.contains(it.id) } != null
                            }
                        } ?: emptyList()

                        val newDevices = newDeviceList.filterNot { initialDeviceIdList.contains(it.id) }
                        val newDevice = newDevices.firstOrNull()

                        if (newDevice == null) {
                            statusInternal.value = TokenExpired
                        } else {
                            statusInternal.value = DidAddDevice(newDevice.name)
                        }
                    }
                } catch (ex: Exception) {
                    statusInternal.value = Failed
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        job.cancel()
    }
}

sealed class Status
object Initializing: Status()
object Failed: Status()
data class ShowingToken(val token: String): Status()
data class DidAddDevice(val deviceName: String): Status()
object TokenExpired: Status()
