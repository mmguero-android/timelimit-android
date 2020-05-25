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
package io.timelimit.android.ui.widget

import android.util.SparseLongArray
import androidx.lifecycle.LiveData
import io.timelimit.android.data.extensions.mapToTimezone
import io.timelimit.android.data.model.getCurrentTimeSlotStartMinute
import io.timelimit.android.data.model.getSlotSwitchMinutes
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.RemainingTime

object TimesWidgetItems {
    fun with(logic: AppLogic): LiveData<List<TimesWidgetItem>> {
        val userEntry = logic.deviceUserEntry
        val userId = logic.deviceUserId
        val userTimezone = userEntry.mapToTimezone()
        val userDate = userTimezone.switchMap { timeZone ->
            liveDataFromFunction { DateInTimezone.newInstance(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone) }
        }.ignoreUnchanged()
        val userMinuteOfWeek = userTimezone.switchMap { timeZone ->
            liveDataFromFunction {
                getMinuteOfWeek(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone)
            }
        }.ignoreUnchanged()
        val categories = userId.switchMap { logic.database.category().getCategoriesByChildId(it) }
        val usedTimeItemsForWeek = userDate.switchMap { date ->
            categories.switchMap { categories ->
                logic.database.usedTimes().getUsedTimesByDayAndCategoryIds(
                        categoryIds = categories.map { it.id },
                        startingDayOfEpoch = date.dayOfEpoch - date.dayOfWeek,
                        endDayOfEpoch = date.dayOfEpoch - date.dayOfWeek + 6
                )
            }
        }
        val timeLimitRules = categories.switchMap { categories ->
            logic.database.timeLimitRules().getTimeLimitRulesByCategories(
                    categories.map { category -> category.id }
            )
        }
        val timeLimitSlot = timeLimitRules.map { it.getSlotSwitchMinutes() }.switchMap {
            userMinuteOfWeek.switchMap { minuteOfWeek ->
                getCurrentTimeSlotStartMinute(it, userMinuteOfWeek.map { it % MinuteOfDay.LENGTH })
            }
        }
        val categoryItems = categories.switchMap { categories ->
            timeLimitRules.switchMap { timeLimitRules ->
                timeLimitSlot.switchMap { timeLimitSlot ->
                    userDate.switchMap { childDate ->
                        usedTimeItemsForWeek.map { usedTimeItemsForWeek ->
                            val rulesByCategoryId = timeLimitRules.groupBy { rule -> rule.categoryId }
                            val usedTimesByCategory = usedTimeItemsForWeek.groupBy { item -> item.categoryId }
                            val firstDayOfWeek = childDate.dayOfEpoch - childDate.dayOfWeek

                            categories.map { category ->
                                val rules = rulesByCategoryId[category.id] ?: emptyList()
                                val usedTimeItemsForCategory = usedTimesByCategory[category.id]
                                        ?: emptyList()

                                TimesWidgetItem(
                                        title = category.title,
                                        remainingTimeToday = RemainingTime.getRemainingTime(
                                                dayOfWeek = childDate.dayOfWeek,
                                                usedTimes = usedTimeItemsForCategory,
                                                rules = rules,
                                                extraTime = category.getExtraTime(dayOfEpoch = childDate.dayOfEpoch),
                                                minuteOfDay = timeLimitSlot,
                                                firstDayOfWeekAsEpochDay = firstDayOfWeek
                                        )?.includingExtraTime
                                )
                            }
                        }
                    }
                }
            }
        }.ignoreUnchanged()

        return categoryItems
    }
}