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

import android.util.Log
import android.util.SparseLongArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.livedata.*
import java.util.*

class CategoriesBlockingReasonUtil(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "CategoryBlockingReason"
    }

    private val blockingReason = BlockingReasonUtil(appLogic)
    private val temporarilyTrustedTimeInMillis = blockingReason.getTemporarilyTrustedTimeInMillis()

    // NOTE: this ignores the current device rule
    fun getCategoryBlockingReasons(
            childDisableLimitsUntil: LiveData<Long>,
            timeZone: LiveData<TimeZone>,
            categories: List<Category>
    ): LiveData<Map<String, BlockingReason>> {
        val result = MediatorLiveData<Map<String, BlockingReason>>()
        val status = mutableMapOf<String, BlockingReason>()

        val reasons = getCategoryBlockingReasonsInternal(childDisableLimitsUntil, timeZone, categories)
        var missing = reasons.size

        reasons.entries.forEach { (k, v) ->
            var ready = false

            result.addSource(v) { newStatus ->
                status[k] = newStatus

                if (!ready) {
                    ready = true
                    missing--
                }

                if (missing == 0) {
                    result.value = status.toMap()
                }
            }
        }

        return result
    }

    // NOTE: this ignores the current device rule
    private fun getCategoryBlockingReasonsInternal(
            childDisableLimitsUntil: LiveData<Long>,
            timeZone: LiveData<TimeZone>,
            categories: List<Category>
    ): Map<String, LiveData<BlockingReason>> {
        val result = mutableMapOf<String, LiveData<BlockingReason>>()
        val categoryById = categories.associateBy { it.id }

        val areLimitsTemporarilyDisabled = areLimitsDisabled(
                temporarilyTrustedTimeInMillis = temporarilyTrustedTimeInMillis,
                childDisableLimitsUntil = childDisableLimitsUntil
        )

        val temporarilyTrustedMinuteOfWeek = timeZone.switchMap { timeZone ->
            blockingReason.getTrustedMinuteOfWeekLive(timeZone)
        }

        val temporarilyTrustedDate = timeZone.switchMap { timeZone ->
            blockingReason.getTrustedDateLive(timeZone)
        }

        fun handleCategory(categoryId: String) {
            categoryById[categoryId]?.let { category ->
                result[categoryId] = result[categoryId] ?: getCategoryBlockingReason(
                        category = liveDataFromValue(category),
                        temporarilyTrustedMinuteOfWeek = temporarilyTrustedMinuteOfWeek,
                        temporarilyTrustedDate = temporarilyTrustedDate,
                        areLimitsTemporarilyDisabled = areLimitsTemporarilyDisabled
                )
            }
        }

        categoryById.keys.forEach { handleCategory(it) }

        return result
    }

    // NOTE: this ignores parent categories (otherwise would check parent category if category has no blocking reason)
    // NOTE: this ignores the current device rule
    private fun getCategoryBlockingReason(
            category: LiveData<Category>,
            temporarilyTrustedMinuteOfWeek: LiveData<Int?>,
            temporarilyTrustedDate: LiveData<DateInTimezone?>,
            areLimitsTemporarilyDisabled: LiveData<Boolean>
    ): LiveData<BlockingReason> {
        return category.switchMap { category ->
            if (category.temporarilyBlocked) {
                liveDataFromValue(BlockingReason.TemporarilyBlocked)
            } else {
                areLimitsTemporarilyDisabled.switchMap { areLimitsTemporarilyDisabled ->
                    if (areLimitsTemporarilyDisabled) {
                        liveDataFromValue(BlockingReason.None)
                    } else {
                        checkCategoryBlockedTimeAreas(
                                temporarilyTrustedMinuteOfWeek = temporarilyTrustedMinuteOfWeek,
                                blockedMinutesInWeek = category.blockedMinutesInWeek.dataNotToModify
                        ).switchMap { blockedTimeAreasReason ->
                            if (blockedTimeAreasReason != BlockingReason.None) {
                                liveDataFromValue(blockedTimeAreasReason)
                            } else {
                                checkCategoryTimeLimitRules(
                                        temporarilyTrustedDate = temporarilyTrustedDate,
                                        category = category,
                                        rules = appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(category.id)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun areLimitsDisabled(
            temporarilyTrustedTimeInMillis: LiveData<Long?>,
            childDisableLimitsUntil: LiveData<Long>
    ): LiveData<Boolean> = childDisableLimitsUntil.switchMap { childDisableLimitsUntil ->
        if (childDisableLimitsUntil == 0L) {
            liveDataFromValue(false)
        } else {
            temporarilyTrustedTimeInMillis.map {
                trustedTimeInMillis ->

                trustedTimeInMillis != null && childDisableLimitsUntil > trustedTimeInMillis
            }.ignoreUnchanged()
        }
    }

    private fun checkCategoryBlockedTimeAreas(blockedMinutesInWeek: BitSet, temporarilyTrustedMinuteOfWeek: LiveData<Int?>): LiveData<BlockingReason> {
        if (blockedMinutesInWeek.isEmpty) {
            return liveDataFromValue(BlockingReason.None)
        } else {
            return temporarilyTrustedMinuteOfWeek.map { temporarilyTrustedMinuteOfWeek ->
                if (temporarilyTrustedMinuteOfWeek == null) {
                    BlockingReason.MissingNetworkTime
                } else if (blockedMinutesInWeek[temporarilyTrustedMinuteOfWeek]) {
                    BlockingReason.BlockedAtThisTime
                } else {
                    BlockingReason.None
                }
            }.ignoreUnchanged()
        }
    }

    private fun checkCategoryTimeLimitRules(
            temporarilyTrustedDate: LiveData<DateInTimezone?>,
            rules: LiveData<List<TimeLimitRule>>,
            category: Category
    ): LiveData<BlockingReason> = rules.switchMap { rules ->
        if (rules.isEmpty()) {
            liveDataFromValue(BlockingReason.None)
        } else {
            temporarilyTrustedDate.switchMap { temporarilyTrustedDate ->
                if (temporarilyTrustedDate == null) {
                    liveDataFromValue(BlockingReason.MissingNetworkTime)
                } else {
                    getBlockingReasonStep7(
                            category = category,
                            nowTrustedDate = temporarilyTrustedDate,
                            rules = rules
                    )
                }
            }
        }
    }

    private fun getBlockingReasonStep7(category: Category, nowTrustedDate: DateInTimezone, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 7")
        }

        return appLogic.database.usedTimes().getUsedTimesOfWeek(category.id, nowTrustedDate.dayOfEpoch - nowTrustedDate.dayOfWeek).map { usedTimes ->
            val usedTimesSparseArray = SparseLongArray()

            for (i in 0..6) {
                val usedTimesItem = usedTimes[i]?.usedMillis
                usedTimesSparseArray.put(i, (if (usedTimesItem != null) usedTimesItem else 0))
            }

            val remaining = RemainingTime.getRemainingTime(nowTrustedDate.dayOfWeek, usedTimesSparseArray, rules, category.extraTimeInMillis)

            if (remaining == null || remaining.includingExtraTime > 0) {
                BlockingReason.None
            } else {
                if (category.extraTimeInMillis > 0) {
                    BlockingReason.TimeOverExtraTimeCanBeUsedLater
                } else {
                    BlockingReason.TimeOver
                }
            }
        }.ignoreUnchanged()
    }
}
