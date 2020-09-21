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
package io.timelimit.android.ui.migrate_to_connected

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.*
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

data class OfflineModeStatus(
        val users: List<User>,
        val categories: List<Category>,
        val categoryApps: List<CategoryApp>,
        val device: Device,
        val rules: List<TimeLimitRule>,
        val usedTimes: List<UsedTimeItem>
) {
    companion object {
        private const val LOG_TAG = "OfflineModeStatus"

        fun query(database: Database): OfflineModeStatus = OfflineModeStatus(
            users = database.user().getAllUsersSync(),
            categories = database.category().getAllCategoriesSync(),
            categoryApps = database.categoryApp().getAllCategoryAppSync(),
            device = database.device().getDeviceByIdSync(database.config().getOwnDeviceIdSync()!!)!!,
            rules = database.timeLimitRules().getAllRulesSync(),
            usedTimes = database.usedTimes().getAllUsedTimeItemsSync()
        )
    }

    // limitations:
    // - child passwords are lost
    // - all parent users except the migrating one are lost
    suspend fun apply(
            authentication: ApplyActionParentPasswordAuthentication,
            appLogic: AppLogic,
            newDeviceId: String
    ) {
        suspend fun apply(action: ParentAction) {
            try {
                ApplyActionUtil.applyParentAction(
                        action = action,
                        database = appLogic.database,
                        authentication = authentication,
                        platformIntegration = appLogic.platformIntegration,
                        syncUtil = appLogic.syncUtil
                )
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not apply action $action", ex)
                }
            }
        }

        suspend fun apply(action: AppLogicAction) {
            try {
                ApplyActionUtil.applyAppLogicAction(
                        action = action,
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = false
                )
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not apply action $action", ex)
                }
            }
        }

        // create child users
        users.filter { it.type == UserType.Child }.forEach { child ->
            apply(
                    AddUserAction(
                            name = child.name,
                            timeZone = child.timeZone,
                            userId = child.id,
                            userType = UserType.Child,
                            password = null
                    )
            )

            // disable limits until
            if (child.disableLimitsUntil != 0L) {
                apply(
                        SetUserDisableLimitsUntilAction(
                                childId = child.id,
                                timestamp = child.disableLimitsUntil
                        )
                )
            }

            // create categories
            val childCategories = categories.filter { it.childId == child.id }

            childCategories.forEach { category ->
                apply(
                        CreateCategoryAction(
                                childId = child.id,
                                categoryId = category.id,
                                title = category.title
                        )
                )

                if (!category.blockedMinutesInWeek.dataNotToModify.isEmpty) {
                    apply(
                            UpdateCategoryBlockedTimesAction(
                                    categoryId = category.id,
                                    blockedTimes = category.blockedMinutesInWeek
                            )
                    )
                }

                if (category.extraTimeInMillis != 0L) {
                    apply(
                            SetCategoryExtraTimeAction(
                                    categoryId = category.id,
                                    newExtraTime = category.extraTimeInMillis,
                                    extraTimeDay = category.extraTimeDay
                            )
                    )
                }

                if (category.temporarilyBlocked) {
                    apply(
                            UpdateCategoryTemporarilyBlockedAction(
                                    categoryId = category.id,
                                    blocked = true,
                                    endTime = category.temporarilyBlockedEndTime
                            )
                    )
                }

                if (category.minBatteryLevelMobile > 0 || category.minBatteryLevelWhileCharging > 0) {
                    apply(
                            UpdateCategoryBatteryLimit(
                                    categoryId = category.id,
                                    mobileLimit = category.minBatteryLevelMobile,
                                    chargingLimit = category.minBatteryLevelWhileCharging
                            )
                    )
                }

                if (category.timeWarnings != 0) {
                    apply(
                            UpdateCategoryTimeWarningsAction(
                                    categoryId = category.id,
                                    enable = true,
                                    flags = category.timeWarnings
                            )
                    )
                }

                if (category.blockAllNotifications) {
                    apply(
                            UpdateCategoryBlockAllNotificationsAction(
                                    categoryId = category.id,
                                    blocked = true
                            )
                    )
                }

                // add category apps
                val thisCategoryApps = categoryApps.filter { it.categoryId == category.id }
                if (thisCategoryApps.isNotEmpty()) {
                    apply(AddCategoryAppsAction(
                            categoryId = category.id,
                            packageNames = thisCategoryApps.map { it.packageName }
                    ))
                }

                // add used times
                val thisUsedTimes = usedTimes.filter { it.categoryId == category.id }
                thisUsedTimes.forEach { usedTime ->
                    apply(AddUsedTimeActionVersion2(
                            dayOfEpoch = usedTime.dayOfEpoch,
                            items = listOf(
                                    AddUsedTimeActionItem(
                                            categoryId = category.id,
                                            timeToAdd = usedTime.usedMillis.toInt(),
                                            extraTimeToSubtract = 0,
                                            sessionDurationLimits = emptySet(),
                                            additionalCountingSlots = emptySet()
                                    )
                            ),
                            trustedTimestamp = 0
                    ))
                }

                // add time limit rules
                val thisRules = rules.filter { it.categoryId == category.id }
                thisRules.forEach { rule -> apply(CreateTimeLimitRuleAction(rule)) }
            }

            // parent categories
            childCategories.forEach { category ->
                if (category.parentCategoryId != "") {
                    apply(SetParentCategory(
                            categoryId = category.id,
                            parentCategory = category.parentCategoryId
                    ))
                }
            }

            // category for not assigned apps
            if (child.categoryForNotAssignedApps != "") {
                apply(SetCategoryForUnassignedApps(
                        childId = child.id,
                        categoryId = child.categoryForNotAssignedApps
                ))
            }
        }

        // update device config
        if (users.find { it.type == UserType.Child && it.id == device.currentUserId } != null) {
            apply(SetDeviceUserAction(
                    deviceId = newDeviceId,
                    userId = device.currentUserId
            ))
        }

        apply(UpdateNetworkTimeVerificationAction(
                deviceId = newDeviceId,
                mode = device.networkTime
        ))

        if (users.find { it.type == UserType.Child && it.id == device.defaultUser } != null) {
            apply(SetDeviceDefaultUserAction(
                    deviceId = newDeviceId,
                    defaultUserId = device.defaultUser
            ))
        }

        apply(SetDeviceDefaultUserTimeoutAction(
                deviceId = newDeviceId,
                timeout = device.defaultUserTimeout
        ))

        if (device.considerRebootManipulation) {
            apply(SetConsiderRebootManipulationAction(
                    deviceId = newDeviceId,
                    considerRebootManipulation = true
            ))
        }

        if (device.enableActivityLevelBlocking) {
            apply(UpdateEnableActivityLevelBlocking(
                    deviceId = newDeviceId,
                    enable = true
            ))
        }
    }
}