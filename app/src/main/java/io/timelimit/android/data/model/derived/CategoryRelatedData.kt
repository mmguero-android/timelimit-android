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

package io.timelimit.android.data.model.derived

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.*

data class CategoryRelatedData(
        val category: Category,
        val rules: List<TimeLimitRule>,
        val usedTimes: List<UsedTimeItem>,
        val durations: List<SessionDuration>,
        val networks: List<CategoryNetworkId>,
        val limitLoginCategories: List<UserLimitLoginCategory>
) {
    companion object {
        fun load(category: Category, database: Database): CategoryRelatedData = database.runInUnobservedTransaction {
            val rules = database.timeLimitRules().getTimeLimitRulesByCategorySync(category.id)
            val usedTimes = database.usedTimes().getUsedTimeItemsByCategoryId(category.id)
            val durations = database.sessionDuration().getSessionDurationItemsByCategoryIdSync(category.id)
            val networks = database.categoryNetworkId().getByCategoryIdSync(category.id)
            val limitLoginCategories = database.userLimitLoginCategoryDao().getByCategoryIdSync(category.id)

            CategoryRelatedData(
                    category = category,
                    rules = rules,
                    usedTimes = usedTimes,
                    durations = durations,
                    networks = networks,
                    limitLoginCategories = limitLoginCategories
            )
        }
    }

    fun update(
            category: Category,
            updateRules: Boolean,
            updateTimes: Boolean,
            updateDurations: Boolean,
            updateNetworks: Boolean,
            updateLimitLoginCategories: Boolean,
            database: Database
    ): CategoryRelatedData = database.runInUnobservedTransaction {
        if (category.id != this.category.id) {
            throw IllegalStateException()
        }

        val rules = if (updateRules) database.timeLimitRules().getTimeLimitRulesByCategorySync(category.id) else rules
        val usedTimes = if (updateTimes) database.usedTimes().getUsedTimeItemsByCategoryId(category.id) else usedTimes
        val durations = if (updateDurations) database.sessionDuration().getSessionDurationItemsByCategoryIdSync(category.id) else durations
        val networks = if (updateNetworks) database.categoryNetworkId().getByCategoryIdSync(category.id) else networks
        val limitLoginCategories = if (updateLimitLoginCategories) database.userLimitLoginCategoryDao().getByCategoryIdSync(category.id) else limitLoginCategories

        if (
                category == this.category && rules == this.rules && usedTimes == this.usedTimes &&
                durations == this.durations && networks == this.networks && limitLoginCategories == this.limitLoginCategories
        ) {
            this
        } else {
            CategoryRelatedData(
                    category = category,
                    rules = rules,
                    usedTimes = usedTimes,
                    durations = durations,
                    networks = networks,
                    limitLoginCategories = limitLoginCategories
            )
        }
    }
}