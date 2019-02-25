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
package io.timelimit.android.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map

object IsAppInForeground {
    private const val LOG_TAG = "IsAppInForeground"
    private const val REPORT_INACTIVE_DELAY = 300L    // 0.3 seconds

    private val activeInstancesInternal = MutableLiveData<Int>().apply { value = 0 }
    private val isRunningInternal = activeInstancesInternal.map { it != 0 }.ignoreUnchanged()
    private val isRunningWithDelayInternal = object: LiveData<Boolean>() {
        private val reportNotRunning = Runnable {
            value = false
        }

        init {
            isRunningInternal.observeForever {
                if (it == true) {
                    Threads.mainThreadHandler.removeCallbacks(reportNotRunning)
                    value = true
                } else if (it == false) {
                    Threads.mainThreadHandler.postDelayed(reportNotRunning, REPORT_INACTIVE_DELAY)
                }
            }
        }
    }.ignoreUnchanged()
    val isRunning = isRunningWithDelayInternal.castDown()

    fun reportStart() {
        activeInstancesInternal.value = activeInstancesInternal.value!! + 1
    }

    fun reportStop() {
        activeInstancesInternal.value = activeInstancesInternal.value!! - 1

        if (activeInstancesInternal.value!! < 0) {
            throw IllegalStateException()
        }
    }

    fun isRunning() = activeInstancesInternal.value != 0

    init {
        if (BuildConfig.DEBUG) {
            isRunning.observeForever {
                Log.d(LOG_TAG, "isRunning = $it")
            }
        }
    }
}
