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
package io.timelimit.android.ui.migrate_to_connected

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
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.ApplyServerDataStatus
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.sync.network.ClientDataStatus
import io.timelimit.android.sync.network.NewDeviceInfo
import io.timelimit.android.sync.network.ParentPassword
import io.timelimit.android.sync.network.StatusOfMailAddress
import io.timelimit.android.sync.network.api.HttpError
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MigrateToConnectedModeModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "MigrateToConnected"
    }

    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database
    private var mailAuthToken: String? = null
    private val lock = Mutex()
    private val statusInternal = MutableLiveData<MigrateToConnectedModeStatus>().apply {
        value = WaitingForAuthMigrationStatus
    }

    val status = statusInternal.castDown()

    fun onLoginSucceeded(mailAuthToken: String) {
        this.mailAuthToken = mailAuthToken

        runAsync {
            lock.withLock {
                try {
                    statusInternal.value = WorkingMigrationStatus

                    val api = logic.serverLogic.getServerConfigCoroutine().api

                    val status = api.getStatusByMailToken(mailAuthToken)

                    when (status.status) {
                        StatusOfMailAddress.MailAddressWithFamily -> statusInternal.value = ConflictAlreadyHasAccountMigrationStatus
                        StatusOfMailAddress.MailAddressWithoutFamily -> {
                            statusInternal.value = if (status.canCreateFamily)
                                WaitingForConfirmationByParentMigrationStatus
                            else
                                SingupDisabledMigrationStatus
                        }
                    }.let { /* require handling all paths */ }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "error during checking mail", ex)
                    }

                    Toast.makeText(
                            getApplication(),
                            if (ex is HttpError)
                                R.string.error_server_rejected
                            else
                                R.string.error_network,
                            Toast.LENGTH_SHORT
                    ).show()

                    statusInternal.value = LeaveScreenMigrationStatus
                }
            }
        }
    }

    fun doMigration(
            model: ActivityViewModel,
            parentFirstName: String
    ) {
        runAsync {
            lock.withLock {
                try {
                    statusInternal.value = WorkingMigrationStatus

                    if (!model.isParentAuthenticated()) {
                        throw IllegalStateException()
                    }

                    val auth = model.getAuthenticatedUser()!!

                    val currentConfig = Threads.database.executeAndWait {
                        database.runInTransaction {
                            // check if not yet linked
                            if (database.config().getDeviceAuthTokenSync() != "") {
                                throw IllegalStateException("already linked")
                            }

                            OfflineModeStatus.query(database)
                        }
                    }

                    // create family at server
                    val server = logic.serverLogic.getServerConfigCoroutine()

                    val addDeviceResponse = server.api.createFamilyByMailToken(
                            mailToken = mailAuthToken!!,
                            parentPassword = ParentPassword(
                                    parentPasswordHash = auth.firstPasswordHash,
                                    parentPasswordSecondHash = auth.secondPasswordHash,
                                    parentPasswordSecondSalt = currentConfig.users.find { it.id == auth.userId }!!.secondPasswordSalt
                            ),
                            parentDevice = NewDeviceInfo(model = currentConfig.device.model),
                            deviceName = currentConfig.device.name,
                            parentName = parentFirstName,
                            timeZone = logic.timeApi.getSystemTimeZone().id
                    )

                    // sync from server
                    val clientStatusResponse = server.api.pullChanges(addDeviceResponse.deviceAuthToken, ClientDataStatus.empty)

                    val authentication = Threads.database.executeAndWait {
                        logic.database.runInTransaction {
                            val customServerUrl = logic.database.config().getCustomServerUrlSync()
                            val database = logic.database

                            database.deleteAllData()

                            database.config().setCustomServerUrlSync(customServerUrl)
                            database.config().setOwnDeviceIdSync(addDeviceResponse.ownDeviceId)
                            database.config().setDeviceAuthTokenSync(addDeviceResponse.deviceAuthToken)

                            ApplyServerDataStatus.applyServerDataStatusSync(clientStatusResponse, logic.database, logic.platformIntegration)

                            val newParentUser = database.user().getParentUsersSync().first()
                            val newParentUserAuth = ApplyActionParentPasswordAuthentication(
                                    parentUserId = newParentUser.id,
                                    secondPasswordHash = auth.secondPasswordHash
                            )

                            newParentUserAuth
                        }
                    }

                    currentConfig.apply(authentication, logic, addDeviceResponse.ownDeviceId)
                    DatabaseBackup.with(getApplication()).tryCreateDatabaseBackupAsync()

                    statusInternal.value = DoneMigrationStatus
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "error migration to connected mode", ex)
                    }

                    Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                    statusInternal.value = LeaveScreenMigrationStatus
                }
            }
        }
    }
}

sealed class MigrateToConnectedModeStatus
object WaitingForAuthMigrationStatus: MigrateToConnectedModeStatus()
object LeaveScreenMigrationStatus: MigrateToConnectedModeStatus()
object WaitingForConfirmationByParentMigrationStatus: MigrateToConnectedModeStatus()
object ConflictAlreadyHasAccountMigrationStatus: MigrateToConnectedModeStatus()
object WorkingMigrationStatus: MigrateToConnectedModeStatus()
object DoneMigrationStatus: MigrateToConnectedModeStatus()
object SingupDisabledMigrationStatus: MigrateToConnectedModeStatus()