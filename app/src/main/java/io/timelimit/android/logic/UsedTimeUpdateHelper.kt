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
package io.timelimit.android.logic

import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.sync.actions.AddUsedTimeActionItem
import io.timelimit.android.sync.actions.AddUsedTimeActionVersion2
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class UsedTimeUpdateHelper (val date: DateInTimezone) {
    val timeToAdd = mutableMapOf<String, Int>()
    val extraTimeToSubtract = mutableMapOf<String, Int>()
    var shouldDoAutoCommit = false

    suspend fun add(categoryId: String, time: Int, includingExtraTime: Boolean) {
        if (time < 0) {
            throw IllegalArgumentException()
        }

        if (time == 0) {
            return
        }

        timeToAdd[categoryId] = (timeToAdd[categoryId] ?: 0) + time

        if (includingExtraTime) {
            extraTimeToSubtract[categoryId] = (extraTimeToSubtract[categoryId] ?: 0) + time
        }

        if (timeToAdd[categoryId]!! >= 1000 * 10) {
            shouldDoAutoCommit = true
        }
    }

    fun reportCurrentCategories(categories: Set<String>) {
        if (!categories.containsAll(timeToAdd.keys)) {
            shouldDoAutoCommit = true
        }

        if (!categories.containsAll(extraTimeToSubtract.keys)) {
            shouldDoAutoCommit = true
        }
    }

    suspend fun forceCommit(appLogic: AppLogic) {
        if (timeToAdd.isEmpty() && extraTimeToSubtract.isEmpty()) {
            return
        }

        val categoryIds = timeToAdd.keys + extraTimeToSubtract.keys

        ApplyActionUtil.applyAppLogicAction(
                action = AddUsedTimeActionVersion2(
                        dayOfEpoch = date.dayOfEpoch,
                        items = categoryIds.map { categoryId ->
                            AddUsedTimeActionItem(
                                    categoryId = categoryId,
                                    timeToAdd = timeToAdd[categoryId] ?: 0,
                                    extraTimeToSubtract = extraTimeToSubtract[categoryId] ?: 0
                            )
                        }
                ),
                appLogic = appLogic,
                ignoreIfDeviceIsNotConfigured = true
        )

        timeToAdd.clear()
        extraTimeToSubtract.clear()
        shouldDoAutoCommit = false
    }
}