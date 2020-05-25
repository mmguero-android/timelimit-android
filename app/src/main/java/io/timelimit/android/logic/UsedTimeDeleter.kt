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
package io.timelimit.android.logic

import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.data.transaction
import io.timelimit.android.date.DateInTimezone

object UsedTimeDeleter {
    fun deleteOldUsedTimeItems(database: Database, date: DateInTimezone, timestamp: Long) {
        Threads.database.execute {
            database.transaction().use {
                if (database.config().getDeviceAuthTokenSync().isNotEmpty()) {
                    // using the connected mode
                    // => server does the deletion
                    return@execute
                }

                if (database.config().getOwnDeviceIdSync() == null) {
                    // not configured
                    // => no need to delete anything
                    return@execute
                }

                database.usedTimes().deleteOldUsedTimeItems(lastDayToKeep = date.dayOfEpoch - date.dayOfWeek)

                database.sessionDuration().deleteOldSessionDurationItemsSync(trustedTimestamp = timestamp)

                it.setSuccess()
            }
        }
    }
}