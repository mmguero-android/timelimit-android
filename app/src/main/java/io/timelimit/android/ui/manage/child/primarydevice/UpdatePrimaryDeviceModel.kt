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

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.livedata.waitUntilValueMatches
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequest
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.sync.network.UpdatePrimaryDeviceResponseType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException

class UpdatePrimaryDeviceModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "UpdatePrimaryDevice"

        suspend fun unsetPrimaryDeviceInBackground(logic: AppLogic) {
            logic.syncUtil.requestImportantSyncAndWait()

            val ownDeviceId = logic.database.config().getOwnDeviceId().waitForNullableValue()!!
            val ownDeviceEntry = logic.database.device().getDeviceById(ownDeviceId).waitForNullableValue()!!
            val server = logic.serverLogic.getServerConfigCoroutine()

            if (!server.hasAuthToken) {
                throw IOException("has not auth token")
            }

            val response = server.api.updatePrimaryDevice(UpdatePrimaryDeviceRequest(
                    action = UpdatePrimaryDeviceRequestType.UnsetThisDevice,
                    currentUserId = ownDeviceEntry.currentUserId,
                    deviceAuthToken = server.deviceAuthToken
            ))

            if (response.status != UpdatePrimaryDeviceResponseType.Success) {
                throw IOException("server reported ${response.status}")
            }

            Threads.database.executeAndWait {
                logic.database.runInTransaction {
                    val userEntry = logic.database.user().getUserByIdSync(ownDeviceEntry.currentUserId)!!

                    logic.database.user().updateUserSync(userEntry.copy(currentDevice = ""))
                }
            }
        }
    }

    private val logic = DefaultAppLogic.with(application)
    private val job = Job()
    private val otherAssignedDevice = logic.currentDeviceLogic.otherAssignedDevice

    private val statusInternal = MutableLiveData<UpdatePrimaryDeviceStatus>()
    private var hasStarted = false

    val status = statusInternal.castDown()

    fun start(type: UpdatePrimaryDeviceRequestType) {
        if (hasStarted) {
            return
        }

        hasStarted = true

        runAsync {
            val server = logic.serverLogic.getServerConfigCoroutine()

            if (!server.hasAuthToken) {
                statusInternal.value = FailedGeneral
                return@runAsync
            }

            withContext(job) {
                suspend fun forceSync() {
                    statusInternal.value = WaitingForSync
                    logic.syncUtil.requestImportantSyncAndWait()
                }

                suspend fun tryToDoAction() {
                    try {
                        statusInternal.value = Updating

                        val ownDeviceId = logic.database.config().getOwnDeviceId().waitForNullableValue()!!
                        val ownDeviceEntry = logic.database.device().getDeviceById(ownDeviceId).waitForNullableValue()!!

                        val response = server.api.updatePrimaryDevice(UpdatePrimaryDeviceRequest(
                                action = type,
                                currentUserId = ownDeviceEntry.currentUserId,
                                deviceAuthToken = server.deviceAuthToken
                        ))

                        if (response.status == UpdatePrimaryDeviceResponseType.Success) {
                            Threads.database.executeAndWait {
                                logic.database.runInTransaction {
                                    val userEntry = logic.database.user().getUserByIdSync(ownDeviceEntry.currentUserId)!!

                                    logic.database.user().updateUserSync(
                                            userEntry.copy(
                                                    currentDevice = when (type) {
                                                        UpdatePrimaryDeviceRequestType.SetThisDevice -> ownDeviceId
                                                        UpdatePrimaryDeviceRequestType.UnsetThisDevice -> ""
                                                    }
                                            )
                                    )
                                }
                            }

                            Toast.makeText(getApplication(), R.string.update_primary_device_toast_success, Toast.LENGTH_SHORT).show()
                            statusInternal.value = Success
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.w(LOG_TAG, "failed to set primary device, server reported ${response.status}")
                            }

                            statusInternal.value = when (response.status) {
                                UpdatePrimaryDeviceResponseType.RequiresFullVersion -> FailedPremium
                                UpdatePrimaryDeviceResponseType.AssignedToOtherDevice -> FailedUserAssignedToOtherDevice(
                                        otherAssignedDevice.waitForNullableValue()?.name
                                )
                                else -> FailedGeneral
                            }
                        }
                    } catch (ex: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.w(LOG_TAG, "failed to set primary device", ex)
                        }

                        statusInternal.value = FailedGeneral
                    }
                }

                while (true) {
                    forceSync()
                    tryToDoAction()

                    if (statusInternal.value is FailedUserAssignedToOtherDevice) {
                        // retry when it changed
                        otherAssignedDevice.waitUntilValueMatches { it != null }

                        // this prevents that the current device is shown as null sometimes
                        statusInternal.value = FailedUserAssignedToOtherDevice(
                                otherAssignedDevice.waitForNullableValue()?.name
                        )

                        // send sign out requests
                        val signOutRequestJob = Job()

                        try {
                            runAsync {
                                withContext(signOutRequestJob) {
                                    while (this.isActive) {
                                        if (BuildConfig.DEBUG) {
                                            Log.d(LOG_TAG, "send sign out request")
                                        }

                                        try {
                                            server.api.requestSignOutAtPrimaryDevice(
                                                    deviceAuthToken = server.deviceAuthToken
                                            )
                                        } catch (ex: Exception) {
                                            if (BuildConfig.DEBUG) {
                                                Log.w(LOG_TAG, "sending sign out request failed", ex)
                                            }
                                        }

                                        delay(5000) // wait 5 seconds
                                    }
                                }
                            }

                            otherAssignedDevice.waitUntilValueMatches { it == null }
                        } finally {
                            signOutRequestJob.cancel()
                        }
                    } else {
                        break
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        job.cancel()
    }
}

sealed class UpdatePrimaryDeviceStatus()
object WaitingForSync: UpdatePrimaryDeviceStatus()
object Updating: UpdatePrimaryDeviceStatus()
object Success: UpdatePrimaryDeviceStatus()
object FailedGeneral: UpdatePrimaryDeviceStatus()
object FailedPremium: UpdatePrimaryDeviceStatus()
class FailedUserAssignedToOtherDevice(val deviceTitle: String?): UpdatePrimaryDeviceStatus()
