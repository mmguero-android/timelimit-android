/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
import io.timelimit.android.logic.blockingreason.CategoryItselfHandling
import io.timelimit.android.sync.actions.AddUsedTimeActionItem
import io.timelimit.android.sync.actions.AddUsedTimeActionVersion2
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.actions.dispatch.CategoryNotFoundException

class UsedTimeUpdateHelper (private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "NewUsedTimeUpdateHelper"
    }

    private var countedTime = 0
    private var lastCategoryHandlings = emptyList<CategoryItselfHandling>()
    private var categoryIds = emptySet<String>()
    private var trustedTimestamp = 0L
    private var dayOfEpoch = 0
    private var maxTimeToAdd = Long.MAX_VALUE

    fun getCountedTime() = countedTime
    fun getCountedCategoryIds() = categoryIds

    // returns true if it made a commit
    suspend fun report(duration: Int, handlings: List<CategoryItselfHandling>, trustedTimestamp: Long, dayOfEpoch: Int): Boolean {
        if (handlings.find { !it.shouldCountTime } != null || duration < 0) {
            throw IllegalArgumentException()
        }

        if (duration == 0) {
            return false
        }

        // the time is counted for the previous categories
        // otherwise, the time is so much that the previous session of a session duration limit
        // will be extend which causes an unintended blocking
        countedTime += duration

        val makeCommitByDifferntHandling = if (handlings != lastCategoryHandlings) {
            val newIds = handlings.map { it.createdWithCategoryRelatedData.category.id }.toSet()
            val oldIds = categoryIds

            maxTimeToAdd = handlings.minBy { it.maxTimeToAdd }?.maxTimeToAdd ?: Long.MAX_VALUE
            categoryIds = newIds

            if (lastCategoryHandlings.size != handlings.size) {
                true
            } else {
                if ((oldIds - newIds).isNotEmpty() || (newIds - oldIds).isNotEmpty()) {
                    true
                } else {
                    val oldHandlingById = lastCategoryHandlings.associateBy { it.createdWithCategoryRelatedData.category.id }

                    handlings.find { newHandling ->
                        val oldHandling = oldHandlingById[newHandling.createdWithCategoryRelatedData.category.id]!!

                        oldHandling.shouldCountExtraTime != newHandling.shouldCountExtraTime ||
                                oldHandling.additionalTimeCountingSlots != newHandling.additionalTimeCountingSlots ||
                                oldHandling.sessionDurationSlotsToCount != newHandling.sessionDurationSlotsToCount
                    } != null
                }
            }
        } else false
        val makeCommitByDifferentBaseData = this.dayOfEpoch != dayOfEpoch
        val makeCommitByCountedTime = countedTime >= 30 * 1000 || countedTime >= maxTimeToAdd
        val makeCommit = makeCommitByDifferntHandling || makeCommitByDifferentBaseData || makeCommitByCountedTime

        val madeCommit = if (makeCommit) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "makeCommitByDifferntHandling = $makeCommitByDifferntHandling; makeCommitByDifferentBaseData = $makeCommitByDifferentBaseData; makeCommitByCountedTime = $makeCommitByCountedTime")
            }

            doCommitPrivate()
        } else {
            false
        }

        this.lastCategoryHandlings = handlings
        this.trustedTimestamp = trustedTimestamp
        this.dayOfEpoch = dayOfEpoch

        return madeCommit
    }

    suspend fun flush() {
        doCommitPrivate()

        lastCategoryHandlings = emptyList()
        categoryIds = emptySet()
    }

    private suspend fun doCommitPrivate(): Boolean {
        val makeCommit = lastCategoryHandlings.isNotEmpty() && countedTime > 0

        if (makeCommit) {
            try {
                ApplyActionUtil.applyAppLogicAction(
                        action = AddUsedTimeActionVersion2(
                                dayOfEpoch = dayOfEpoch,
                                items = lastCategoryHandlings.map { handling ->
                                    AddUsedTimeActionItem(
                                            categoryId = handling.createdWithCategoryRelatedData.category.id,
                                            timeToAdd = countedTime,
                                            extraTimeToSubtract = if (handling.shouldCountExtraTime) countedTime else 0,
                                            additionalCountingSlots = handling.additionalTimeCountingSlots,
                                            sessionDurationLimits = handling.sessionDurationSlotsToCount
                                    )
                                },
                                trustedTimestamp = if (lastCategoryHandlings.find { it.sessionDurationSlotsToCount.isNotEmpty() } != null) trustedTimestamp else 0
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
        }

        countedTime = 0
        // doing this would cause a commit very soon again
        // lastCategoryHandlings = emptyList()
        // categoryIds = emptySet()

        return makeCommit
    }
}