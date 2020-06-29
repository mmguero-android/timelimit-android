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
package io.timelimit.android.logic.blockingreason

import io.timelimit.android.data.model.derived.CategoryRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.RemainingSessionDuration
import io.timelimit.android.logic.RemainingTime
import io.timelimit.android.sync.actions.AddUsedTimeActionItemAdditionalCountingSlot
import io.timelimit.android.sync.actions.AddUsedTimeActionItemSessionDurationLimitSlot
import org.threeten.bp.ZoneId

data class CategoryItselfHandling (
        val shouldCountTime: Boolean,
        val shouldCountExtraTime: Boolean,
        val maxTimeToAdd: Long,
        val sessionDurationSlotsToCount: Set<AddUsedTimeActionItemSessionDurationLimitSlot>,
        val additionalTimeCountingSlots: Set<AddUsedTimeActionItemAdditionalCountingSlot>,
        val areLimitsTemporarilyDisabled: Boolean,
        val okByBattery: Boolean,
        val okByTempBlocking: Boolean,
        val okByBlockedTimeAreas: Boolean,
        val okByTimeLimitRules: Boolean,
        val okBySessionDurationLimits: Boolean,
        val okByCurrentDevice: Boolean,
        val missingNetworkTime: Boolean,
        val blockAllNotifications: Boolean,
        val remainingTime: RemainingTime?,
        val remainingSessionDuration: Long?,
        val dependsOnMinTime: Long,
        val dependsOnMaxTime: Long,
        val dependsOnBatteryCharging: Boolean,
        val dependsOnMinBatteryLevel: Int,
        val dependsOnMaxBatteryLevel: Int,
        val createdWithCategoryRelatedData: CategoryRelatedData,
        val createdWithUserRelatedData: UserRelatedData,
        val createdWithBatteryStatus: BatteryStatus,
        val createdWithTemporarilyTrustTime: Boolean,
        val createdWithAssumeCurrentDevice: Boolean
) {
    companion object {
        fun calculate(
                categoryRelatedData: CategoryRelatedData,
                user: UserRelatedData,
                batteryStatus: BatteryStatus,
                shouldTrustTimeTemporarily: Boolean,
                timeInMillis: Long,
                assumeCurrentDevice: Boolean
        ): CategoryItselfHandling {
            val dependsOnMinTime = timeInMillis
            val dateInTimezone = DateInTimezone.newInstance(timeInMillis, user.timeZone)
            val minuteInWeek = getMinuteOfWeek(timeInMillis, user.timeZone)
            val dayOfWeek = dateInTimezone.dayOfWeek
            val dayOfEpoch = dateInTimezone.dayOfEpoch
            val firstDayOfWeekAsEpochDay = dayOfEpoch - dayOfWeek
            val localDate = dateInTimezone.localDate

            val minRequiredBatteryLevel = if (batteryStatus.charging) categoryRelatedData.category.minBatteryLevelWhileCharging else categoryRelatedData.category.minBatteryLevelMobile
            val okByBattery = batteryStatus.level >= minRequiredBatteryLevel
            val dependsOnBatteryCharging = categoryRelatedData.category.minBatteryLevelWhileCharging != categoryRelatedData.category.minBatteryLevelMobile
            val dependsOnMinBatteryLevel = if (okByBattery) minRequiredBatteryLevel else Int.MIN_VALUE
            val dependsOnMaxBatteryLevel = if (okByBattery) Int.MAX_VALUE else minRequiredBatteryLevel - 1

            val okByTempBlocking = !categoryRelatedData.category.temporarilyBlocked || (
                    shouldTrustTimeTemporarily && categoryRelatedData.category.temporarilyBlockedEndTime != 0L && categoryRelatedData.category.temporarilyBlockedEndTime < timeInMillis )
            val dependsOnMaxTimeByTempBlocking = if (okByTempBlocking || !shouldTrustTimeTemporarily || categoryRelatedData.category.temporarilyBlockedEndTime == 0L) Long.MAX_VALUE else categoryRelatedData.category.temporarilyBlockedEndTime
            val missingNetworkTimeForDisableTempBlocking = categoryRelatedData.category.temporarilyBlocked && categoryRelatedData.category.temporarilyBlockedEndTime != 0L

            val areLimitsTemporarilyDisabled = shouldTrustTimeTemporarily && timeInMillis < user.user.disableLimitsUntil
            val dependsOnMaxTimeByTemporarilyDisabledLimits = if (areLimitsTemporarilyDisabled) user.user.disableLimitsUntil else Long.MAX_VALUE
            // ignore it for this case: val requiresTrustedTimeForTempLimitsDisabled = user.user.disableLimitsUntil != 0L

            val missingNetworkTimeForBlockedTimeAreas = !categoryRelatedData.category.blockedMinutesInWeek.dataNotToModify.isEmpty
            val okByBlockedTimeAreas = areLimitsTemporarilyDisabled || !categoryRelatedData.category.blockedMinutesInWeek.read(minuteInWeek)

            val relatedRules = if (areLimitsTemporarilyDisabled)
                emptyList()
            else
                RemainingTime.getRulesRelatedToDay(
                    dayOfWeek = dayOfWeek,
                    minuteOfDay = minuteInWeek % MinuteOfDay.LENGTH,
                    rules = categoryRelatedData.rules
            )

            val remainingTime = RemainingTime.getRemainingTime(
                    usedTimes = categoryRelatedData.usedTimes,
                    extraTime = categoryRelatedData.category.extraTimeInMillis,
                    rules = relatedRules,
                    dayOfWeek = dayOfWeek,
                    minuteOfDay = minuteInWeek % MinuteOfDay.LENGTH,
                    firstDayOfWeekAsEpochDay = firstDayOfWeekAsEpochDay
            )

            val remainingSessionDuration = RemainingSessionDuration.getRemainingSessionDuration(
                    rules = relatedRules,
                    minuteOfDay = minuteInWeek % MinuteOfDay.LENGTH,
                    dayOfWeek = dayOfWeek,
                    timestamp = timeInMillis,
                    durationsOfCategory = categoryRelatedData.durations
            )

            val missingNetworkTimeForRules = categoryRelatedData.rules.isNotEmpty()
            val okByTimeLimitRules = relatedRules.isEmpty() || (remainingTime != null && remainingTime.hasRemainingTime)
            val dependsOnMaxTimeByMinuteOfDay = (relatedRules.minBy { it.endMinuteOfDay }?.endMinuteOfDay ?: Int.MAX_VALUE).coerceAtMost(
                    categoryRelatedData.rules
                            .filter {
                                // related to today
                                it.dayMask.toInt() and (1 shl dayOfWeek) != 0 &&
                                        // will be applied later at this day
                                        it.startMinuteOfDay > minuteInWeek % MinuteOfDay.LENGTH
                            }
                            .minBy { it.startMinuteOfDay }?.startMinuteOfDay ?: Int.MAX_VALUE
            )
            val dependsOnMaxTimeByRules = if (dependsOnMaxTimeByMinuteOfDay != Int.MAX_VALUE) {
                localDate.atStartOfDay(ZoneId.of(user.user.timeZone)).plusMinutes(dependsOnMaxTimeByMinuteOfDay.toLong()).toEpochSecond() * 1000
            } else {
                localDate.plusDays(1).atStartOfDay(ZoneId.of(user.user.timeZone)).toEpochSecond() * 1000
            }
            val dependsOnMaxTimeBySessionDurationLimitItems = (
                    categoryRelatedData.durations.map { it.lastUsage + it.sessionPauseDuration } +
                            categoryRelatedData.durations.map { it.lastUsage + it.maxSessionDuration - it.lastSessionDuration }
                    )
                    .filter { it > timeInMillis }
                    .min() ?: Long.MAX_VALUE

            val okBySessionDurationLimits = remainingSessionDuration == null || remainingSessionDuration > 0
            val okByCurrentDevice = assumeCurrentDevice || (remainingTime == null && remainingSessionDuration == null)

            val dependsOnMaxTime = dependsOnMaxTimeByTempBlocking
                    .coerceAtMost(dependsOnMaxTimeByTemporarilyDisabledLimits)
                    .coerceAtMost(dependsOnMaxTimeByRules)
                    .coerceAtMost(dependsOnMaxTimeBySessionDurationLimitItems)
            val missingNetworkTime = !shouldTrustTimeTemporarily &&
                    (missingNetworkTimeForDisableTempBlocking || missingNetworkTimeForBlockedTimeAreas || missingNetworkTimeForRules)

            val shouldCountTime = relatedRules.isNotEmpty()
            val shouldCountExtraTime = remainingTime?.usingExtraTime == true
            val sessionDurationSlotsToCount = if (remainingSessionDuration != null && remainingSessionDuration <= 0)
                emptySet()
            else
                relatedRules.filter { it.sessionDurationLimitEnabled }.map {
                    AddUsedTimeActionItemSessionDurationLimitSlot(
                            startMinuteOfDay = it.startMinuteOfDay,
                            endMinuteOfDay = it.endMinuteOfDay,
                            maxSessionDuration = it.sessionDurationMilliseconds,
                            sessionPauseDuration = it.sessionPauseMilliseconds
                    )
                }.toSet()

            val maxTimeToAddByRegularTime = if (!shouldCountTime || remainingTime == null)
                Long.MAX_VALUE
            else if (shouldCountExtraTime)
                remainingTime.includingExtraTime
            else
                remainingTime.default
            val maxTimeToAddBySessionDuration = remainingSessionDuration ?: Long.MAX_VALUE
            val maxTimeToAdd = maxTimeToAddByRegularTime.coerceAtMost(maxTimeToAddBySessionDuration)

            val additionalTimeCountingSlots = if (shouldCountTime)
                relatedRules
                        .filterNot { it.appliesToWholeDay }
                        .map { AddUsedTimeActionItemAdditionalCountingSlot(it.startMinuteOfDay, it.endMinuteOfDay) }
                        .toSet()
            else
                emptySet()

            val blockAllNotifications = categoryRelatedData.category.blockAllNotifications

            return CategoryItselfHandling(
                    shouldCountTime = shouldCountTime,
                    shouldCountExtraTime = shouldCountExtraTime,
                    maxTimeToAdd = maxTimeToAdd,
                    sessionDurationSlotsToCount = sessionDurationSlotsToCount,
                    areLimitsTemporarilyDisabled = areLimitsTemporarilyDisabled,
                    okByBattery = okByBattery,
                    okByTempBlocking = okByTempBlocking,
                    okByBlockedTimeAreas = okByBlockedTimeAreas,
                    okByTimeLimitRules = okByTimeLimitRules,
                    okBySessionDurationLimits = okBySessionDurationLimits,
                    okByCurrentDevice = okByCurrentDevice,
                    missingNetworkTime = missingNetworkTime,
                    blockAllNotifications = blockAllNotifications,
                    remainingTime = remainingTime,
                    remainingSessionDuration = remainingSessionDuration,
                    additionalTimeCountingSlots = additionalTimeCountingSlots,
                    dependsOnMinTime = dependsOnMinTime,
                    dependsOnMaxTime = dependsOnMaxTime,
                    dependsOnBatteryCharging = dependsOnBatteryCharging,
                    dependsOnMinBatteryLevel = dependsOnMinBatteryLevel,
                    dependsOnMaxBatteryLevel = dependsOnMaxBatteryLevel,
                    createdWithCategoryRelatedData = categoryRelatedData,
                    createdWithBatteryStatus = batteryStatus,
                    createdWithTemporarilyTrustTime = shouldTrustTimeTemporarily,
                    createdWithAssumeCurrentDevice = assumeCurrentDevice,
                    createdWithUserRelatedData = user
            )
        }
    }

    val okBasic = okByBattery && okByTempBlocking && okByBlockedTimeAreas && okByTimeLimitRules && okBySessionDurationLimits && !missingNetworkTime
    val okAll = okBasic && okByCurrentDevice
    val shouldBlockActivities = !okAll
    val activityBlockingReason: BlockingReason = if (!okByBattery)
        BlockingReason.BatteryLimit
    else if (!okByTempBlocking)
        BlockingReason.TemporarilyBlocked
    else if (!okByBlockedTimeAreas)
        BlockingReason.BlockedAtThisTime
    else if (!okByTimeLimitRules)
        if (remainingTime?.hasRemainingTime == true)
            BlockingReason.TimeOverExtraTimeCanBeUsedLater
        else
            BlockingReason.TimeOver
    else if (!okBySessionDurationLimits)
        BlockingReason.SessionDurationLimit
    else if (!okByCurrentDevice)
        BlockingReason.RequiresCurrentDevice
    else if (missingNetworkTime)
        BlockingReason.MissingNetworkTime
    else
        BlockingReason.None

    // blockAllNotifications is only relevant if premium or local mode
    // val shouldBlockNotifications = !okAll || blockAllNotifications
    val shouldBlockAtSystemLevel = !okBasic

    fun isValid(
            categoryRelatedData: CategoryRelatedData,
            user: UserRelatedData,
            batteryStatus: BatteryStatus,
            shouldTrustTimeTemporarily: Boolean,
            timeInMillis: Long,
            assumeCurrentDevice: Boolean
    ): Boolean {
        if (
                categoryRelatedData != createdWithCategoryRelatedData || user != createdWithUserRelatedData ||
                shouldTrustTimeTemporarily != createdWithTemporarilyTrustTime || assumeCurrentDevice != createdWithAssumeCurrentDevice
        ) {
            return false
        }

        if (timeInMillis < dependsOnMinTime || timeInMillis > dependsOnMaxTime) {
            return false
        }

        if (batteryStatus.charging != this.createdWithBatteryStatus.charging && this.dependsOnBatteryCharging) {
            return false
        }

        if (batteryStatus.level < dependsOnMinBatteryLevel || batteryStatus.level > dependsOnMaxBatteryLevel) {
            return false
        }

        return true
    }
}
