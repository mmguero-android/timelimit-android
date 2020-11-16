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
package io.timelimit.android.ui.setup.customserver

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
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.api.HttpError
import java.io.IOException

class SelectCustomServerModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "SelectCustomServerModel"
    }

    private val logic = DefaultAppLogic.with(application)
    private val statusInternal = MutableLiveData<SelectCustomServerStatus>().apply { value = SelectCustomServerStatus.Idle }

    val status = statusInternal.castDown()

    fun checkAndSave(url: String) {
        statusInternal.value = SelectCustomServerStatus.Working

        runAsync {
            try {
                if (url.isNotEmpty()) {
                    logic.serverCreator(url).getTimeInMillis()
                }

                saveUrl(url)

                statusInternal.value = SelectCustomServerStatus.Done
            } catch (ex: Exception) {
                statusInternal.value = SelectCustomServerStatus.Idle

                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "can not set custom server", ex)
                }

                val messageResource = when (ex) {
                    is HttpError -> R.string.custom_server_select_test_failed
                    is IOException -> R.string.error_network
                    else -> R.string.error_general
                }

                val message = getApplication<Application>().getString(messageResource)

                Toast.makeText(getApplication(), message + "\n" + ex.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveUrl(url: String) {
        Threads.database.executeAndWait {
            if (logic.database.config().getOwnDeviceIdSync() != null) {
                throw IllegalStateException("already configured")
            }

            logic.database.config().setCustomServerUrlSync(url)
        }
    }
}

enum class SelectCustomServerStatus {
    Idle, Working, Done
}