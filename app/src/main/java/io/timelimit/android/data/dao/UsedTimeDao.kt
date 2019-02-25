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
package io.timelimit.android.data.dao

import android.util.SparseArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.livedata.ignoreUnchanged

@Dao
abstract class UsedTimeDao {
    @Query("SELECT * FROM used_time WHERE category_id = :categoryId AND day_of_epoch >= :startingDayOfEpoch AND day_of_epoch <= :endDayOfEpoch")
    protected abstract fun getUsedTimesOfWeekInternal(categoryId: String, startingDayOfEpoch: Int, endDayOfEpoch: Int): LiveData<List<UsedTimeItem>>

    fun getUsedTimesOfWeek(categoryId: String, firstDayOfWeekAsEpochDay: Int): LiveData<SparseArray<UsedTimeItem>> {
        return Transformations.map(getUsedTimesOfWeekInternal(categoryId, firstDayOfWeekAsEpochDay, firstDayOfWeekAsEpochDay + 6).ignoreUnchanged()) {
            val result = SparseArray<UsedTimeItem>()

            it.forEach {
                result.put(it.dayOfEpoch - firstDayOfWeekAsEpochDay, it)
            }

            result
        }
    }

    @Insert
    abstract fun insertUsedTime(item: UsedTimeItem)

    @Insert
    abstract fun insertUsedTimes(item: List<UsedTimeItem>)

    @Query("UPDATE used_time SET used_time = :newUsedTime WHERE category_id = :categoryId AND day_of_epoch = :dayOfEpoch")
    abstract fun updateUsedTime(categoryId: String, dayOfEpoch: Int, newUsedTime: Long)

    @Query("UPDATE used_time SET used_time = used_time + :timeToAdd WHERE category_id = :categoryId AND day_of_epoch = :dayOfEpoch")
    abstract fun addUsedTime(categoryId: String, dayOfEpoch: Int, timeToAdd: Int): Int

    @Query("SELECT * FROM used_time WHERE category_id = :categoryId AND day_of_epoch = :dayOfEpoch")
    abstract fun getUsedTimeItem(categoryId: String, dayOfEpoch: Int): LiveData<UsedTimeItem?>

    @Query("DELETE FROM used_time WHERE category_id = :categoryId")
    abstract fun deleteUsedTimeItems(categoryId: String)

    @Query("SELECT * FROM used_time LIMIT :pageSize OFFSET :offset")
    abstract fun getUsedTimePageSync(offset: Int, pageSize: Int): List<UsedTimeItem>

    @Query("SELECT * FROM used_time WHERE category_id = :categoryId ORDER BY day_of_epoch DESC")
    abstract fun getUsedTimesByCategoryId(categoryId: String): DataSource.Factory<Int, UsedTimeItem>

    @Query("SELECT * FROM used_time WHERE category_id IN (:categoryIds) AND day_of_epoch >= :startingDayOfEpoch AND day_of_epoch <= :endDayOfEpoch")
    abstract fun getUsedTimesByDayAndCategoryIds(categoryIds: List<String>, startingDayOfEpoch: Int, endDayOfEpoch: Int): LiveData<List<UsedTimeItem>>
}
