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
package io.timelimit.android.ui.setup.child

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.jaredrummler.android.device.DeviceName
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.ApplyServerDataStatus
import io.timelimit.android.sync.network.ClientDataStatus
import io.timelimit.android.sync.network.NewDeviceInfo
import io.timelimit.android.sync.network.api.UnauthorizedHttpError

class SetupRemoteChildViewModel(application: Application): AndroidViewModel(application) {
    private val statusInternal = MutableLiveData<SetupRemoteChildStatus>().apply { value = SetupRemoteChildStatus.Idle }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }

    val status = statusInternal.castDown()
    val isSetupDone = logic.database.config().getOwnDeviceId().map { it != null }

    fun trySetup(registerToken: String) {
        if (statusInternal.value != SetupRemoteChildStatus.Idle) {
            return
        }

        statusInternal.value = SetupRemoteChildStatus.Working

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api

                val registerResponse = api.registerChildDevice(
                        childDeviceInfo = NewDeviceInfo(model = DeviceName.getDeviceName()),
                        registerToken = registerToken,
                        deviceName = DeviceName.getDeviceName()
                )

                val clientStatusResponse = api.pullChanges(registerResponse.deviceAuthToken, ClientDataStatus.empty)

                Threads.database.executeAndWait {
                    logic.database.runInTransaction {
                        val customServerUrl = logic.database.config().getCustomServerUrlSync()

                        logic.database.deleteAllData()
                        logic.database.config().setCustomServerUrlSync(customServerUrl)
                        logic.database.config().setOwnDeviceIdSync(registerResponse.ownDeviceId)
                        logic.database.config().setDeviceAuthTokenSync(registerResponse.deviceAuthToken)

                        ApplyServerDataStatus.applyServerDataStatusSync(clientStatusResponse, logic.database, logic.platformIntegration)
                    }
                }

                DatabaseBackup.with(getApplication()).tryCreateDatabaseBackupAsync()
            } catch (ex: UnauthorizedHttpError) {
                statusInternal.value = SetupRemoteChildStatus.CodeInvalid
            } catch (ex: Exception) {
                statusInternal.value = SetupRemoteChildStatus.NetworkError
            }
        }
    }

    fun confirmError() {
        if (statusInternal.value == SetupRemoteChildStatus.NetworkError || statusInternal.value == SetupRemoteChildStatus.CodeInvalid) {
            statusInternal.value = SetupRemoteChildStatus.Idle
        }
    }
}

enum class SetupRemoteChildStatus {
    Idle, Working, NetworkError, CodeInvalid
}
