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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.update.UpdateIntegration
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic

class DownloadUpdateDialogModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "DownloadUpdateModel"
    }

    private val statusInternal = MutableLiveData<Status>()
    val status = statusInternal.castDown()

    init {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "init")
        }

        val database = DefaultAppLogic.with(application).database

        runAsync {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "start process")
            }

            try {
                statusInternal.value = Status.Working

                val liveStatus = UpdateIntegration.getUpdateStatus(application)
                val storageStatus = Threads.update.executeAndWait { database.config().getUpdateStatusSync() }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got status")
                }

                if (liveStatus != storageStatus) {
                    Threads.update.executeAndWait { database.config().setUpdateStatus(liveStatus) }

                    statusInternal.value = Status.FailureHadChange

                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "starting download")
                }

                UpdateIntegration.downloadAndVerifyUpdate(liveStatus, application)

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "done")
                }

                statusInternal.value = Status.Success
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "error occured", ex)
                }

                statusInternal.value = Status.Failure
            }
        }
    }

    enum class Status {
        Working, FailureHadChange, Failure, Success
    }
}