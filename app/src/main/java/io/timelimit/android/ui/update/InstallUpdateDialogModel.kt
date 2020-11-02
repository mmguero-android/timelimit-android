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

package io.timelimit.android.ui.update

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.crypto.HexString
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.update.UpdateIntegration
import java.io.IOException
import java.security.MessageDigest

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class InstallUpdateDialogModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "InstallUpdateModel"
        private const val UPDATE_STATUS_NOTIFICATION = "update_status_notification"
    }

    private val statusInternal = MutableLiveData<Status>()
    val status = statusInternal.castDown()

    init {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "init")
        }

        Threads.update.submit {
            statusInternal.postValue(Status.Working)

            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    throw IllegalStateException()
                }

                val status = DefaultAppLogic.with(application).database.config().getUpdateStatusSync()!!
                val installer = application.packageManager.packageInstaller

                if (!UpdateIntegration.isDownloadedFileValidSync(status, getApplication())) {
                    statusInternal.postValue(Status.Failure(message = "update validation failed"))

                    return@submit
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "killing old session")
                }

                installer.mySessions.forEach { installer.abandonSession(it.sessionId) }

                val sessionId = installer.createSession(
                        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                            setAppPackageName(BuildConfig.APPLICATION_ID)
                        }
                )
                val session = installer.openSession(sessionId)
                val digest = MessageDigest.getInstance("SHA-512")
                val updateFile = UpdateIntegration.updateSaveFile(application)

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "loading file")
                }

                session.openWrite("update.apk", 0, updateFile.length()).use { writer ->
                    val buffer = ByteArray(1024 * 1024)

                    updateFile.inputStream().use { reader ->
                        while (true) {
                            val len = reader.read(buffer)

                            if (len < 0) {
                                break
                            }

                            digest.update(buffer, 0, len)
                            writer.write(buffer, 0, len)
                        }
                    }

                    session.fsync(writer)
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "checking hash")
                }

                if (!HexString.toHex(digest.digest()).equals(status.sha512, ignoreCase = true)) {
                    session.abandon()
                    throw IOException()
                }

                val token = IdGenerator.generateId()
                val action = UPDATE_STATUS_NOTIFICATION + token

                val receiver = object: BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action != action) { return }

                        val reportedStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

                        if (reportedStatus == PackageInstaller.STATUS_SUCCESS) {
                            application.unregisterReceiver(this)
                            statusInternal.postValue(Status.Success)
                        } else if (reportedStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            statusInternal.postValue(Status.NeedsConfirmation(intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!))
                        } else if (reportedStatus == PackageInstaller.STATUS_FAILURE_ABORTED) {
                            application.unregisterReceiver(this)
                            statusInternal.postValue(Status.Aborted)
                        } else {
                            val message = when (reportedStatus) {
                                PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
                                PackageInstaller.STATUS_FAILURE_CONFLICT -> "conflict"
                                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "incompatible"
                                PackageInstaller.STATUS_FAILURE_INVALID -> "invalid"
                                PackageInstaller.STATUS_FAILURE_STORAGE -> "storage"
                                else -> "other error"
                            }

                            application.unregisterReceiver(this)
                            statusInternal.postValue(Status.Failure(message))
                        }
                    }
                }

                application.registerReceiver(receiver, IntentFilter(action))

                session.commit(
                        PendingIntent.getBroadcast(
                                application,
                                PendingIntentIds.UPDATE_STATUS,
                                Intent(action).setPackage(BuildConfig.APPLICATION_ID),
                                0
                        ).intentSender
                )

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "commit")
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "update failed", ex)
                }

                statusInternal.postValue(Status.Failure(null))
            }
        }
    }

    sealed class Status {
        object Working: Status()
        class NeedsConfirmation(val intent: Intent, var didOpen: Boolean = false): Status()
        class Failure(val message: String?): Status()
        object Aborted: Status()
        object Success: Status()
    }
}