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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.data.model.UsedTimeListItem
import io.timelimit.android.livedata.ignoreUnchanged

@Dao
abstract class UsedTimeDao {
    @Query("SELECT * FROM used_time WHERE category_id = :categoryId AND day_of_epoch >= :startingDayOfEpoch AND day_of_epoch <= :endDayOfEpoch")
    protected abstract fun getUsedTimesOfWeekInternal(categoryId: String, startingDayOfEpoch: Int, endDayOfEpoch: Int): LiveData<List<UsedTimeItem>>

    fun getUsedTimesOfWeek(categoryId: String, firstDayOfWeekAsEpochDay: Int): LiveData<List<UsedTimeItem>> {
        return getUsedTimesOfWeekInternal(categoryId, firstDayOfWeekAsEpochDay, firstDayOfWeekAsEpochDay + 6).ignoreUnchanged()
    }

    @Insert
    abstract fun insertUsedTime(item: UsedTimeItem)

    @Insert
    abstract fun insertUsedTimes(item: List<UsedTimeItem>)

    @Query("UPDATE used_time SET used_time = used_time + :timeToAdd WHERE category_id = :categoryId AND day_of_epoch = :dayOfEpoch AND start_time_of_day = :start AND end_time_of_day = :end")
    abstract fun addUsedTime(categoryId: String, dayOfEpoch: Int, timeToAdd: Int, start: Int, end: Int): Int

    @Query("SELECT * FROM used_time WHERE category_id = :categoryId AND day_of_epoch = :dayOfEpoch AND start_time_of_day = :start AND end_time_of_day = :end")
    abstract fun getUsedTimeItemSync(categoryId: String, dayOfEpoch: Int, start: Int, end: Int): UsedTimeItem?

    @Query("DELETE FROM used_time WHERE category_id = :categoryId")
    abstract fun deleteUsedTimeItems(categoryId: String)

    @Query("DELETE FROM used_time WHERE day_of_epoch < :lastDayToKeep")
    abstract fun deleteOldUsedTimeItems(lastDayToKeep: Int)

    @Query("SELECT * FROM used_time LIMIT :pageSize OFFSET :offset")
    abstract fun getUsedTimePageSync(offset: Int, pageSize: Int): List<UsedTimeItem>

    @Query("SELECT * FROM used_time WHERE category_id IN (:categoryIds) AND day_of_epoch >= :startingDayOfEpoch AND day_of_epoch <= :endDayOfEpoch")
    abstract fun getUsedTimesByDayAndCategoryIds(categoryIds: List<String>, startingDayOfEpoch: Int, endDayOfEpoch: Int): LiveData<List<UsedTimeItem>>

    @Query("SELECT * FROM used_time WHERE category_id = :categoryId")
    abstract fun getUsedTimeItemsByCategoryId(categoryId: String): List<UsedTimeItem>

    @Query("SELECT * FROM used_time")
    abstract fun getAllUsedTimeItemsSync(): List<UsedTimeItem>

    // breaking it into multiple lines causes issues during compilation ...
    @Query("SELECT 2 AS type, start_time_of_day AS startMinuteOfDay, end_time_of_day AS endMinuteOfDay, used_time AS duration, day_of_epoch AS day, NULL AS lastUsage, NULL AS maxSessionDuration, NULL AS pauseDuration FROM used_time WHERE category_id = :categoryId UNION ALL SELECT 1 AS type, start_minute_of_day AS startMinuteOfDay, end_minute_of_day AS endMinuteOfDay, last_session_duration AS duration, NULL AS day, last_usage AS lastUsage, max_session_duration AS maxSessionDuration, session_pause_duration AS pauseDuration FROM session_duration WHERE category_id = :categoryId ORDER BY type, day DESC, lastUsage DESC, startMinuteOfDay, endMinuteOfDay")
    abstract fun getUsedTimeListItemsByCategoryId(categoryId: String): DataSource.Factory<Int, UsedTimeListItem>
}
