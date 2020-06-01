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
package io.timelimit.android.sync.actions.dispatch

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.*
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.integration.platform.NewPermissionStatusUtil
import io.timelimit.android.integration.platform.ProtectionLevelUtil
import io.timelimit.android.integration.platform.RuntimePermissionStatusUtil
import io.timelimit.android.logic.ManipulationLogic
import io.timelimit.android.sync.actions.*

object LocalDatabaseAppLogicActionDispatcher {
    fun dispatchAppLogicActionSync(action: AppLogicAction, deviceId: String, database: Database, manipulationLogic: ManipulationLogic) {
        DatabaseValidation.assertDeviceExists(database, deviceId)

        database.runInTransaction {
            when(action) {
                is AddUsedTimeAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)!!
                    val parentCategoryEntry = if (categoryEntry.parentCategoryId.isNotEmpty())
                        database.category().getCategoryByIdSync(categoryEntry.parentCategoryId)
                    else
                        null

                    fun handleAddUsedTime(categoryId: String) {
                        // try to update
                        val updatedRows = database.usedTimes().addUsedTime(
                                categoryId = categoryId,
                                timeToAdd = action.timeToAdd,
                                dayOfEpoch = action.dayOfEpoch,
                                start = MinuteOfDay.MIN,
                                end = MinuteOfDay.MAX
                        )

                        if (updatedRows == 0) {
                            // create new entry

                            database.usedTimes().insertUsedTime(UsedTimeItem(
                                    categoryId = categoryId,
                                    dayOfEpoch = action.dayOfEpoch,
                                    usedMillis = action.timeToAdd.toLong(),
                                    startTimeOfDay = MinuteOfDay.MIN,
                                    endTimeOfDay = MinuteOfDay.MAX
                            ))
                        }


                        if (action.extraTimeToSubtract != 0) {
                            database.category().subtractCategoryExtraTime(
                                    categoryId = categoryId,
                                    removedExtraTime = action.extraTimeToSubtract
                            )
                        }
                    }

                    handleAddUsedTime(categoryEntry.id)

                    if (parentCategoryEntry?.childId == categoryEntry.childId) {
                        handleAddUsedTime(parentCategoryEntry.id)
                    }

                    null
                }
                is AddUsedTimeActionVersion2 -> {
                    action.items.forEach { item ->
                        database.category().getCategoryByIdSync(item.categoryId)
                                ?: throw CategoryNotFoundException()

                        fun handle(start: Int, end: Int) {
                            val updatedRows = database.usedTimes().addUsedTime(
                                    categoryId = item.categoryId,
                                    timeToAdd = item.timeToAdd,
                                    dayOfEpoch = action.dayOfEpoch,
                                    start = start,
                                    end = end
                            )

                            if (updatedRows == 0) {
                                // create new entry

                                database.usedTimes().insertUsedTime(UsedTimeItem(
                                        categoryId = item.categoryId,
                                        dayOfEpoch = action.dayOfEpoch,
                                        usedMillis = item.timeToAdd.toLong(),
                                        startTimeOfDay = start,
                                        endTimeOfDay = end
                                ))
                            }
                        }

                        handle(MinuteOfDay.MIN, MinuteOfDay.MAX)
                        item.additionalCountingSlots.forEach { handle(it.start, it.end) }

                        kotlin.run {
                            val hasTrustedTimestamp = action.trustedTimestamp != 0L

                            item.sessionDurationLimits.forEach { limit ->
                                val oldItem = database.sessionDuration().getSessionDurationItemSync(
                                        categoryId = item.categoryId,
                                        maxSessionDuration = limit.maxSessionDuration,
                                        sessionPauseDuration = limit.sessionPauseDuration,
                                        startMinuteOfDay = limit.startMinuteOfDay,
                                        endMinuteOfDay = limit.endMinuteOfDay
                                )

                                val newItem = oldItem?.copy(
                                        lastUsage = if (hasTrustedTimestamp) action.trustedTimestamp else oldItem.lastUsage,
                                        lastSessionDuration = if (hasTrustedTimestamp && action.trustedTimestamp - item.timeToAdd > oldItem.lastUsage + oldItem.sessionPauseDuration)
                                            item.timeToAdd.toLong()
                                        else
                                            oldItem.lastSessionDuration + item.timeToAdd.toLong()
                                ) ?: SessionDuration(
                                        categoryId = item.categoryId,
                                        maxSessionDuration = limit.maxSessionDuration,
                                        sessionPauseDuration = limit.sessionPauseDuration,
                                        startMinuteOfDay = limit.startMinuteOfDay,
                                        endMinuteOfDay = limit.endMinuteOfDay,
                                        lastSessionDuration = item.timeToAdd.toLong(),
                                        // this will cause a small loss of session durations
                                        lastUsage = if (hasTrustedTimestamp) action.trustedTimestamp else 0
                                )

                                if (oldItem == null) {
                                    database.sessionDuration().insertSessionDurationItemSync(newItem)
                                } else {
                                    database.sessionDuration().updateSessionDurationItemSync(newItem)
                                }
                            }
                        }

                        if (item.extraTimeToSubtract != 0) {
                            database.category().subtractCategoryExtraTime(
                                    categoryId = item.categoryId,
                                    removedExtraTime = item.extraTimeToSubtract
                            )
                        }
                    }

                    null
                }
                is AddInstalledAppsAction -> {
                    database.app().addAppsSync(
                            action.apps.map {
                                App(
                                        deviceId = deviceId,
                                        packageName = it.packageName,
                                        title = it.title,
                                        isLaunchable = it.isLaunchable,
                                        recommendation = it.recommendation
                                )
                            }
                    )
                }
                is RemoveInstalledAppsAction -> {
                    database.app().removeAppsByDeviceIdAndPackageNamesSync(
                            deviceId = deviceId,
                            packageNames = action.packageNames
                    )
                }
                is UpdateDeviceStatusAction -> {
                    var device = database.device().getDeviceByIdSync(deviceId)!!

                    if (action.newProtectionLevel != null) {
                        if (device.currentProtectionLevel != action.newProtectionLevel) {
                            device = device.copy(
                                    currentProtectionLevel = action.newProtectionLevel
                            )

                            if (ProtectionLevelUtil.toInt(action.newProtectionLevel) > ProtectionLevelUtil.toInt(device.highestProtectionLevel)) {
                                device = device.copy(
                                        highestProtectionLevel = action.newProtectionLevel
                                )
                            }

                            if (device.currentProtectionLevel != device.highestProtectionLevel) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.PROTECTION_LEVEL
                                )
                            }
                        }
                    }

                    if (action.newUsageStatsPermissionStatus != null) {
                        if (device.currentUsageStatsPermission != action.newUsageStatsPermissionStatus) {
                            device = device.copy(
                                    currentUsageStatsPermission = action.newUsageStatsPermissionStatus
                            )

                            if (RuntimePermissionStatusUtil.toInt(action.newUsageStatsPermissionStatus) > RuntimePermissionStatusUtil.toInt(device.highestUsageStatsPermission)) {
                                device = device.copy(
                                        highestUsageStatsPermission = action.newUsageStatsPermissionStatus
                                )
                            }

                            if (device.currentUsageStatsPermission != device.highestUsageStatsPermission) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.USAGE_STATS_ACCESS
                                )
                            }
                        }
                    }

                    if (action.newNotificationAccessPermission != null) {
                        if (device.currentNotificationAccessPermission != action.newNotificationAccessPermission) {
                            device = device.copy(
                                    currentNotificationAccessPermission = action.newNotificationAccessPermission
                            )

                            if (NewPermissionStatusUtil.toInt(action.newNotificationAccessPermission) > NewPermissionStatusUtil.toInt(device.highestNotificationAccessPermission)) {
                                device = device.copy(
                                        highestNotificationAccessPermission = action.newNotificationAccessPermission
                                )
                            }

                            if (device.currentNotificationAccessPermission != device.highestNotificationAccessPermission) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.NOTIFICATION_ACCESS
                                )
                            }
                        }
                    }

                    if (action.newOverlayPermission != null) {
                        if (device.currentOverlayPermission != action.newOverlayPermission) {
                            device = device.copy(
                                    currentOverlayPermission = action.newOverlayPermission
                            )

                            if (RuntimePermissionStatusUtil.toInt(action.newOverlayPermission) > RuntimePermissionStatusUtil.toInt(device.highestOverlayPermission)) {
                                device = device.copy(
                                        highestOverlayPermission = action.newOverlayPermission
                                )
                            }

                            if (device.currentOverlayPermission != device.highestOverlayPermission) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.OVERLAY_PERMISSION
                                )
                            }
                        }
                    }

                    if (action.newAccessibilityServiceEnabled != null) {
                        if (device.accessibilityServiceEnabled != action.newAccessibilityServiceEnabled) {
                            device = device.copy(
                                    accessibilityServiceEnabled = action.newAccessibilityServiceEnabled
                            )

                            if (action.newAccessibilityServiceEnabled) {
                                device = device.copy(
                                        wasAccessibilityServiceEnabled = true
                                )
                            }

                            if (device.accessibilityServiceEnabled != device.wasAccessibilityServiceEnabled) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.ACCESSIBILITY_SERVICE
                                )
                            }
                        }
                    }

                    if (action.newAppVersion != null) {
                        if (device.currentAppVersion != action.newAppVersion) {
                            device = device.copy(
                                    currentAppVersion = action.newAppVersion,
                                    highestAppVersion = Math.max(device.highestAppVersion, action.newAppVersion)
                            )

                            if (device.currentAppVersion != device.highestAppVersion) {
                                device = device.copy(
                                        hadManipulation = true,
                                        hadManipulationFlags = device.hadManipulationFlags or HadManipulationFlag.APP_VERSION
                                )
                            }
                        }
                    }

                    if (action.didReboot && device.considerRebootManipulation) {
                        device = device.copy(
                                manipulationDidReboot = true
                        )
                    }

                    if (action.isQOrLaterNow && !device.qOrLater) {
                        device = device.copy(qOrLater = true)
                    }

                    database.device().updateDeviceEntry(device)

                    if (device.hasActiveManipulationWarning) {
                        manipulationLogic.lockDeviceSync()
                    }

                    null
                }
                is TriedDisablingDeviceAdminAction -> {
                    database.device().updateDeviceEntry(
                            database.device().getDeviceByIdSync(
                                    database.config().getOwnDeviceIdSync()!!
                            )!!.copy(
                                    manipulationTriedDisablingDeviceAdmin = true
                            )
                    )

                    manipulationLogic.lockDeviceSync()

                    null
                }
                is SignOutAtDeviceAction -> {
                    val deviceEntry = database.device().getDeviceByIdSync(database.config().getOwnDeviceIdSync()!!)!!

                    if (deviceEntry.defaultUser.isEmpty()) {
                        throw IllegalStateException("can not sign out without configured default user")
                    }

                    LocalDatabaseParentActionDispatcher.dispatchParentActionSync(
                            SetDeviceUserAction(
                                    deviceId = deviceEntry.id,
                                    userId = deviceEntry.defaultUser
                            ),
                            database
                    )

                    null
                }
                is UpdateAppActivitiesAction -> {
                    if (action.updatedOrAddedActivities.isNotEmpty()) {
                        database.appActivity().addAppActivitiesSync(
                                action.updatedOrAddedActivities.map { item ->
                                    AppActivity(
                                            deviceId = deviceId,
                                            appPackageName = item.packageName,
                                            activityClassName = item.className,
                                            title = item.title
                                    )
                                }
                        )
                    }

                    if (action.removedActivities.isNotEmpty()) {
                        action.removedActivities.groupBy { it.first }.entries.forEach { item ->
                            val packageName = item.component1()
                            val activities = item.component2().map { it.second }

                            database.appActivity().deleteAppActivitiesSync(
                                    deviceId = deviceId,
                                    packageName = packageName,
                                    activities = activities
                            )
                        }
                    }

                    null
                }
            }.let {  }
        }
    }
}

class CategoryNotFoundException: NullPointerException()