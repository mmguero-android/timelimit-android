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
package io.timelimit.android.ui.manage.child.category

import android.app.Application
import android.util.SparseLongArray
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.extensions.mapToTimezone
import io.timelimit.android.data.extensions.sorted
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RemainingTime

class ManageChildCategoriesModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val childId = MutableLiveData<String>()

    fun init(childId: String) {
        if (this.childId.value != childId) {
            this.childId.value = childId
        }
    }

    private val childDevices = childId.switchMap { logic.database.device().getDevicesByUserId(it) }

    private val hasChildDevicesWithManipulation = childDevices.map { devices -> devices.find { device -> device.hasAnyManipulation } != null }.ignoreUnchanged()

    private val childEntry = childId.switchMap { logic.database.user().getChildUserByIdLive(it) }

    private val categoryForUnassignedAppsLive = childEntry.map { it?.categoryForNotAssignedApps }.ignoreUnchanged()

    private val childTimezone = childEntry.mapToTimezone()

    private val childMinuteOfWeek = childTimezone.switchMap { timeZone ->
        liveDataFromFunction { getMinuteOfWeek(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone) }
    }.ignoreUnchanged()

    private val childDate = childTimezone.switchMap { timeZone ->
        liveDataFromFunction { DateInTimezone.newInstance(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone) }
    }.ignoreUnchanged()

    private val categories = childId.switchMap { logic.database.category().getCategoriesByChildId(it) }

    private val usedTimeItemsForWeek = childDate.switchMap { date ->
        categories.switchMap { categories ->
            logic.database.usedTimes().getUsedTimesByDayAndCategoryIds(
                    categoryIds = categories.map { it.id },
                    startingDayOfEpoch = date.dayOfEpoch - date.dayOfWeek,
                    endDayOfEpoch = date.dayOfEpoch - date.dayOfWeek + 6
            )
        }
    }

    private val timeLimitRules = categories.switchMap { categories ->
        logic.database.timeLimitRules().getTimeLimitRulesByCategories(
                categories.map { category -> category.id }
        )
    }

    private val sortedCategories = categories.map { it.sorted() }

    private val categoryItems = categoryForUnassignedAppsLive.switchMap { categoryForUnassignedApps ->
        sortedCategories.switchMap { categories ->
            timeLimitRules.switchMap { timeLimitRules ->
                childDate.switchMap { childDate ->
                    usedTimeItemsForWeek.switchMap { usedTimeItemsForWeek ->
                        childMinuteOfWeek.map { childMinuteOfWeek ->
                            val rulesByCategoryId = timeLimitRules.groupBy { rule -> rule.categoryId }
                            val usedTimesByCategory = usedTimeItemsForWeek.groupBy { item -> item.categoryId }
                            val firstDayOfWeek = childDate.dayOfEpoch - childDate.dayOfWeek

                            categories.map { category ->
                                val rules = rulesByCategoryId[category.id] ?: emptyList()
                                val usedTimeItemsForCategory = usedTimesByCategory[category.id]
                                        ?: emptyList()
                                val parentCategory = categories.find { it.id == category.parentCategoryId }

                                CategoryItem(
                                        category = category,
                                        isBlockedTimeNow = category.blockedMinutesInWeek.read(childMinuteOfWeek),
                                        remainingTimeToday = RemainingTime.getRemainingTime(
                                                dayOfWeek = childDate.dayOfWeek,
                                                usedTimes = SparseLongArray().apply {
                                                    usedTimeItemsForCategory.forEach { usedTimeItem ->

                                                        val dayOfWeek = usedTimeItem.dayOfEpoch - firstDayOfWeek

                                                        put(dayOfWeek, usedTimeItem.usedMillis)
                                                    }
                                                },
                                                rules = rules,
                                                extraTime = category.getExtraTime(dayOfEpoch = childDate.dayOfEpoch)
                                        )?.includingExtraTime,
                                        usedTimeToday = usedTimeItemsForCategory.find { item -> item.dayOfEpoch == childDate.dayOfEpoch }?.usedMillis
                                                ?: 0,
                                        usedForNotAssignedApps = categoryForUnassignedApps == category.id,
                                        parentCategoryTitle = parentCategory?.title
                                )
                            }
                        }
                    }
                }
            }
        }
    }.ignoreUnchanged()

    private val hasShownHint = logic.database.config().wereHintsShown(HintsToShow.CATEGORIES_INTRODUCTION)

    private val listContentStep1 = hasShownHint.switchMap { hasShownHint ->
        categoryItems.map { categoryItems ->
            if (hasShownHint) {
                categoryItems + listOf(CreateCategoryItem)
            } else {
                listOf(CategoriesIntroductionHeader) + categoryItems + listOf(CreateCategoryItem)
            }
        }
    }

    val listContent = hasChildDevicesWithManipulation.switchMap { hasChildDevicesWithManipulation ->
        listContentStep1.map { listContent ->
            if (hasChildDevicesWithManipulation) {
                listOf(ManipulationWarningCategoryItem) + listContent
            } else {
                listContent
            }
        }
    }
}