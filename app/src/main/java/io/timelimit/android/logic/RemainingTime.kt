/*
 * TimeLimit Copyright <C> 2019- 2020 Jonas Lochmann
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

import android.util.SparseLongArray
import io.timelimit.android.data.model.TimeLimitRule

data class RemainingTime(val includingExtraTime: Long, val default: Long) {
    val hasRemainingTime = includingExtraTime > 0
    val usingExtraTime = includingExtraTime > 0 && default == 0L

    init {
        if (includingExtraTime < 0 || default < 0) {
            throw IllegalStateException("time is < 0")
        }

        if (includingExtraTime < default) {
            throw IllegalStateException("extra time < default time")
        }
    }

    companion object {
        fun min(a: RemainingTime?, b: RemainingTime?): RemainingTime? = if (a == null) {
            b
        } else if (b == null) {
            a
        } else {
            RemainingTime(
                    includingExtraTime = Math.min(a.includingExtraTime, b.includingExtraTime),
                    default = Math.min(a.default, b.default)
            )
        }

        private fun getRulesRelatedToDay(dayOfWeek: Int, rules: List<TimeLimitRule>): List<TimeLimitRule> {
            return rules.filter { (it.dayMask.toInt() and (1 shl dayOfWeek)) != 0 }
        }

        fun getRemainingTime(dayOfWeek: Int, usedTimes: SparseLongArray, rules: List<TimeLimitRule>, extraTime: Long): RemainingTime? {
            if (extraTime < 0) {
                throw IllegalStateException("extra time < 0")
            }

            val relatedRules = getRulesRelatedToDay(dayOfWeek, rules)
            val withoutExtraTime = getRemainingTime(usedTimes, relatedRules, false)
            val withExtraTime = getRemainingTime(usedTimes, relatedRules, true)

            if (withoutExtraTime == null && withExtraTime == null) {
                // no rules
                return null
            } else if (withoutExtraTime != null && withExtraTime != null) {
                // with rules for extra time
                val additionalTimeWithExtraTime = withExtraTime - withoutExtraTime

                if (additionalTimeWithExtraTime < 0) {
                    throw IllegalStateException("additional time with extra time < 0")
                }

                return RemainingTime(
                        includingExtraTime = withoutExtraTime + Math.min(extraTime, additionalTimeWithExtraTime),
                        default = withoutExtraTime
                )
            } else if (withoutExtraTime != null) {
                // without rules for extra time
                return RemainingTime(
                        includingExtraTime = withoutExtraTime + extraTime,
                        default = withoutExtraTime
                )
            } else {
                throw IllegalStateException()
            }
        }

        private fun getRemainingTime(usedTimes: SparseLongArray, relatedRules: List<TimeLimitRule>, assumeMaximalExtraTime: Boolean): Long? {
            return relatedRules.filter { (!assumeMaximalExtraTime) || it.applyToExtraTimeUsage }.map {
                var usedTime = 0L

                for (day in 0..6) {
                    if ((it.dayMask.toInt() and (1 shl day)) != 0) {
                        usedTime += usedTimes[day]
                    }
                }

                val maxTime = it.maximumTimeInMillis
                val remaining = Math.max(0, maxTime - usedTime)

                remaining
            }.min()
        }
    }
}
