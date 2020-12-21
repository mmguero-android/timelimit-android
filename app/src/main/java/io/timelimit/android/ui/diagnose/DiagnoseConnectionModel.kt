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
package io.timelimit.android.ui.diagnose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.internal.wait

class DiagnoseConnectionModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

    private val statusInternal = MutableLiveData<ConnectionTestStatus>().apply { value = ConnectionTestStatus.Idle }
    val status = statusInternal.castDown()

    fun startConnectionTest() {
        if (statusInternal.value != ConnectionTestStatus.Idle) return

        statusInternal.value = ConnectionTestStatus.Running

        runAsync {
            statusInternal.value = try {
                val pause = launch { delay(250) }

                logic.serverLogic.getServerConfigCoroutine().api.getTimeInMillis()

                pause.join()

                ConnectionTestStatus.Success
            } catch (ex: Exception) {
                ConnectionTestStatus.Failure(ex)
            }
        }
    }

    fun confirmConnectionTestResult() {
        if (statusInternal.value != ConnectionTestStatus.Running)
            statusInternal.value = ConnectionTestStatus.Idle
    }

    sealed class ConnectionTestStatus {
        object Idle: ConnectionTestStatus()
        object Running: ConnectionTestStatus()
        object Success: ConnectionTestStatus()
        class Failure(val ex: Exception): ConnectionTestStatus()
    }
}