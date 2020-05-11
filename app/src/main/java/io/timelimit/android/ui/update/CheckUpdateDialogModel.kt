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
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.update.UpdateUtil

class CheckUpdateDialogModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "CheckUpdateDialogModel"
    }

    private val statusInternal = MutableLiveData<Status>()
    val status = statusInternal.castDown()

    init {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "init")
        }

        val database = DefaultAppLogic.with(application).database

        runAsync {
            statusInternal.value = Status.Working

            try {
                if (UpdateUtil.doUpdateCheck(application, database, enableNotifications = false)) {
                    statusInternal.value = Status.SuccessUpdateAvailable
                } else {
                    statusInternal.value = Status.SuccessNoNewUpdate
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not check for updates", ex)
                }

                statusInternal.value = Status.Failure
            }
        }
    }

    enum class Status {
        Working, Failure, SuccessNoNewUpdate, SuccessUpdateAvailable
    }
}