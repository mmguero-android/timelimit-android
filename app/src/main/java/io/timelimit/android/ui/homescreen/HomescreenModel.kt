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
package io.timelimit.android.ui.homescreen

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import kotlinx.coroutines.delay

class HomescreenModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val DELAY = 5000L                  // 5 seconds

        private const val COUNTER_DURATION = 1000 * 60L  // 1 minute
        private const val COUNTER_MIN = 20               // 20 times

        private var counter = 0
        private var hadFirstRunAfterLaunch = false
    }

    private val logic = DefaultAppLogic.with(application)
    private val statusInternal = MutableLiveData<HomescreenStatus>()
    private var isHandlingLaunch = false
    private var hadInitialLaunchHandling = false

    val status = statusInternal.castDown()

    fun handleLaunchIfNotYetExecuted(forceSelection: Boolean) {
        if (hadInitialLaunchHandling) {
            return
        }

        hadInitialLaunchHandling = true

        handleLaunch(forceSelection)
    }

    fun handleLaunch(forceSelection: Boolean) {
        if (isHandlingLaunch) {
            return
        }

        isHandlingLaunch = true

        runAsync {
            val enableDelay = Threads.database.executeAndWait {
                logic.database.config().isExperimentalFlagsSetSync(ExperimentalFlags.CUSTOM_HOMESCREEN_DELAY)
            }

            if (enableDelay && (!hadFirstRunAfterLaunch)) {
                val timeApi = logic.timeApi
                val start = timeApi.getCurrentUptimeInMillis()
                val end = start + DELAY

                while (true) {
                    val now = timeApi.getCurrentUptimeInMillis()
                    val progress = (now - start) * 100L / DELAY

                    if (now >= end) {
                        break
                    }

                    statusInternal.value = DelayHomescreenStatus(progress.toInt())

                    delay(50)
                }
            }

            hadFirstRunAfterLaunch = true

            if (forceSelection || counter >= COUNTER_MIN) {
                showSelectionList()
            } else {
                runAsync {
                    counter++

                    delay(COUNTER_DURATION)

                    if (counter > 0) {
                        counter--
                    }
                }

                val defaultHomescreen = Threads.database.executeAndWait { logic.database.config().getDefaultHomescreenSync() }

                if (defaultHomescreen != null) {
                    statusInternal.value = TryLaunchHomescreenStatus(defaultHomescreen, false)
                } else {
                    showSelectionList()
                }
            }

            isHandlingLaunch = false
        }
    }

    fun showSelectionList() {
        statusInternal.value = SelectionListHomescreenStatus
    }

    fun saveDefaultOption(componentName: ComponentName) {
        counter = 0

        Threads.database.execute { logic.database.config().setDefaultHomescreenSync(componentName) }
    }
}

sealed class HomescreenStatus
object SelectionListHomescreenStatus: HomescreenStatus()
class TryLaunchHomescreenStatus(val component: ComponentName, var didTry: Boolean): HomescreenStatus()
class DelayHomescreenStatus(val progress: Int): HomescreenStatus()