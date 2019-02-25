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

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.sync.actions.AddUsedTimeAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class UsedTimeItemBatchUpdateHelper(
        val date: DateInTimezone,
        val childCategoryId: String,
        val parentCategoryId: String?,
        var cachedItemChild: UsedTimeItem?,
        var cachedItemParent: UsedTimeItem?
) {
    companion object {
        suspend fun eventuallyUpdateInstance(
                date: DateInTimezone,
                childCategoryId: String,
                parentCategoryId: String?,
                oldInstance: UsedTimeItemBatchUpdateHelper?,
                usedTimeItemForDayChild: UsedTimeItem?,
                usedTimeItemForDayParent: UsedTimeItem?,
                logic: AppLogic
        ): UsedTimeItemBatchUpdateHelper {
            if (
                    oldInstance != null &&
                    oldInstance.date == date &&
                    oldInstance.childCategoryId == childCategoryId &&
                    oldInstance.parentCategoryId == parentCategoryId
            ) {
                if (oldInstance.cachedItemChild != usedTimeItemForDayChild) {
                    oldInstance.cachedItemChild = usedTimeItemForDayChild
                }

                if (oldInstance.cachedItemParent != usedTimeItemForDayParent) {
                    oldInstance.cachedItemParent = usedTimeItemForDayParent
                }

                return oldInstance
            } else {
                if (oldInstance != null) {
                    oldInstance.commit(logic)
                }

                return UsedTimeItemBatchUpdateHelper(
                        date = date,
                        childCategoryId = childCategoryId,
                        parentCategoryId = parentCategoryId,
                        cachedItemChild = usedTimeItemForDayChild,
                        cachedItemParent = usedTimeItemForDayParent
                )
            }
        }
    }

    private var timeToAdd = 0
    private var extraTimeToSubtract = 0

    suspend fun addUsedTime(time: Int, subtractExtraTime: Boolean, appLogic: AppLogic) {
        timeToAdd += time

        if (subtractExtraTime) {
            extraTimeToSubtract += time
        }

        if (Math.max(timeToAdd, extraTimeToSubtract) > 1000 * 10 /* 10 seconds */) {
            commit(appLogic)
        }
    }

    fun getTotalUsedTimeChild(): Long = (cachedItemChild?.usedMillis ?: 0) + timeToAdd
    fun getTotalUsedTimeParent(): Long = (cachedItemParent?.usedMillis ?: 0) + timeToAdd

    fun getCachedExtraTimeToSubtract(): Int {
        return extraTimeToSubtract
    }

    suspend fun queryCurrentStatusFromDatabase(database: Database) {
        cachedItemChild = database.usedTimes().getUsedTimeItem(childCategoryId, date.dayOfEpoch).waitForNullableValue()
        cachedItemParent = parentCategoryId?.let {
            database.usedTimes().getUsedTimeItem(parentCategoryId, date.dayOfEpoch).waitForNullableValue()
        }
    }

    suspend fun commit(logic: AppLogic) {
        if (timeToAdd == 0) {
            // do nothing
        } else {
            ApplyActionUtil.applyAppLogicAction(
                    AddUsedTimeAction(
                            categoryId = childCategoryId,
                            timeToAdd = timeToAdd,
                            dayOfEpoch = date.dayOfEpoch,
                            extraTimeToSubtract = extraTimeToSubtract
                    ),
                    logic
            )

            timeToAdd = 0
            extraTimeToSubtract = 0

            queryCurrentStatusFromDatabase(logic.database)
        }
    }
}