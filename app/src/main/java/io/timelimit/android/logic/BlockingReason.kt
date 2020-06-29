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
package io.timelimit.android.logic

import androidx.lifecycle.LiveData
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.livedata.*
import java.util.*

enum class BlockingReason {
    None,
    NotPartOfAnCategory,
    TemporarilyBlocked,
    BlockedAtThisTime,
    TimeOver,
    TimeOverExtraTimeCanBeUsedLater,
    MissingNetworkTime,
    RequiresCurrentDevice,
    NotificationsAreBlocked,
    BatteryLimit,
    SessionDurationLimit
}

enum class BlockingLevel {
    App,
    Activity
}


class BlockingReasonUtil(private val appLogic: AppLogic) {
    fun getTrustedMinuteOfWeekLive(timeZone: TimeZone): LiveData<Int?> {
        val realTime = RealTime.newInstance()

        return object: LiveData<Int?>() {
            fun update() {
                appLogic.realTimeLogic.getRealTime(realTime)

                if (realTime.shouldTrustTimeTemporarily) {
                    value = getMinuteOfWeek(realTime.timeInMillis, timeZone)
                } else {
                    value = null
                }
            }

            init {
                update()
            }

            val scheduledUpdateRunnable = Runnable {
                update()
                scheduleUpdate()
            }

            fun scheduleUpdate() {
                appLogic.timeApi.runDelayed(scheduledUpdateRunnable, 1000L /* every second */)
            }

            fun cancelScheduledUpdate() {
                appLogic.timeApi.cancelScheduledAction(scheduledUpdateRunnable)
            }

            override fun onActive() {
                super.onActive()

                update()
                scheduleUpdate()
            }

            override fun onInactive() {
                super.onInactive()

                cancelScheduledUpdate()
            }
        }.ignoreUnchanged()
    }
}
