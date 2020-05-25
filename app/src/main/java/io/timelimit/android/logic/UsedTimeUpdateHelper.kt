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

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.sync.actions.AddUsedTimeActionItem
import io.timelimit.android.sync.actions.AddUsedTimeActionItemAdditionalCountingSlot
import io.timelimit.android.sync.actions.AddUsedTimeActionItemSessionDurationLimitSlot
import io.timelimit.android.sync.actions.AddUsedTimeActionVersion2
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.actions.dispatch.CategoryNotFoundException

class UsedTimeUpdateHelper (val date: DateInTimezone) {
    companion object {
        private const val LOG_TAG = "UsedTimeUpdateHelper"
    }

    val timeToAdd = mutableMapOf<String, Int>()
    val extraTimeToSubtract = mutableMapOf<String, Int>()
    val sessionDurationLimitSlots = mutableMapOf<String, Set<AddUsedTimeActionItemSessionDurationLimitSlot>>()
    var trustedTimestamp: Long = 0
    val additionalSlots = mutableMapOf<String, Set<AddUsedTimeActionItemAdditionalCountingSlot>>()
    var shouldDoAutoCommit = false

    fun add(
            categoryId: String, time: Int, slots: Set<AddUsedTimeActionItemAdditionalCountingSlot>,
            includingExtraTime: Boolean, sessionDurationLimits: Set<AddUsedTimeActionItemSessionDurationLimitSlot>,
            trustedTimestamp: Long
    ) {
        if (time < 0) {
            throw IllegalArgumentException()
        }

        if (time == 0) {
            return
        }

        timeToAdd[categoryId] = (timeToAdd[categoryId] ?: 0) + time

        if (sessionDurationLimits.isNotEmpty()) {
            this.sessionDurationLimitSlots[categoryId] = sessionDurationLimits
        }

        if (sessionDurationLimits.isNotEmpty() && trustedTimestamp != 0L) {
            this.trustedTimestamp = trustedTimestamp
        }

        if (includingExtraTime) {
            extraTimeToSubtract[categoryId] = (extraTimeToSubtract[categoryId] ?: 0) + time
        }

        if (additionalSlots[categoryId] != null && slots != additionalSlots[categoryId]) {
            shouldDoAutoCommit = true
        } else if (slots.isNotEmpty()) {
            additionalSlots[categoryId] = slots
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

        try {
            ApplyActionUtil.applyAppLogicAction(
                    action = AddUsedTimeActionVersion2(
                            dayOfEpoch = date.dayOfEpoch,
                            items = categoryIds.map { categoryId ->
                                AddUsedTimeActionItem(
                                        categoryId = categoryId,
                                        timeToAdd = timeToAdd[categoryId] ?: 0,
                                        extraTimeToSubtract = extraTimeToSubtract[categoryId] ?: 0,
                                        additionalCountingSlots = additionalSlots[categoryId] ?: emptySet(),
                                        sessionDurationLimits = sessionDurationLimitSlots[categoryId] ?: emptySet()
                                )
                            },
                            trustedTimestamp = trustedTimestamp
                    ),
                    appLogic = appLogic,
                    ignoreIfDeviceIsNotConfigured = true
            )
        } catch (ex: CategoryNotFoundException) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "could not commit used times", ex)
            }

            // this is a very rare case if a category is deleted while it is used;
            // in this case there could be some lost time
            // changes for other categories, but it's no big problem
        }

        timeToAdd.clear()
        extraTimeToSubtract.clear()
        sessionDurationLimitSlots.clear()
        trustedTimestamp = 0
        additionalSlots.clear()
        shouldDoAutoCommit = false
    }
}