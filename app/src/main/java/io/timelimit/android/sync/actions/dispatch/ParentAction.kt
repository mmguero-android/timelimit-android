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
        database.beginTransaction()

        try {
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
                    database.category().addCategory(Category(
                            id = action.categoryId,
                            childId = action.childId,
                            title = action.title,
                            // nothing blocked by default
                            blockedMinutesInWeek = ImmutableBitmask(BitSet()),
                            extraTimeInMillis = 0,
                            temporarilyBlocked = false,
                            baseVersion = "",
                            assignedAppsVersion = "",
                            timeLimitRulesVersion = "",
                            usedTimesVersion = "",
                            parentCategoryId = "",
                            blockAllNotifications = false
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

                    database.category().updateCategoryExtraTime(action.categoryId, action.newExtraTime)
                }
                is IncrementCategoryExtraTimeAction -> {
                    if (action.addedExtraTime < 0) {
                        throw IllegalArgumentException("invalid added extra time")
                    }

                    val category = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category ${action.categoryId} does not exist")

                    database.category().incrementCategoryExtraTime(action.categoryId, action.addedExtraTime)

                    if (category.parentCategoryId.isNotEmpty()) {
                        val parentCategory = database.category().getCategoryByIdSync(category.parentCategoryId)

                        if (parentCategory?.childId == category.childId) {
                            database.category().incrementCategoryExtraTime(parentCategory.id, action.addedExtraTime)
                        }
                    }

                    null
                }
                is UpdateCategoryTemporarilyBlockedAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTemporarilyBlocked(action.categoryId, action.blocked)
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
                            mailNotificationFlags = 0
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
                            applyToExtraTimeUsage = action.applyToExtraTimeUsage
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
            }.let { }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
