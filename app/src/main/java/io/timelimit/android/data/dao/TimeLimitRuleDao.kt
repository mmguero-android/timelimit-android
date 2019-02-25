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

import androidx.lifecycle.LiveData
import androidx.room.*
import io.timelimit.android.data.model.TimeLimitRule

@Dao
abstract class TimeLimitRuleDao {
    @Query("SELECT * FROM time_limit_rule WHERE category_id = :categoryId")
    abstract fun getTimeLimitRulesByCategory(categoryId: String): LiveData<List<TimeLimitRule>>

    @Query("SELECT * FROM time_limit_rule WHERE category_id IN (:categoryIds)")
    abstract fun getTimeLimitRulesByCategories(categoryIds: List<String>): LiveData<List<TimeLimitRule>>

    @Query("SELECT * FROM time_limit_rule WHERE category_id = :categoryId")
    abstract fun getTimeLimitRulesByCategorySync(categoryId: String): List<TimeLimitRule>

    @Query("DELETE FROM time_limit_rule WHERE category_id = :categoryId")
    abstract fun deleteTimeLimitRulesByCategory(categoryId: String)

    @Insert
    abstract fun addTimeLimitRule(rule: TimeLimitRule)

    @Update
    abstract fun updateTimeLimitRule(rule: TimeLimitRule)

    @Delete
    abstract fun deleteTimeLimitRule(rule: TimeLimitRule)

    @Query("SELECT * FROM time_limit_rule WHERE id = :timeLimitRuleId")
    abstract fun getTimeLimitRuleByIdSync(timeLimitRuleId: String): TimeLimitRule?

    @Query("SELECT * FROM time_limit_rule WHERE id = :timeLimitRuleId")
    abstract fun getTimeLimitRuleByIdLive(timeLimitRuleId: String): LiveData<TimeLimitRule?>

    @Query("DELETE FROM time_limit_rule WHERE id = :timeLimitRuleId")
    abstract fun deleteTimeLimitRuleByIdSync(timeLimitRuleId: String)

    @Query("DELETE FROM time_limit_rule WHERE id IN (:timeLimitRuleIds)")
    abstract fun deleteTimeLimitRulesByIdsSync(timeLimitRuleIds: List<String>)

    @Query("SELECT * FROM time_limit_rule LIMIT :pageSize OFFSET :offset")
    abstract fun getRulePageSync(offset: Int, pageSize: Int): List<TimeLimitRule>

    @Query("SELECT * FROM time_limit_rule")
    abstract fun getAllRulesSync(): List<TimeLimitRule>
}
