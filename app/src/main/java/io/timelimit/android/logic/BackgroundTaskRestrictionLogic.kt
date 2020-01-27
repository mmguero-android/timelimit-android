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

import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.User
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.extension.isCategoryAllowed

object BackgroundTaskRestrictionLogic {
    suspend fun getHandling(
            foregroundAppPackageName: String?,
            foregroundAppActivityName: String?,
            pauseForegroundAppBackgroundLoop: Boolean,
            temporarilyAllowedApps: List<String>,
            categories: List<Category>,
            activityLevelBlocking: Boolean,
            deviceUserEntry: User,
            batteryStatus: BatteryStatus,
            shouldTrustTimeTemporarily: Boolean,
            nowTimestamp: Long,
            minuteOfWeek: Int,
            cache: BackgroundTaskLogicCache,
            result: BackgroundTaskRestrictionLogicResult
    ) {
        if (pauseForegroundAppBackgroundLoop) {
            result.status = BackgroundTaskLogicAppStatus.BackgroundLogicPaused

            return
        } else if (
                (foregroundAppPackageName == BuildConfig.APPLICATION_ID) ||
                (foregroundAppPackageName != null && AndroidIntegrationApps.ignoredApps[foregroundAppPackageName].let {
                    when (it) {
                        null -> false
                        AndroidIntegrationApps.IgnoredAppHandling.Ignore -> true
                        AndroidIntegrationApps.IgnoredAppHandling.IgnoreOnStoreOtherwiseWhitelistAndDontDisable -> BuildConfig.storeCompilant
                    }
                }) ||
                (foregroundAppPackageName != null && foregroundAppActivityName != null &&
                        AndroidIntegrationApps.shouldIgnoreActivity(foregroundAppPackageName, foregroundAppActivityName))
        ) {
            result.status = BackgroundTaskLogicAppStatus.InternalWhitelist

            return
        } else if (foregroundAppPackageName != null && temporarilyAllowedApps.contains(foregroundAppPackageName)) {
            result.status = BackgroundTaskLogicAppStatus.TemporarilyAllowed

            return
        } else if (foregroundAppPackageName != null) {
            val categoryIds = categories.map { it.id }

            val appCategory = run {
                val appLevelCategoryLive = cache.appCategories.get(foregroundAppPackageName to categoryIds)

                if (activityLevelBlocking && foregroundAppActivityName != null) {
                    val appActivityCategoryLive = cache.appCategories.get("$foregroundAppPackageName:$foregroundAppActivityName" to categoryIds)

                    appActivityCategoryLive.waitForNullableValue() ?: appLevelCategoryLive.waitForNullableValue()
                } else {
                    appLevelCategoryLive.waitForNullableValue()
                }
            }

            val category = categories.find { it.id == appCategory?.categoryId }
                    ?: categories.find { it.id == deviceUserEntry.categoryForNotAssignedApps }
            val parentCategory = categories.find { it.id == category?.parentCategoryId }

            result.categoryId = category?.id
            result.parentCategoryId = parentCategory?.id

            if (category == null) {
                result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                return
            } else if ((!batteryStatus.isCategoryAllowed(category)) || (!batteryStatus.isCategoryAllowed(parentCategory))) {
                result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                return
            } else if (category.temporarilyBlocked or (parentCategory?.temporarilyBlocked == true)) {
                result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                return
            } else {
                // disable time limits temporarily feature
                if (shouldTrustTimeTemporarily && nowTimestamp < deviceUserEntry.disableLimitsUntil) {
                    result.status = BackgroundTaskLogicAppStatus.LimitsDisabled

                    return
                } else if (
                // check blocked time areas
                // directly blocked
                        (category.blockedMinutesInWeek.read(minuteOfWeek)) or
                        (parentCategory?.blockedMinutesInWeek?.read(minuteOfWeek) == true) or
                        // or no safe time
                        (
                                (
                                        (category.blockedMinutesInWeek.dataNotToModify.isEmpty == false) or
                                                (parentCategory?.blockedMinutesInWeek?.dataNotToModify?.isEmpty == false)
                                        ) &&
                                        (!shouldTrustTimeTemporarily)
                                )
                ) {
                    result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                    return
                } else {
                    // check time limits
                    val rules = cache.timeLimitRules.get(category.id).waitForNonNullValue()
                    val parentRules = parentCategory?.let {
                        cache.timeLimitRules.get(it.id).waitForNonNullValue()
                    } ?: emptyList()

                    if (rules.isEmpty() and parentRules.isEmpty()) {
                        // unlimited
                        result.status = BackgroundTaskLogicAppStatus.AllowedNoTimelimit

                        return
                    } else {
                        val isCurrentDevice = cache.isThisDeviceTheCurrentDeviceLive.read().waitForNonNullValue()

                        if (!isCurrentDevice) {
                            result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                            return
                        } else if (shouldTrustTimeTemporarily) {
                            result.status = BackgroundTaskLogicAppStatus.AllowedCountAndCheckTime

                            return
                        } else {
                            result.status = BackgroundTaskLogicAppStatus.ShouldBlock

                            return
                        }
                    }
                }
            }
        } else {
            result.status = BackgroundTaskLogicAppStatus.Idle
        }
    }
}

class BackgroundTaskRestrictionLogicResult {
    var status: BackgroundTaskLogicAppStatus = BackgroundTaskLogicAppStatus.Idle
    var categoryId: String? = null
    var parentCategoryId: String? = null

    fun reset() {
        status = BackgroundTaskLogicAppStatus.Idle
        categoryId = null
        parentCategoryId = null
    }
}

enum class BackgroundTaskLogicAppStatus {
    ShouldBlock,
    BackgroundLogicPaused,
    InternalWhitelist,
    TemporarilyAllowed,
    LimitsDisabled,
    AllowedNoTimelimit,
    AllowedCountAndCheckTime,
    Idle
}