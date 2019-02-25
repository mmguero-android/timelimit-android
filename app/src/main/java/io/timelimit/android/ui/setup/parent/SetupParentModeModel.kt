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
package io.timelimit.android.ui.setup.parent

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.jaredrummler.android.device.DeviceName
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.data.transaction
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.ApplyServerDataStatus
import io.timelimit.android.sync.network.ClientDataStatus
import io.timelimit.android.sync.network.NewDeviceInfo
import io.timelimit.android.sync.network.ParentPassword
import io.timelimit.android.sync.network.StatusOfMailAddressResponse
import io.timelimit.android.sync.network.api.ConflictHttpError
import io.timelimit.android.sync.network.api.UnauthorizedHttpError

class SetupParentModeModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

    private val mailAuthTokenInternal = MutableLiveData<String?>().apply { value = null }
    private val statusOfMailAddressInternal = MutableLiveData<StatusOfMailAddressResponse?>().apply { value = null }
    private val isDoingSetupInternal = MutableLiveData<Boolean>().apply { value = false }

    val mailAuthToken = mailAuthTokenInternal.castDown()
    val statusOfMailAddress = statusOfMailAddressInternal.castDown()
    val isDoingSetup = isDoingSetupInternal.castDown()
    val isSetupDone = logic.database.config().getOwnDeviceId().map { it != null }

    fun setMailToken(mailAuthToken: String) {
        if (this.mailAuthTokenInternal.value == null) {
            this.mailAuthTokenInternal.value = mailAuthToken
            this.statusOfMailAddressInternal.value = null

            runAsync {
                try {
                    val api = logic.serverLogic.getServerConfigCoroutine().api
                    val status = api.getStatusByMailToken(mailAuthToken)

                    statusOfMailAddressInternal.value = status
                } catch (ex: Exception) {
                    Toast.makeText(getApplication(), R.string.error_network, Toast.LENGTH_SHORT).show()

                    mailAuthTokenInternal.value = null
                }
            }
        }
    }

    fun createFamily(parentPassword: String, parentName: String, deviceName: String) {
        val database = logic.database

        if (isDoingSetup.value!!) {
            return
        }

        isDoingSetupInternal.value = true

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api

                val registerResponse = api.createFamilyByMailToken(
                        mailToken = mailAuthToken.value!!,
                        parentPassword = ParentPassword.createCoroutine(parentPassword),
                        parentDevice = NewDeviceInfo(
                                model = DeviceName.getDeviceName()
                        ),
                        deviceName = deviceName,
                        parentName = parentName,
                        timeZone = logic.timeApi.getSystemTimeZone().id
                )

                val clientStatusResponse = api.pullChanges(registerResponse.deviceAuthToken, ClientDataStatus.empty)

                Threads.database.executeAndWait {
                    logic.database.transaction().use { transaction ->
                        val customServerUrl = logic.database.config().getCustomServerUrlSync()

                        database.deleteAllData()

                        database.config().setCustomServerUrlSync(customServerUrl)
                        database.config().setOwnDeviceIdSync(registerResponse.ownDeviceId)
                        database.config().setDeviceAuthTokenSync(registerResponse.deviceAuthToken)

                        ApplyServerDataStatus.applyServerDataStatusSync(clientStatusResponse, logic.database, logic.platformIntegration)

                        transaction.setSuccess()
                    }
                }

                DatabaseBackup.with(getApplication()).tryCreateDatabaseBackupAsync()

                // the fragment detects the success and leaves this screen
            } catch (ex: ConflictHttpError) {
                mailAuthTokenInternal.value = null
                isDoingSetupInternal.value = false

                Toast.makeText(getApplication(), R.string.error_server_rejected, Toast.LENGTH_SHORT).show()
            } catch (ex: UnauthorizedHttpError) {
                isDoingSetupInternal.value = false
                mailAuthTokenInternal.value = null

                Toast.makeText(getApplication(), R.string.error_server_rejected, Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                isDoingSetupInternal.value = false

                Toast.makeText(getApplication(), R.string.error_network, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addDeviceToFamily(deviceName: String) {
        val database = logic.database

        if (isDoingSetup.value!!) {
            return
        }

        isDoingSetupInternal.value = true

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api

                val registerResponse = api.signInToFamilyByMailToken(
                        mailToken = mailAuthToken.value!!,
                        parentDevice = NewDeviceInfo(
                                model = DeviceName.getDeviceName()
                        ),
                        deviceName = deviceName
                )

                val clientStatusResponse = api.pullChanges(registerResponse.deviceAuthToken, ClientDataStatus.empty)

                Threads.database.executeAndWait {
                    logic.database.transaction().use { transaction ->

                        database.deleteAllData()
                        database.config().setOwnDeviceIdSync(registerResponse.ownDeviceId)
                        database.config().setDeviceAuthTokenSync(registerResponse.deviceAuthToken)

                        ApplyServerDataStatus.applyServerDataStatusSync(clientStatusResponse, logic.database, logic.platformIntegration)

                        transaction.setSuccess()
                    }
                }

                DatabaseBackup.with(getApplication()).tryCreateDatabaseBackupAsync()

                // the fragment detects the success and leaves this screen
            } catch (ex: ConflictHttpError) {
                isDoingSetupInternal.value = false
                mailAuthTokenInternal.value = null

                Toast.makeText(getApplication(), R.string.error_server_rejected, Toast.LENGTH_SHORT).show()
            } catch (ex: UnauthorizedHttpError) {
                isDoingSetupInternal.value = false
                mailAuthTokenInternal.value = null

                Toast.makeText(getApplication(), R.string.error_server_rejected, Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                isDoingSetupInternal.value = false

                Toast.makeText(getApplication(), R.string.error_network, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
