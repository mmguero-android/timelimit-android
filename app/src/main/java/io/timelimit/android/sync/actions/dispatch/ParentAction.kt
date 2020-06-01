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
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.sync.actions.*
import java.util.*

object LocalDatabaseParentActionDispatcher {
    fun dispatchParentActionSync(action: ParentAction, database: Database) {
        database.runInTransaction {
            when (action) {
                is AddCategoryAppsAction -> {
                    // validate that the category exists
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    // remove same apps from other categories of the same child
                    val allCategoriesOfChild = database.category().getCategoriesByChildIdSync(categoryEntry.childId)

                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = allCategoriesOfChild.map { it.id }
                    )

                    // add the apps to the new category
                    database.categoryApp().addCategoryAppsSync(
                            action.packageNames.map {
                                CategoryApp(
                                        categoryId = action.categoryId,
                                        packageName = it
                                )
                            }
                    )
                }
                is RemoveCategoryAppsAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // remove the apps from the category
                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = listOf(action.categoryId)
                    )
                }
                is CreateCategoryAction -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    // create the category
                    val sort = database.category().getNextCategorySortKeyByChildId(action.childId)

                    database.category().addCategory(Category(
                            id = action.categoryId,
                            childId = action.childId,
                            title = action.title,
                            // nothing blocked by default
                            blockedMinutesInWeek = ImmutableBitmask(BitSet()),
                            extraTimeInMillis = 0,
                            extraTimeDay = -1,
                            temporarilyBlocked = false,
                            temporarilyBlockedEndTime = 0,
                            baseVersion = "",
                            assignedAppsVersion = "",
                            timeLimitRulesVersion = "",
                            usedTimesVersion = "",
                            parentCategoryId = "",
                            blockAllNotifications = false,
                            timeWarnings = 0,
                            minBatteryLevelWhileCharging = 0,
                            minBatteryLevelMobile = 0,
                            sort = sort
                    ))
                }
                is DeleteCategoryAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // delete all related data and the category
                    database.timeLimitRules().deleteTimeLimitRulesByCategory(action.categoryId)
                    database.usedTimes().deleteUsedTimeItems(action.categoryId)
                    database.categoryApp().deleteCategoryAppsByCategoryId(action.categoryId)
                    database.user().removeAsCategoryForUnassignedApps(action.categoryId)
                    database.category().deleteCategory(action.categoryId)
                }
                is UpdateCategoryTitleAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTitle(
                            categoryId = action.categoryId,
                            newTitle = action.newTitle
                    )
                }
                is SetCategoryExtraTimeAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    if (action.newExtraTime < 0) {
                        throw IllegalArgumentException("invalid new extra time")
                    }

                    if (action.extraTimeDay < -1) {
                        throw IllegalArgumentException()
                    }

                    database.category().updateCategoryExtraTime(action.categoryId, action.newExtraTime, action.extraTimeDay)
                }
                is IncrementCategoryExtraTimeAction -> {
                    if (action.addedExtraTime < 0) {
                        throw IllegalArgumentException("invalid added extra time")
                    }

                    val category = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category ${action.categoryId} does not exist")

                    fun handleExtratimeIncrement(category: Category) {
                        database.category().updateCategorySync(
                                category.copy(
                                        extraTimeDay = action.extraTimeDay,
                                        extraTimeInMillis = category.getExtraTime(action.extraTimeDay) + action.addedExtraTime
                                )
                        )
                    }

                    handleExtratimeIncrement(category)

                    if (category.parentCategoryId.isNotEmpty()) {
                        val parentCategory = database.category().getCategoryByIdSync(category.parentCategoryId)

                        if (parentCategory?.childId == category.childId) {
                            handleExtratimeIncrement(parentCategory)
                        }
                    }

                    null
                }
                is UpdateCategoryTemporarilyBlockedAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTemporarilyBlocked(
                            categoryId = action.categoryId,
                            blocked = action.blocked,
                            endTime = if (action.blocked) action.endTime ?: 0 else 0
                    )
                }
                is DeleteTimeLimitRuleAction -> {
                    DatabaseValidation.assertTimelimitRuleExists(database, action.ruleId)

                    database.timeLimitRules().deleteTimeLimitRuleByIdSync(action.ruleId)
                }
                is AddUserAction -> {
                    database.user().addUserSync(User(
                            id = action.userId,
                            name = action.name,
                            type = action.userType,
                            timeZone = action.timeZone,
                            password = if (action.password == null) "" else action.password.parentPasswordHash,
                            secondPasswordSalt = if (action.password == null) "" else action.password.parentPasswordSecondSalt,
                            disableLimitsUntil = 0,
                            mail = "",
                            currentDevice = "",
                            categoryForNotAssignedApps = "",
                            relaxPrimaryDevice = false,
                            mailNotificationFlags = 0,
                            blockedTimes = ImmutableBitmask(BitSet()),
                            flags = 0
                    ))
                }
                is UpdateCategoryBlockedTimesAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryBlockedTimes(action.categoryId, action.blockedTimes)
                }
                is CreateTimeLimitRuleAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.rule.categoryId)

                    database.timeLimitRules().addTimeLimitRule(action.rule)
                }
                is UpdateNetworkTimeVerificationAction -> {
                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    database.device().updateNetworkTimeVerification(
                            deviceId = action.deviceId,
                            mode = action.mode
                    )
                }
                is UpdateTimeLimitRuleAction -> {
                    val oldRule = database.timeLimitRules().getTimeLimitRuleByIdSync(action.ruleId)!!

                    database.timeLimitRules().updateTimeLimitRule(oldRule.copy(
                            maximumTimeInMillis = action.maximumTimeInMillis,
                            dayMask = action.dayMask,
                            applyToExtraTimeUsage = action.applyToExtraTimeUsage,
                            startMinuteOfDay = action.start,
                            endMinuteOfDay = action.end,
                            sessionDurationMilliseconds = action.sessionDurationMilliseconds,
                            sessionPauseMilliseconds = action.sessionPauseMilliseconds
                    ))
                }
                is SetDeviceUserAction -> {
                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    if (action.userId != "") {
                        DatabaseValidation.assertUserExists(database, action.userId)
                    }

                    database.device().updateDeviceUser(
                            deviceId = action.deviceId,
                            userId = action.userId
                    )
                }
                is SetUserDisableLimitsUntilAction -> {
                    val affectedRows = database.user().updateDisableChildUserLimitsUntil(
                            childId = action.childId,
                            timestamp = action.timestamp
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided user id does not exist")
                    }

                    null
                }
                is UpdateDeviceNameAction -> {
                    val affectedRows = database.device().updateDeviceName(
                            deviceId = action.deviceId,
                            name = action.name
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided device id was invalid")
                    }

                    null
                }
                is RemoveUserAction -> {
                    // authentication is not checked locally, only at the server

                    val userToDelete = database.user().getUserByIdSync(action.userId)!!

                    if (userToDelete.type == UserType.Parent) {
                        val currentParents = database.user().getParentUsersSync()

                        if (currentParents.size <= 1) {
                            throw IllegalStateException("would delete last parent")
                        }
                    }

                    if (userToDelete.type == UserType.Child) {
                        val categories = database.category().getCategoriesByChildIdSync(userToDelete.id)

                        categories.forEach {
                            category ->

                            dispatchParentActionSync(
                                    DeleteCategoryAction(
                                            categoryId = category.id
                                    ),
                                    database
                            )
                        }
                    }

                    database.device().unassignCurrentUserFromAllDevices(action.userId)

                    database.user().deleteUsersByIds(listOf(action.userId))
                }
                is ChangeParentPasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentUserId)

                    if (userEntry == null || userEntry.type != UserType.Parent) {
                        throw IllegalArgumentException("invalid user entry")
                    }

                    // the client does not have the data to check the integrity

                    database.user().updateUserSync(
                            userEntry.copy(
                                    password = action.newPasswordFirstHash,
                                    secondPasswordSalt = action.newPasswordSecondSalt
                            )
                    )
                }
                is IgnoreManipulationAction -> {
                    val originalDeviceEntry = database.device().getDeviceByIdSync(action.deviceId)!!
                    var deviceEntry = originalDeviceEntry

                    if (action.ignoreDeviceAdminManipulation) {
                        deviceEntry = deviceEntry.copy(highestProtectionLevel = deviceEntry.currentProtectionLevel)
                    }

                    if (action.ignoreDeviceAdminManipulationAttempt) {
                        deviceEntry = deviceEntry.copy(manipulationTriedDisablingDeviceAdmin = false)
                    }

                    if (action.ignoreAppDowngrade) {
                        deviceEntry = deviceEntry.copy(highestAppVersion = deviceEntry.currentAppVersion)
                    }

                    if (action.ignoreNotificationAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestNotificationAccessPermission = deviceEntry.currentNotificationAccessPermission)
                    }

                    if (action.ignoreUsageStatsAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestUsageStatsPermission = deviceEntry.currentUsageStatsPermission)
                    }

                    if (action.ignoreOverlayPermissionManipulation) {
                        deviceEntry = deviceEntry.copy(highestOverlayPermission = deviceEntry.currentOverlayPermission)
                    }

                    if (action.ignoreAccessibilityServiceManipulation) {
                        deviceEntry = deviceEntry.copy(wasAccessibilityServiceEnabled = deviceEntry.accessibilityServiceEnabled)
                    }

                    if (action.ignoreReboot) {
                        deviceEntry = deviceEntry.copy(manipulationDidReboot = false)
                    }

                    if (action.ignoreHadManipulation) {
                        deviceEntry = deviceEntry.copy(hadManipulation = false)
                    }

                    if (action.ignoreHadManipulationFlags != 0L && deviceEntry.hadManipulationFlags != 0L) {
                        val newFlags = deviceEntry.hadManipulationFlags and (action.ignoreHadManipulationFlags.inv())

                        deviceEntry = deviceEntry.copy(hadManipulationFlags = newFlags)

                        if (newFlags == 0L) {
                            deviceEntry = deviceEntry.copy(hadManipulation = false)
                        }
                    }

                    database.device().updateDeviceEntry(deviceEntry)
                }
                is SetKeepSignedInAction -> {
                    database.device().updateKeepSignedIn(
                            deviceId = action.deviceId,
                            keepSignedIn = action.keepSignedIn
                    )
                }
                is SetCategoryForUnassignedApps -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    if (action.categoryId.isNotEmpty()) {
                        val category = database.category().getCategoryByIdSync(action.categoryId)!!

                        if (category.childId != action.childId) {
                            throw IllegalArgumentException("category does not belong to child")
                        }
                    }

                    database.user().updateCategoryForUnassignedApps(
                            categoryId = action.categoryId,
                            childId = action.childId
                    )
                }
                is SetParentCategory -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId)!!

                    if (action.parentCategory.isNotEmpty()) {
                        val categories = database.category().getCategoriesByChildIdSync(category.childId)

                        val parentCategoryItem = categories.find { it.id == action.parentCategory }
                                ?: throw IllegalArgumentException("selected parent category does not exist")

                        if (parentCategoryItem.parentCategoryId.isNotEmpty()) {
                            throw IllegalArgumentException("can not set a category as parent which itself has got a parent")
                        }

                        if (categories.find { it.parentCategoryId == action.categoryId } != null) {
                            throw IllegalArgumentException("can not make category a child category if it is already a parent category")
                        }
                    }

                    database.category().updateParentCategory(
                            categoryId = action.categoryId,
                            parentCategoryId = action.parentCategory
                    )
                }
                is SetSendDeviceConnected -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("invalid device id for setting send device connected: ${action.deviceId}")

                    val newDeviceEntry = deviceEntry.copy(
                            showDeviceConnected = action.enable
                    )

                    if (newDeviceEntry != deviceEntry) {
                        database.device().updateDeviceEntry(newDeviceEntry)
                    }

                    null
                }
                is SetRelaxPrimaryDeviceAction -> {
                    val userEntry = database.user().getUserByIdSync(action.userId)
                            ?: throw IllegalArgumentException("invalid user id for setting relay primary device: ${action.userId}")

                    val newUserEntry = userEntry.copy(
                            relaxPrimaryDevice = action.relax
                    )

                    if (newUserEntry != userEntry) {
                        database.user().updateUserSync(newUserEntry)
                    }

                    null
                }
                is SetUserTimezoneAction -> {
                    DatabaseValidation.assertUserExists(database, action.userId)

                    database.user().updateUserTimezone(
                            userId = action.userId,
                            timezone = action.timezone
                    )
                }
                is SetDeviceDefaultUserAction -> {
                    if (action.defaultUserId.isNotEmpty()) {
                        DatabaseValidation.assertUserExists(database, action.defaultUserId)
                    }

                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    database.device().updateDeviceDefaultUser(
                            deviceId = action.deviceId,
                            defaultUserId = action.defaultUserId
                    )
                }
                is SetChildPasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(action.childId)

                    if (userEntry?.type != UserType.Child) {
                        throw IllegalArgumentException("can not set child password for a child which does not exist")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    password = action.newPassword.parentPasswordHash,
                                    secondPasswordSalt = action.newPassword.parentPasswordSecondSalt
                            )
                    )
                }
                is SetDeviceDefaultUserTimeoutAction -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(deviceEntry.copy(
                            defaultUserTimeout = action.timeout
                    ))
                }
                is SetConsiderRebootManipulationAction -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(
                            deviceEntry.copy(
                                    considerRebootManipulation = action.considerRebootManipulation
                            )
                    )
                }
                is RenameChildAction -> {
                    val userEntry = database.user().getUserByIdSync(action.childId)

                    if (userEntry?.type != UserType.Child) {
                        throw IllegalArgumentException("can not set child password for a child which does not exist")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    name = action.newName
                            )
                    )
                }
                is UpdateParentNotificationFlagsAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentId)

                    if (userEntry?.type != UserType.Parent) {
                        throw IllegalArgumentException("can not set mail notification flags for a parent which does not exist")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    mailNotificationFlags = if (action.set)
                                        userEntry.mailNotificationFlags or action.flags
                                    else
                                        userEntry.mailNotificationFlags and (action.flags.inv())
                            )
                    )
                }
                is UpdateCategoryBlockAllNotificationsAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("can not update notification blocking for non exsistent category")

                    database.category().updateCategorySync(
                            categoryEntry.copy(
                                    blockAllNotifications = action.blocked
                            )
                    )
                }
                is UpdateEnableActivityLevelBlocking -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(
                            deviceEntry.copy(
                                    enableActivityLevelBlocking = action.enable
                            )
                    )
                }
                is UpdateCategoryTimeWarningsAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category not found")

                    val modified = if (action.enable)
                        categoryEntry.copy(
                                timeWarnings = categoryEntry.timeWarnings or action.flags
                        )
                    else
                        categoryEntry.copy(
                                timeWarnings = categoryEntry.timeWarnings and (action.flags.inv())
                        )

                    if (modified != categoryEntry) {
                        database.category().updateCategorySync(modified)
                    }

                    null
                }
                is UpdateParentBlockedTimesAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentId)

                    if (userEntry?.type != UserType.Parent) {
                        throw IllegalArgumentException("no valid parent id")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    blockedTimes = action.blockedTimes
                            )
                    )
                }
                is ResetParentBlockedTimesAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentId)

                    if (userEntry?.type != UserType.Parent) {
                        throw IllegalArgumentException("no valid parent id")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    blockedTimes = ImmutableBitmask(BitSet())
                            )
                    )
                }
                is UpdateCategoryBatteryLimit -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("can not update battery limit for a category which does not exist")

                    database.category().updateCategorySync(
                            categoryEntry.copy(
                                    minBatteryLevelWhileCharging = action.chargingLimit ?: categoryEntry.minBatteryLevelWhileCharging,
                                    minBatteryLevelMobile = action.mobileLimit ?: categoryEntry.minBatteryLevelMobile
                            )
                    )
                }
                is UpdateCategorySortingAction -> {
                    // no validation here:
                    // - only parents can do it
                    // - using it over categories which don't belong together destroys the sorting for both,
                    //   but does not cause any trouble

                    action.categoryIds.forEachIndexed { index, categoryId ->
                        database.category().updateCategorySorting(categoryId, index)
                    }
                }
                is UpdateUserFlagsAction -> {
                    val user = database.user().getUserByIdSync(action.userId)!!

                    val updatedUser = user.copy(
                            flags = (user.flags and action.modifiedBits.inv()) or action.newValues
                    )

                    database.user().updateUserSync(updatedUser)
                }
            }.let { }
        }
    }
}
