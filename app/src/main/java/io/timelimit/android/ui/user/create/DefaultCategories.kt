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
package io.timelimit.android.ui.user.create

import android.content.Context
import io.timelimit.android.R
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.TimeLimitRule
import java.util.*

class DefaultCategories private constructor(private val context: Context) {
    companion object {
        private var instance: DefaultCategories? = null

        fun with(context: Context): DefaultCategories {
            if (instance == null) {
                instance = DefaultCategories(context.applicationContext)
            }

            return instance!!
        }
    }

    val allowedAppsTitle get() = context.getString(R.string.setup_category_allowed)
    val allowedGamesTitle get() = context.getString(R.string.setup_category_games)
    val allowedGamesBlockedTimes: ImmutableBitmask by lazy {
        ImmutableBitmask(
                BitSet().apply {
                    for (i in 0..6) {
                        val startOfDay = i * 60 * 24

                        // to 6 AM
                        set(startOfDay, startOfDay + 6 * 60)

                        // from 6 PM
                        set(startOfDay + 18 * 60, startOfDay + 24 * 60)
                    }
                }
        )
    }

    fun generateGamesTimeLimitRules(categoryId: String): List<TimeLimitRule> {
        val rules = mutableListOf<TimeLimitRule>()

        // maximum time for each workday
        for (day in 0..4) {
            rules.add(
                    TimeLimitRule(
                            id = IdGenerator.generateId(),
                            categoryId = categoryId,
                            applyToExtraTimeUsage = false,
                            dayMask = (1 shl day).toByte(),
                            maximumTimeInMillis = 1000 * 60 * 30    // 30 minutes
                    )
            )
        }

        // maximum time for each weekend day
        for (day in 5..6) {
            rules.add(
                    TimeLimitRule(
                            id = IdGenerator.generateId(),
                            categoryId = categoryId,
                            applyToExtraTimeUsage = false,
                            dayMask = (1 shl day).toByte(),
                            maximumTimeInMillis = 1000 * 60 * 60 * 3    // 3 hours
                    )
            )
        }

        // maximum time per total week
        val dayMask = BitSet()

        dayMask.set(0, 7)

        rules.add(
                TimeLimitRule(
                        id = IdGenerator.generateId(),
                        categoryId = categoryId,
                        applyToExtraTimeUsage = false,
                        dayMask = 1 + 2 + 4 + 8 + 16 + 32 + 64,
                        maximumTimeInMillis = 1000 * 60 * 60 * 6    // 6 hours
                )
        )

        return rules.toList()
    }
}
