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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RemainingTime
import java.util.*

class ManageChildCategoriesModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val childId = MutableLiveData<String>()

    fun init(childId: String) {
        if (this.childId.value != childId) {
            this.childId.value = childId
        }
    }

    private val childDevices = childId.switchMap { logic.database.device().getDevicesByUserId(it) }

    private val suppressChildDeviceManipulation = logic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.HIDE_MANIPULATION_WARNING)

    private val hasChildDevicesWithManipulation = childDevices.map { devices -> devices.find { device -> device.hasAnyManipulation } != null }.ignoreUnchanged()

    private val hasNotSuppressedChildDeviceManipulation = hasChildDevicesWithManipulation.and(suppressChildDeviceManipulation.invert())

    private val childRelatedData = childId.switchMap { logic.database.derivedDataDao().getUserRelatedDataLive(it) }

    private val childTimezone = childRelatedData.map { it?.timeZone ?: TimeZone.getDefault() }

    private val childMinuteOfWeek = childTimezone.switchMap { timeZone ->
        liveDataFromFunction { getMinuteOfWeek(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone) }
    }.ignoreUnchanged()

    private val childDate = childTimezone.switchMap { timeZone ->
        liveDataFromFunction { DateInTimezone.newInstance(logic.realTimeLogic.getCurrentTimeInMillis(), timeZone) }
    }.ignoreUnchanged()

    private val categoryItems = childRelatedData.switchMap { userRelatedData ->
        childDate.switchMap { childDate ->
            childMinuteOfWeek.map { childMinuteOfWeek ->
                if (userRelatedData != null) {
                    val firstDayOfWeek = childDate.dayOfEpoch - childDate.dayOfWeek

                    userRelatedData.sortedCategories().map { (level, category) ->
                        val rules = category.rules
                        val usedTimeItemsForCategory = category.usedTimes

                        CategoryItem(
                                category = category.category,
                                isBlockedTimeNow = category.category.blockedMinutesInWeek.read(childMinuteOfWeek),
                                remainingTimeToday = RemainingTime.getRemainingTime(
                                        dayOfWeek = childDate.dayOfWeek,
                                        usedTimes = usedTimeItemsForCategory,
                                        rules = rules,
                                        extraTime = category.category.getExtraTime(dayOfEpoch = childDate.dayOfEpoch),
                                        minuteOfDay = childMinuteOfWeek % MinuteOfDay.LENGTH,
                                        firstDayOfWeekAsEpochDay = firstDayOfWeek
                                )?.includingExtraTime,
                                usedTimeToday = usedTimeItemsForCategory.find { item ->
                                    item.dayOfEpoch == childDate.dayOfEpoch && item.startTimeOfDay == MinuteOfDay.MIN &&
                                            item.endTimeOfDay == MinuteOfDay.MAX
                                }?.usedMillis ?: 0,
                                usedForNotAssignedApps = category.category.id == userRelatedData.user.categoryForNotAssignedApps,
                                parentCategoryId = if (level == 0) null else category.category.parentCategoryId,
                                categoryNestingLevel = level
                        )
                    }
                } else {
                    emptyList()
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

    val listContent = hasNotSuppressedChildDeviceManipulation.switchMap { hasChildDevicesWithManipulation ->
        listContentStep1.map { listContent ->
            if (hasChildDevicesWithManipulation) {
                listOf(ManipulationWarningCategoryItem) + listContent
            } else {
                listContent
            }
        }
    }
}