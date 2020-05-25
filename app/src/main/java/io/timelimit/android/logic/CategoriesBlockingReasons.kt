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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.extension.isCategoryAllowed
import java.util.*

class CategoriesBlockingReasonUtil(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "CategoryBlockingReason"
    }

    private val blockingReason = BlockingReasonUtil(appLogic)
    private val temporarilyTrustedTimeInMillis = blockingReason.getTemporarilyTrustedTimeInMillis()
    private val batteryLevel = appLogic.platformIntegration.getBatteryStatusLive()

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

        fun handleCategory(categoryId: String, depth: Int) {
            if (depth > 2) {
                return
            }

            categoryById[categoryId]?.let { category ->
                result[categoryId] = result[categoryId] ?: kotlin.run {
                    handleCategory(category.parentCategoryId, depth + 1)

                    val parentCategoryBlockingReason  = result[category.parentCategoryId]
                    val selfReason = getCategoryBlockingReason(
                            category = liveDataFromValue(category),
                            temporarilyTrustedMinuteOfWeek = temporarilyTrustedMinuteOfWeek,
                            temporarilyTrustedDate = temporarilyTrustedDate,
                            areLimitsTemporarilyDisabled = areLimitsTemporarilyDisabled
                    )

                    selfReason.switchMap { self ->
                        if (self == BlockingReason.None && parentCategoryBlockingReason != null) {
                            parentCategoryBlockingReason
                        } else {
                            liveDataFromValue(self)
                        }
                    }
                }
            }
        }

        categoryById.keys.forEach { handleCategory(it, 0) }

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
            val batteryOk = batteryLevel.map { it.isCategoryAllowed(category) }.ignoreUnchanged()
            val elseCase = areLimitsTemporarilyDisabled.switchMap { areLimitsTemporarilyDisabled ->
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
                                    rules = appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(category.id),
                                    temporarilyTrustedMinuteOfWeek = temporarilyTrustedMinuteOfWeek
                            )
                        }
                    }
                }
            }


            batteryOk.switchMap { ok ->
                if (!ok) {
                    liveDataFromValue(BlockingReason.BatteryLimit)
                } else if (category.temporarilyBlocked) {
                    if (category.temporarilyBlockedEndTime == 0L) {
                        liveDataFromValue(BlockingReason.TemporarilyBlocked)
                    } else {
                        temporarilyTrustedTimeInMillis.switchMap { timeInMillis ->
                            if (timeInMillis == null) {
                                liveDataFromValue(BlockingReason.MissingNetworkTime)
                            } else if (timeInMillis < category.temporarilyBlockedEndTime) {
                                liveDataFromValue(BlockingReason.TemporarilyBlocked)
                            } else {
                                elseCase
                            }
                        }
                    }
                } else {
                    elseCase
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
            temporarilyTrustedMinuteOfWeek: LiveData<Int?>,
            rules: LiveData<List<TimeLimitRule>>,
            category: Category
    ): LiveData<BlockingReason> = rules.switchMap { rules ->
        if (rules.isEmpty()) {
            liveDataFromValue(BlockingReason.None)
        } else {
            temporarilyTrustedDate.switchMap { temporarilyTrustedDate ->
                temporarilyTrustedMinuteOfWeek.switchMap { temporarilyTrustedMinuteOfWeek ->
                    if (temporarilyTrustedDate == null || temporarilyTrustedMinuteOfWeek == null) {
                        liveDataFromValue(BlockingReason.MissingNetworkTime)
                    } else {
                        getBlockingReasonStep7(
                                category = category,
                                nowTrustedDate = temporarilyTrustedDate,
                                rules = rules,
                                trustedMinuteOfWeek = temporarilyTrustedMinuteOfWeek
                        )
                    }
                }
            }
        }
    }

    private fun getBlockingReasonStep7(category: Category, nowTrustedDate: DateInTimezone, trustedMinuteOfWeek: Int, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 7")
        }

        val extraTime = category.getExtraTime(dayOfEpoch = nowTrustedDate.dayOfEpoch)
        val firstDayOfWeekAsEpochDay = nowTrustedDate.dayOfEpoch - nowTrustedDate.dayOfWeek

        return appLogic.database.usedTimes().getUsedTimesOfWeek(category.id, firstDayOfWeekAsEpochDay).switchMap { usedTimes ->
            val remaining = RemainingTime.getRemainingTime(nowTrustedDate.dayOfWeek, trustedMinuteOfWeek % MinuteOfDay.LENGTH, usedTimes, rules, extraTime, firstDayOfWeekAsEpochDay)

            if (remaining == null || remaining.includingExtraTime > 0) {
                appLogic.database.sessionDuration().getSessionDurationItemsByCategoryId(category.id).switchMap { durations ->
                    blockingReason.getTemporarilyTrustedTimeInMillis().map { timeInMillis ->
                        if (timeInMillis == null) {
                            BlockingReason.MissingNetworkTime
                        } else {
                            val remainingDuration = RemainingSessionDuration.getRemainingSessionDuration(
                                    rules = rules,
                                    dayOfWeek = nowTrustedDate.dayOfWeek,
                                    durationsOfCategory = durations,
                                    minuteOfDay = trustedMinuteOfWeek % MinuteOfDay.LENGTH,
                                    timestamp = timeInMillis
                            )

                            if (remainingDuration == null || remainingDuration > 0) {
                                BlockingReason.None
                            } else {
                                BlockingReason.SessionDurationLimit
                            }
                        }
                    }
                }
            } else {
                if (extraTime > 0) {
                    liveDataFromValue(BlockingReason.TimeOverExtraTimeCanBeUsedLater)
                } else {
                    liveDataFromValue(BlockingReason.TimeOver)
                }
            }
        }.ignoreUnchanged()
    }
}
