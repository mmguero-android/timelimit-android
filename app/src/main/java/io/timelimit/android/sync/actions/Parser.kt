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
package io.timelimit.android.sync.actions

import org.json.JSONObject

object ActionParser {
    fun parseAppLogicAction(action: JSONObject): AppLogicAction = when(action.getString(TYPE)) {
        AddUsedTimeAction.TYPE_VALUE -> AddUsedTimeAction.parse(action)
        AddInstalledAppsAction.TYPE_VALUE -> AddInstalledAppsAction.parse(action)
        RemoveInstalledAppsAction.TYPE_VALUE -> RemoveInstalledAppsAction.parse(action)
        TriedDisablingDeviceAdminAction.TYPE_VALUE -> TriedDisablingDeviceAdminAction
        SignOutAtDeviceAction.TYPE_VALUE -> SignOutAtDeviceAction
        UpdateAppActivitiesAction.TYPE_VALUE -> UpdateAppActivitiesAction.parse(action)
        UpdateDeviceStatusAction.TYPE_VALUE -> UpdateDeviceStatusAction.parse(action)
        AddUsedTimeActionVersion2.TYPE_VALUE -> AddUsedTimeActionVersion2.parse(action)
        else -> throw IllegalStateException()
    }

    fun parseParentAction(action: JSONObject): ParentAction = when(action.getString(TYPE)) {
        AddCategoryAppsAction.TYPE_VALUE -> AddCategoryAppsAction.parse(action)
        RemoveCategoryAppsAction.TYPE_VALUE -> RemoveCategoryAppsAction.parse(action)
        CreateCategoryAction.TYPE_VALUE -> CreateCategoryAction.parse(action)
        DeleteCategoryAction.TYPE_VALUE -> DeleteCategoryAction.parse(action)
        UpdateCategoryTitleAction.TYPE_VALUE -> UpdateCategoryTitleAction.parse(action)
        SetCategoryExtraTimeAction.TYPE_VALUE -> SetCategoryExtraTimeAction.parse(action)
        IncrementCategoryExtraTimeAction.TYPE_VALUE -> IncrementCategoryExtraTimeAction.parse(action)
        UpdateCategoryTemporarilyBlockedAction.TYPE_VALUE -> UpdateCategoryTemporarilyBlockedAction.parse(action)
        DeleteTimeLimitRuleAction.TYPE_VALUE -> DeleteTimeLimitRuleAction.parse(action)
        AddUserAction.TYPE_VALUE -> AddUserAction.parse(action)
        // actions without parser:
        // UpdateCategoryBlockedTimesAction
        // AddTimeLimitRuleAction
        // UpdateTimeLimitRuleAction
        // UpdateNetworkTimeVerificationAction
        // SetDeviceUserAction
        // SetUserDisableLimitsUntilAction
        // UpdateDeviceNameAction
        // RemoveUserAction
        // ChangeParentPasswordAction
        // IgnoreManipulationAction
        // SetKeepSignedInAction
        // SetCategoryForUnassignedApps
        // SetParentCategory
        // SetRelaxPrimaryDeviceAction
        // SetUserTimezoneAction
        // SetDeviceDefaultUserAction
        // SetChildPasswordAction
        // SetDeviceDefaultUserTimeoutAction
        // SetConsiderRebootManipulationAction
        // RenameChildAction
        // UpdateParentNotificationFlagsAction
        // UpdateCategoryBlockAllNotificationsAction
        // UpdateEnableActivityLevelBlocking
        // UpdateCategoryTimeWarningsAction
        // UpdateCategoryBatteryLimit
        // UpdateCategorySorting
        // UpdateUserFlagsAction
        else -> throw IllegalStateException()
    }
}
