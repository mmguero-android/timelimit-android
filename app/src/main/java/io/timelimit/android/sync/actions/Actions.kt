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
package io.timelimit.android.sync.actions

import android.util.JsonReader
import android.util.JsonWriter
import io.timelimit.android.crypto.HexString
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson
import io.timelimit.android.data.model.*
import io.timelimit.android.integration.platform.*
import io.timelimit.android.sync.network.ParentPassword
import io.timelimit.android.sync.validation.ListValidation
import org.json.JSONObject
import java.util.*

// Tip: [Ctrl] + [A] and [Ctrl] + [Shift] + [Minus] make this file easy to read

/*
 * The actions describe things that happen.
 * The same actions (should) result in the same state if applied in the same order.
 * This actions are used for the remote control and monitoring.
 *
 * When an action is executed:
 * 1. It's validated (the action classes only hold the actions without any validation)
 * 2. It's applied at the clients database
 * (end here if in local only mode)
 * 3. It's queued for uploading
 * 4. The client sends all actions to the server
 * 5. The client pulls the status from the server
 *
 * When there was no connection for a longer time, then actions can be merged (only for adding used time)
 *
 * ID HANDLING
 *
 * The client generates IDs by itself randomly. The Server uses the same IDs.
 * This implies that the Server internally prefixes everything with the id of the family.
 *
 * CONFLICT HANDLING
 *
 * - the client attempts to upload his changes first
 * - failed actions are ignored -> destroy version number at the client to trigger a full sync
 *
 * VERSION NUMBERS
 *
 * - are random strings
 * - are kept at actions at the client
 * - are modified at the client when dispatching an action failed (to trigger an query)
 * - version number for
 *   - devices (device list + the status of the devices) - saved at the config table
 *   - installed apps - saved per device at the device table
 *   - categories (status + config of the category as it is) - saved per category in the category table
 *   - categories (assigned apps) - saved per category in the category table
 *   - categories (time limit rules) - saved per category in the category table
 *   - users as whole - saved at the config table
 *   - used time items per category - saved at the category table
 *
 * HANDLING LAGGY CONNECTIONS
 *
 * - client marks actions as once sent before sending them
 * - after that no merging may occur to this actions
 * - each actions gets an number (numbers are ascending)
 * - server does not process actions when the device already sent one with this number
 * - server still sends "new" status of this actions
 * - client deletes actions when server confirmed receiving them
 */

// actions which "implement" this are not synchronized
// interface LocalOnlyAction

// base types
sealed class Action {
    abstract fun serialize(writer: JsonWriter)
}

sealed class AppLogicAction: Action()
sealed class ParentAction: Action()
sealed class ChildAction: Action()

//
// now the concrete actions
//

const val TYPE = "type"

data class AddUsedTimeAction(val categoryId: String, val dayOfEpoch: Int, val timeToAdd: Int, val extraTimeToSubtract: Int): AppLogicAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (dayOfEpoch < 0) {
            throw IllegalArgumentException()
        }

        if (timeToAdd < 0) {
            throw IllegalArgumentException()
        }

        if (extraTimeToSubtract < 0) {
            throw IllegalArgumentException()
        }
    }

    companion object {
        const val TYPE_VALUE = "ADD_USED_TIME"
        private const val CATEGORY_ID = "categoryId"
        private const val DAY_OF_EPOCH = "day"
        private const val TIME_TO_ADD = "timeToAdd"
        private const val EXTRA_TIME_TO_SUBTRACT = "extraTimeToSubtract"

        fun parse(action: JSONObject) = AddUsedTimeAction(
                categoryId = action.getString(CATEGORY_ID),
                dayOfEpoch = action.getInt(DAY_OF_EPOCH),
                timeToAdd = action.getInt(TIME_TO_ADD),
                extraTimeToSubtract = action.getInt(EXTRA_TIME_TO_SUBTRACT)
        )
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(this.categoryId)
        writer.name(DAY_OF_EPOCH).value(dayOfEpoch)
        writer.name(TIME_TO_ADD).value(this.timeToAdd)
        writer.name(EXTRA_TIME_TO_SUBTRACT).value(this.extraTimeToSubtract)

        writer.endObject()
    }
}

// data class ClearTemporarilyAllowedAppsAction(val deviceId: String): AppLogicAction(), LocalOnlyAction

data class InstalledApp(val packageName: String, val title: String, val isLaunchable: Boolean, val recommendation: AppRecommendation) {
    companion object {
        private const val PACKAGE_NAME = "packageName"
        private const val TITLE = "title"
        private const val IS_LAUNCHABLE = "isLaunchable"
        private const val RECOMMENDATION = "recommendation"

        fun parse(item: JSONObject) = InstalledApp(
                packageName = item.getString(PACKAGE_NAME),
                title = item.getString(TITLE),
                isLaunchable = item.getBoolean(IS_LAUNCHABLE),
                recommendation = AppRecommendationJson.parse(item.getString(RECOMMENDATION))
        )

        fun parse(reader: JsonReader): InstalledApp {
            var packageName: String? = null
            var title: String? = null
            var isLaunchable: Boolean? = null
            var recommendation: AppRecommendation? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    PACKAGE_NAME -> packageName = reader.nextString()
                    TITLE -> title = reader.nextString()
                    IS_LAUNCHABLE -> isLaunchable = reader.nextBoolean()
                    RECOMMENDATION -> recommendation = AppRecommendationJson.parse(reader.nextString())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return InstalledApp(
                    packageName = packageName!!,
                    title = title!!,
                    isLaunchable = isLaunchable!!,
                    recommendation = recommendation!!
            )
        }

        fun parseList(reader: JsonReader): List<InstalledApp> {
            val result = ArrayList<InstalledApp>()

            reader.beginArray()
            while (reader.hasNext()) {
                result.add(parse(reader))
            }
            reader.endArray()

            return Collections.unmodifiableList(result)
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(PACKAGE_NAME).value(packageName)
        writer.name(TITLE).value(title)
        writer.name(IS_LAUNCHABLE).value(isLaunchable)
        writer.name(RECOMMENDATION).value(AppRecommendationJson.serialize(recommendation))

        writer.endObject()
    }
}
data class AddInstalledAppsAction(val apps: List<InstalledApp>): AppLogicAction() {
    companion object {
        const val TYPE_VALUE = "ADD_INSTALLED_APPS"
        private const val APPS = "apps"

        fun parse(action: JSONObject) = AddInstalledAppsAction(
                apps = ParseUtils.readObjectArray(action.getJSONArray(APPS)).map { InstalledApp.parse(it) }
        )
    }

    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(apps.map { it.packageName })
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.name(APPS)
        writer.beginArray()
        apps.forEach { it.serialize(writer) }
        writer.endArray()

        writer.endObject()
    }
}
data class RemoveInstalledAppsAction(val packageNames: List<String>): AppLogicAction() {
    companion object {
        const val TYPE_VALUE = "REMOVE_INSTALLED_APPS"
        private const val PACKAGE_NAMES = "packageNames"

        fun parse(action: JSONObject) = RemoveInstalledAppsAction(
                packageNames = ParseUtils.readStringArray(action.getJSONArray(PACKAGE_NAMES))
        )
    }

    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.name(PACKAGE_NAMES)
        writer.beginArray()
        packageNames.forEach { writer.value(it) }
        writer.endArray()

        writer.endObject()
    }
}
object SignOutAtDeviceAction: AppLogicAction() {
    const val TYPE_VALUE = "SIGN_OUT_AT_DEVICE"

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.endObject()
    }
}

data class AddCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    companion object {
        const val TYPE_VALUE = "ADD_CATEGORY_APPS"
        private const val CATEGORY_ID = "categoryId"
        private const val PACKAGE_NAMES = "packageNames"

        fun parse(action: JSONObject) = AddCategoryAppsAction(
                categoryId = action.getString(CATEGORY_ID),
                packageNames = ParseUtils.readStringArray(action.getJSONArray(PACKAGE_NAMES))
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.name(PACKAGE_NAMES)
        writer.beginArray()
        packageNames.forEach { writer.value(it) }
        writer.endArray()

        writer.endObject()
    }
}
data class RemoveCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    companion object {
        const val TYPE_VALUE = "REMOVE_CATEGORY_APPS"
        private const val CATEGORY_ID = "categoryId"
        private const val PACKAGE_NAMES = "packageNames"

        fun parse(action: JSONObject) = RemoveCategoryAppsAction(
                categoryId = action.getString(CATEGORY_ID),
                packageNames = ParseUtils.readStringArray(action.getJSONArray(PACKAGE_NAMES))
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.name(PACKAGE_NAMES)
        writer.beginArray()
        packageNames.forEach { writer.value(it) }
        writer.endArray()

        writer.endObject()
    }
}

data class CreateCategoryAction(val childId: String, val categoryId: String, val title: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "CREATE_CATEGORY"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
        private const val TITLE = "title"

        fun parse(action: JSONObject) = CreateCategoryAction(
                childId = action.getString(CHILD_ID),
                categoryId = action.getString(CATEGORY_ID),
                title = action.getString(TITLE)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
        IdGenerator.assertIdValid(childId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CHILD_ID).value(childId)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(TITLE).value(title)

        writer.endObject()
    }
}
data class DeleteCategoryAction(val categoryId: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "DELETE_CATEGORY"
        private const val CATEGORY_ID = "categoryId"

        fun parse(action: JSONObject) = DeleteCategoryAction(
                categoryId = action.getString(CATEGORY_ID)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.endObject()
    }
}
data class UpdateCategoryTitleAction(val categoryId: String, val newTitle: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_CATEGORY_TITLE"
        private const val CATEGORY_ID = "categoryId"
        private const val NEW_TITLE = "newTitle"

        fun parse(action: JSONObject) = UpdateCategoryTitleAction(
                categoryId = action.getString(CATEGORY_ID),
                newTitle = action.getString(NEW_TITLE)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(NEW_TITLE).value(newTitle)

        writer.endObject()
    }
}
data class SetCategoryExtraTimeAction(val categoryId: String, val newExtraTime: Long): ParentAction() {
    companion object {
        const val TYPE_VALUE = "SET_CATEGORY_EXTRA_TIME"
        private const val CATEGORY_ID = "categoryId"
        private const val NEW_EXTRA_TIME = "newExtraTime"

        fun parse(action: JSONObject) = SetCategoryExtraTimeAction(
                categoryId = action.getString(CATEGORY_ID),
                newExtraTime = action.getLong(NEW_EXTRA_TIME)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (newExtraTime < 0) {
            throw IllegalArgumentException("newExtraTime must be >= 0")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(NEW_EXTRA_TIME).value(newExtraTime)

        writer.endObject()
    }
}
data class IncrementCategoryExtraTimeAction(val categoryId: String, val addedExtraTime: Long): ParentAction() {
    companion object {
        const val TYPE_VALUE = "INCREMENT_CATEGORY_EXTRATIME"
        private const val CATEGORY_ID = "categoryId"
        private const val ADDED_EXTRA_TIME = "addedExtraTime"

        fun parse(action: JSONObject) = IncrementCategoryExtraTimeAction(
                categoryId = action.getString(CATEGORY_ID),
                addedExtraTime = action.getLong(ADDED_EXTRA_TIME)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (addedExtraTime <= 0) {
            throw IllegalArgumentException("addedExtraTime must be more than zero")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(ADDED_EXTRA_TIME).value(addedExtraTime)

        writer.endObject()
    }
}
data class UpdateCategoryTemporarilyBlockedAction(val categoryId: String, val blocked: Boolean): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_CATEGORY_TEMPORARILY_BLOCKED"
        private const val CATEGORY_ID = "categoryId"
        private const val BLOCKED = "blocked"

        fun parse(action: JSONObject) = UpdateCategoryTemporarilyBlockedAction(
                categoryId = action.getString(CATEGORY_ID),
                blocked = action.getBoolean(BLOCKED)
        )
    }

    init {
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(BLOCKED).value(blocked)

        writer.endObject()
    }
}
data class SetCategoryForUnassignedApps(val childId: String, val categoryId: String): ParentAction() {
    // category id can be empty

    companion object {
        const val TYPE_VALUE = "SET_CATEGORY_FOR_UNASSIGNED_APPS"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
    }

    init {
        IdGenerator.assertIdValid(childId)

        if (categoryId.isNotEmpty()) {
            IdGenerator.assertIdValid(categoryId)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CHILD_ID).value(childId)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.endObject()
    }
}
data class SetParentCategory(val categoryId: String, val parentCategory: String): ParentAction() {
    // parent category id can be empty

    companion object {
        private const val TYPE_VALUE = "SET_PARENT_CATEGORY"
        private const val CATEGORY_ID = "categoryId"
        private const val PARENT_CATEGORY = "parentCategory"
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (parentCategory.isNotEmpty()) {
            IdGenerator.assertIdValid(parentCategory)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(PARENT_CATEGORY).value(parentCategory)

        writer.endObject()
    }
}

// DeviceDao

data class UpdateDeviceStatusAction(
        val newProtectionLevel: ProtectionLevel?,
        val newUsageStatsPermissionStatus: RuntimePermissionStatus?,
        val newNotificationAccessPermission: NewPermissionStatus?,
        val newOverlayPermission: RuntimePermissionStatus?,
        val newAppVersion: Int?,
        val didReboot: Boolean
): AppLogicAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_DEVICE_STATUS"
        private const val NEW_PROTECTION_LEVEL = "protectionLevel"
        private const val NEW_USAGE_STATS_PERMISSION_STATUS = "usageStats"
        private const val NEW_NOTIFICATION_ACCESS_PERMISSION = "notificationAccess"
        private const val NEW_OVERLAY_PERMISSION = "overlayPermission"
        private const val NEW_APP_VERSION = "appVersion"
        private const val DID_REBOOT = "didReboot"

        val empty = UpdateDeviceStatusAction(
                newProtectionLevel = null,
                newUsageStatsPermissionStatus = null,
                newNotificationAccessPermission = null,
                newOverlayPermission = null,
                newAppVersion = null,
                didReboot = false
        )
    }

    init {
        if (newAppVersion != null && newAppVersion < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        if (newProtectionLevel != null) {
            writer.name(NEW_PROTECTION_LEVEL)
            writer.value(ProtectionLevelUtil.serialize(newProtectionLevel))
        }

        if (newUsageStatsPermissionStatus != null) {
            writer
                    .name(NEW_USAGE_STATS_PERMISSION_STATUS)
                    .value(RuntimePermissionStatusUtil.serialize(newUsageStatsPermissionStatus))
        }

        if (newNotificationAccessPermission != null) {
            writer
                    .name(NEW_NOTIFICATION_ACCESS_PERMISSION)
                    .value(NewPermissionStatusUtil.serialize(newNotificationAccessPermission))
        }

        if (newOverlayPermission != null) {
            writer
                    .name(NEW_OVERLAY_PERMISSION)
                    .value(RuntimePermissionStatusUtil.serialize(newOverlayPermission))
        }

        if (newAppVersion != null) {
            writer.name(NEW_APP_VERSION)
            writer.value(newAppVersion)
        }

        if (didReboot) {
            writer.name(DID_REBOOT).value(true)
        }

        writer.endObject()
    }
}

data class IgnoreManipulationAction(
        val deviceId: String,
        val ignoreDeviceAdminManipulation: Boolean,
        val ignoreDeviceAdminManipulationAttempt: Boolean,
        val ignoreAppDowngrade: Boolean,
        val ignoreNotificationAccessManipulation: Boolean,
        val ignoreUsageStatsAccessManipulation: Boolean,
        val ignoreOverlayPermissionManipulation: Boolean,
        val ignoreReboot: Boolean,
        val ignoreHadManipulation: Boolean
): ParentAction() {
    companion object {
        const val TYPE_VALUE = "IGNORE_MANIPULATION"
        private const val DEVICE_ID = "deviceId"
        private const val IGNORE_ADMIN = "admin"
        private const val IGNORE_ADMIN_ATTEMPT = "adminA"
        private const val IGNORE_APP_DOWNGRADE = "downgrade"
        private const val IGNORE_NOTIFICATION_ACCESS = "notification"
        private const val IGNORE_USAGE_STATS_ACCESS = "usageStats"
        private const val IGNORE_OVERLAY_PERMISSION_MANIPULATION = "overlay"
        private const val IGNORE_HAD_MANIPULATION = "hadManipulation"
        private const val IGNORE_REBOOT = "reboot"
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    val isEmpty = (!ignoreDeviceAdminManipulation) &&
            (!ignoreDeviceAdminManipulationAttempt) &&
            (!ignoreAppDowngrade) &&
            (!ignoreNotificationAccessManipulation) &&
            (!ignoreUsageStatsAccessManipulation) &&
            (!ignoreOverlayPermissionManipulation) &&
            (!ignoreReboot) &&
            (!ignoreHadManipulation)

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(IGNORE_ADMIN).value(ignoreDeviceAdminManipulation)
        writer.name(IGNORE_ADMIN_ATTEMPT).value(ignoreDeviceAdminManipulationAttempt)
        writer.name(IGNORE_APP_DOWNGRADE).value(ignoreAppDowngrade)
        writer.name(IGNORE_NOTIFICATION_ACCESS).value(ignoreNotificationAccessManipulation)
        writer.name(IGNORE_USAGE_STATS_ACCESS).value(ignoreUsageStatsAccessManipulation)
        writer.name(IGNORE_OVERLAY_PERMISSION_MANIPULATION).value(ignoreOverlayPermissionManipulation)
        writer.name(IGNORE_HAD_MANIPULATION).value(ignoreHadManipulation)
        writer.name(IGNORE_REBOOT).value(ignoreReboot)

        writer.endObject()
    }
}

object TriedDisablingDeviceAdminAction: AppLogicAction() {
    const val TYPE_VALUE = "TRIED_DISABLING_DEVICE_ADMIN"

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.endObject()
    }
}

data class UpdateNetworkTimeVerificationAction(val deviceId: String, val mode: NetworkTime): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_NETWORK_TIME_VERIFICATION"
        private const val DEVICE_ID = "deviceId"
        private const val MODE = "mode"
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(MODE).value(NetworkTimeJson.serialize(mode))

        writer.endObject()
    }
}

data class SetDeviceUserAction(val deviceId: String, val userId: String): ParentAction() {
    // user id can be an empty string

    companion object {
        const val TYPE_VALUE = "SET_DEVICE_USER"
        private const val DEVICE_ID = "deviceId"
        private const val USER_ID = "userId"
    }

    init {
        IdGenerator.assertIdValid(deviceId)

        if (userId != "") {
            IdGenerator.assertIdValid(userId)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(USER_ID).value(userId)

        writer.endObject()
    }
}

data class SetKeepSignedInAction(val deviceId: String, val keepSignedIn: Boolean): ParentAction() {
    companion object {
        const val TYPE_VALUE = "SET_KEEP_SIGNED_IN"
        private const val DEVICE_ID = "deviceId"
        private const val KEEP_SIGNED_IN = "keepSignedIn"
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(KEEP_SIGNED_IN).value(keepSignedIn)

        writer.endObject()
    }
}

data class SetSendDeviceConnected(val deviceId: String, val enable: Boolean): ParentAction() {
    companion object {
        const val TYPE_VALUE = "SET_SEND_DEVICE_CONNECTED"
        private const val DEVICE_ID = "deviceId"
        private const val ENABLE = "enable"
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(ENABLE).value(enable)

        writer.endObject()
    }
}

data class SetDeviceDefaultUserAction(val deviceId: String, val defaultUserId: String): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "SET_DEVICE_DEFAULT_USER"
        private const val DEVICE_ID = "deviceId"
        private const val DEFAULT_USER_ID = "defaultUserId"
    }

    init {
        IdGenerator.assertIdValid(deviceId)

        if (defaultUserId.isNotEmpty()) {
            IdGenerator.assertIdValid(defaultUserId)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(DEFAULT_USER_ID).value(defaultUserId)

        writer.endObject()
    }
}

data class SetDeviceDefaultUserTimeoutAction(val deviceId: String, val timeout: Int): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "SET_DEVICE_DEFAULT_USER_TIMEOUT"
        private const val DEVICE_ID = "deviceId"
        private const val TIMEOUT = "timeout"
    }

    init {
        IdGenerator.assertIdValid(deviceId)

        if (timeout < 0) {
            throw IllegalArgumentException("can not set a negative default user timeout")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(TIMEOUT).value(timeout)

        writer.endObject()
    }
}

data class SetConsiderRebootManipulationAction(val deviceId: String, val considerRebootManipulation: Boolean): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "SET_CONSIDER_REBOOT_MANIPULATION"
        private const val DEVICE_ID = "deviceId"
        private const val ENABLE = "enable"
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(ENABLE).value(considerRebootManipulation)

        writer.endObject()
    }
}

data class UpdateCategoryBlockedTimesAction(val categoryId: String, val blockedTimes: ImmutableBitmask): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_CATEGORY_BLOCKED_TIMES"
        private const val CATEGORY_ID = "categoryId"
        private const val BLOCKED_TIMES = "times"
    }

    init {
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(BLOCKED_TIMES).value(ImmutableBitmaskJson.serialize(blockedTimes))

        writer.endObject()
    }
}

data class UpdateCategoryBlockAllNotificationsAction(val categoryId: String, val blocked: Boolean): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "UPDATE_CATEGORY_BLOCK_ALL_NOTIFICATIONS"
        private const val CATEGORY_ID = "categoryId"
        private const val BLOCK = "blocked"
    }

    init {
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(BLOCK).value(blocked)

        writer.endObject()
    }
}

data class CreateTimeLimitRuleAction(val rule: TimeLimitRule): ParentAction() {
    companion object {
        const val TYPE_VALUE = "CREATE_TIMELIMIT_RULE"
        private const val RULE = "rule"
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.name(RULE)
        rule.serialize(writer)

        writer.endObject()
    }
}

data class UpdateTimeLimitRuleAction(val ruleId: String, val dayMask: Byte, val maximumTimeInMillis: Int, val applyToExtraTimeUsage: Boolean): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_TIMELIMIT_RULE"
        private const val RULE_ID = "ruleId"
        private const val MAX_TIME_IN_MILLIS = "time"
        private const val DAY_MASK = "days"
        private const val APPLY_TO_EXTRA_TIME_USAGE = "extraTime"
    }

    init {
        IdGenerator.assertIdValid(ruleId)

        if (maximumTimeInMillis < 0) {
            throw IllegalArgumentException()
        }

        if (dayMask < 0 || dayMask > (1 or 2 or 4 or 8 or 16 or 32 or 64)) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(RULE_ID).value(ruleId)
        writer.name(MAX_TIME_IN_MILLIS).value(maximumTimeInMillis)
        writer.name(DAY_MASK).value(dayMask)
        writer.name(APPLY_TO_EXTRA_TIME_USAGE).value(applyToExtraTimeUsage)

        writer.endObject()
    }
}

data class DeleteTimeLimitRuleAction(val ruleId: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "DELETE_TIMELIMIT_RULE"
        private const val RULE_ID = "ruleId"

        fun parse(action: JSONObject) = DeleteTimeLimitRuleAction(
                ruleId = action.getString(RULE_ID)
        )
    }

    init {
        IdGenerator.assertIdValid(ruleId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(RULE_ID).value(this.ruleId)

        writer.endObject()
    }
}

// UserDao
data class AddUserAction(val name: String, val userType: UserType, val password: ParentPassword?, val userId: String, val timeZone: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "ADD_USER"
        private const val USER_ID = "userId"
        private const val USER_TYPE = "userType"
        private const val NAME = "name"
        private const val PASSWORD = "password"
        private const val TIMEZONE = "timeZone"

        fun parse(action: JSONObject): AddUserAction {
            var password: ParentPassword? = null
            val passwordObject = action.getJSONObject(PASSWORD)

            if (passwordObject != null) {
                password = ParentPassword.parse(passwordObject)
            }

            return AddUserAction(
                    name = action.getString(NAME),
                    userType = UserTypeJson.parse(action.getString(USER_TYPE)),
                    password = password,
                    userId = action.getString(USER_ID),
                    timeZone = action.getString(TIMEZONE)
            )
        }
    }

    init {
        if (userType == UserType.Parent) {
            password!!
        }

        IdGenerator.assertIdValid(userId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(NAME).value(name)
        writer.name(USER_TYPE).value(UserTypeJson.serialize(userType))
        writer.name(USER_ID).value(userId)
        writer.name(TIMEZONE).value(timeZone)

        if (password != null) {
            writer.name(PASSWORD)
            password.serialize(writer)
        }

        writer.endObject()
    }
}

data class ChangeParentPasswordAction(
        val parentUserId: String,
        val newPasswordFirstHash: String,
        val newPasswordSecondSalt: String,
        val newPasswordSecondHashEncrypted: String,
        val integrity: String   // SHA512(old password second hash  + parent user id + new password first hash + new password second salt + new password second hash encrypted) as hex string
): ParentAction() {
    companion object {
        const val TYPE_VALUE = "CHANGE_PARENT_PASSWORD"
        private const val PARENT_USER_ID = "userId"
        private const val NEW_PASSWORD_FIRST_HASH = "hash"
        private const val NEW_PASSWORD_SECOND_SALT = "secondSalt"
        private const val NEW_PASSWORD_SECOND_HASH_ENCRYPTED = "secondHashEncrypted"
        private const val INTEGRITY = "integrity"
    }

    init {
        IdGenerator.assertIdValid(parentUserId)

        if (newPasswordFirstHash.isEmpty() || newPasswordSecondSalt.isEmpty() || newPasswordSecondHashEncrypted.isEmpty() || integrity.isEmpty()) {
            throw IllegalArgumentException("missing required parameter")
        }

        if (integrity.length != 128) {
            throw IllegalArgumentException("wrong length of integrity data")
        }

        HexString.assertIsHexString(integrity)
        HexString.assertIsHexString(newPasswordSecondHashEncrypted)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(PARENT_USER_ID).value(parentUserId)
        writer.name(NEW_PASSWORD_FIRST_HASH).value(newPasswordFirstHash)
        writer.name(NEW_PASSWORD_SECOND_SALT).value(newPasswordSecondSalt)
        writer.name(NEW_PASSWORD_SECOND_HASH_ENCRYPTED).value(newPasswordSecondHashEncrypted)
        writer.name(INTEGRITY).value(integrity)

        writer.endObject()
    }
}

data class UpdateParentNotificationFlagsAction(val parentId: String, val flags: Int, val set: Boolean): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "UPDATE_PARENT_NOTIFICATION_FLAGS"
        private const val PARENT_ID = "parentId"
        private const val FLAGS = "flags"
        private const val SET = "set"
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(PARENT_ID).value(parentId)
        writer.name(FLAGS).value(flags)
        writer.name(SET).value(set)

        writer.endObject()
    }
}

data class RemoveUserAction(val userId: String, val authentication: String?): ParentAction() {
    companion object {
        const val TYPE_VALUE = "REMOVE_USER"
        private const val USER_ID = "userId"
        private const val AUTHENTICATION = "authentication"
    }

    init {
        IdGenerator.assertIdValid(userId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(USER_ID).value(userId)

        if (authentication != null) {
            writer.name(AUTHENTICATION).value(authentication)
        }

        writer.endObject()
    }
}

data class SetUserDisableLimitsUntilAction(val childId: String, val timestamp: Long): ParentAction() {
    companion object {
        const val TYPE_VALUE = "SET_USER_DISABLE_LIMITS_UNTIL"
        private const val CHILD_ID = "childId"
        private const val TIMESTAMP = "time"
    }

    init {
        IdGenerator.assertIdValid(childId)

        if (timestamp < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CHILD_ID).value(childId)
        writer.name(TIMESTAMP).value(timestamp)

        writer.endObject()
    }
}

data class UpdateDeviceNameAction(val deviceId: String, val name: String): ParentAction() {
    companion object {
        const val TYPE_VALUE = "UPDATE_DEVICE_NAME"
        private const val DEVICE_ID = "deviceId"
        private const val NAME = "name"
    }

    init {
        IdGenerator.assertIdValid(deviceId)

        if (name.isBlank()) {
            throw IllegalArgumentException("new device name must not be blank")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(DEVICE_ID).value(deviceId)
        writer.name(NAME).value(name)

        writer.endObject()
    }
}

data class SetRelaxPrimaryDeviceAction(val userId: String, val relax: Boolean): ParentAction() {
    companion object {
        const val TYPE_VALUE = "SET_RELAX_PRIMARY_DEVICE"
        private const val USER_ID = "userId"
        private const val RELAX = "relax"
    }

    init {
        IdGenerator.assertIdValid(userId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(USER_ID).value(userId)
        writer.name(RELAX).value(relax)

        writer.endObject()
    }
}

data class SetUserTimezoneAction(val userId: String, val timezone: String): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "SET_USER_TIMEZONE"
        private const val USER_ID = "userId"
        private const val TIMEZONE = "timezone"
    }

    init {
        IdGenerator.assertIdValid(userId)

        if (timezone.isBlank()) {
            throw IllegalArgumentException("tried to set timezone to empty")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(USER_ID).value(userId)
        writer.name(TIMEZONE).value(timezone)

        writer.endObject()
    }
}

data class SetChildPasswordAction(val childId: String, val newPassword: ParentPassword): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "SET_CHILD_PASSWORD"
        private const val CHILD_ID = "childId"
        private const val NEW_PASSWORD = "newPassword"
    }

    init {
        IdGenerator.assertIdValid(childId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CHILD_ID).value(childId)

        writer.name(NEW_PASSWORD)
        newPassword.serialize(writer)

        writer.endObject()
    }
}

data class RenameChildAction(val childId: String, val newName: String): ParentAction() {
    companion object {
        private const val TYPE_VALUE = "RENAME_CHILD"
        private const val CHILD_ID = "childId"
        private const val NEW_NAME = "newName"
    }

    init {
        IdGenerator.assertIdValid(childId)

        if (newName.isEmpty()) {
            throw IllegalArgumentException("newName must not be empty")
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)
        writer.name(CHILD_ID).value(childId)
        writer.name(NEW_NAME).value(newName)

        writer.endObject()
    }
}

// child actions
object ChildSignInAction: ChildAction() {
    private const val TYPE_VALUE = "CHILD_SIGN_IN"

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.endObject()
    }
}

data class ChildChangePasswordAction(val password: ParentPassword): ChildAction() {
    companion object {
        private const val TYPE_VALUE = "CHILD_CHANGE_PASSWORD"
        private const val PASSWORD = "password"
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(TYPE_VALUE)

        writer.name(PASSWORD)
        password.serialize(writer)

        writer.endObject()
    }
}