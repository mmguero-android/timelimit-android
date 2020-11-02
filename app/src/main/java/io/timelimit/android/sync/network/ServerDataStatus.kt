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
package io.timelimit.android.sync.network

import android.util.JsonReader
import android.util.JsonToken
import io.timelimit.android.crypto.HexString
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson
import io.timelimit.android.data.model.*
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.extensions.parseList
import io.timelimit.android.integration.platform.*
import io.timelimit.android.sync.actions.AppActivityItem
import io.timelimit.android.sync.actions.InstalledApp
import io.timelimit.android.util.parseJsonArray
import io.timelimit.android.util.parseJsonStringArray
import java.util.*
import kotlin.collections.ArrayList

data class ServerDataStatus(
        val newDeviceList: ServerDeviceList?,
        val newInstalledApps: List<ServerInstalledAppsData>,
        val removedCategories: List<String>,
        val newCategoryBaseData: List<ServerUpdatedCategoryBaseData>,
        val newCategoryAssignedApps: List<ServerUpdatedCategoryAssignedApps>,
        val newCategoryUsedTimes: List<ServerUpdatedCategoryUsedTimes>,
        val newCategoryTimeLimitRules: List<ServerUpdatedTimeLimitRules>,
        val newUserList: ServerUserList?,
        val fullVersionUntil: Long,
        val message: String?
) {
    companion object {
        private const val NEW_DEVICE_LIST = "devices"
        private const val NEW_INSTALLED_APPS = "apps"
        private const val REMOVED_CATEGORIES = "rmCategories"
        private const val NEW_CATEGORIES_BASE_DATA = "categoryBase"
        private const val NEW_CATEGORY_ASSIGNED_APPS = "categoryApp"
        private const val NEW_CATEGORY_USED_TIMES = "usedTimes"
        private const val NEW_CATEGORY_TIME_LIMIT_RULES = "rules"
        private const val NEW_USER_LIST = "users"
        private const val FULL_VERSION_UNTIL = "fullVersion"
        private const val MESSAGE = "message"

        fun parse(reader: JsonReader): ServerDataStatus {
            var newDeviceList: ServerDeviceList? = null
            var newInstalledApps: List<ServerInstalledAppsData> = Collections.emptyList()
            var removedCategories: List<String> = Collections.emptyList()
            var newCategoryBaseData: List<ServerUpdatedCategoryBaseData> = Collections.emptyList()
            var newCategoryAssignedApps: List<ServerUpdatedCategoryAssignedApps> = Collections.emptyList()
            var newCategoryUsedTimes: List<ServerUpdatedCategoryUsedTimes> = Collections.emptyList()
            var newCategoryTimeLimitRules: List<ServerUpdatedTimeLimitRules> = Collections.emptyList()
            var newUserList: ServerUserList? = null
            var fullVersionUntil: Long? = null
            var message: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when(reader.nextName()) {
                    NEW_DEVICE_LIST -> newDeviceList = ServerDeviceList.parse(reader)
                    NEW_INSTALLED_APPS -> newInstalledApps = ServerInstalledAppsData.parseList(reader)
                    REMOVED_CATEGORIES -> removedCategories = parseJsonStringArray(reader)
                    NEW_CATEGORIES_BASE_DATA -> newCategoryBaseData = ServerUpdatedCategoryBaseData.parseList(reader)
                    NEW_CATEGORY_ASSIGNED_APPS -> newCategoryAssignedApps = ServerUpdatedCategoryAssignedApps.parseList(reader)
                    NEW_CATEGORY_USED_TIMES -> newCategoryUsedTimes = ServerUpdatedCategoryUsedTimes.parseList(reader)
                    NEW_CATEGORY_TIME_LIMIT_RULES -> newCategoryTimeLimitRules = ServerUpdatedTimeLimitRules.parseList(reader)
                    NEW_USER_LIST -> newUserList = ServerUserList.parse(reader)
                    FULL_VERSION_UNTIL -> fullVersionUntil = reader.nextLong()
                    MESSAGE -> message = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerDataStatus(
                    newDeviceList = newDeviceList,
                    newInstalledApps = newInstalledApps,
                    removedCategories = removedCategories,
                    newCategoryBaseData = newCategoryBaseData,
                    newCategoryAssignedApps = newCategoryAssignedApps,
                    newCategoryUsedTimes = newCategoryUsedTimes,
                    newCategoryTimeLimitRules = newCategoryTimeLimitRules,
                    newUserList = newUserList,
                    fullVersionUntil = fullVersionUntil!!,
                    message = message
            )
        }
    }
}

data class ServerDeviceList(
        val version: String,
        val data: List<ServerDeviceData>
) {
    companion object {
        private const val VERSION = "version"
        private const val DATA = "data"

        fun parse(reader: JsonReader): ServerDeviceList {
            var version: String? = null
            var data: List<ServerDeviceData>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    VERSION -> version = reader.nextString()
                    DATA -> data = ServerDeviceData.parseList(reader)
                }
            }
            reader.endObject()

            return ServerDeviceList(
                    version = version!!,
                    data = data!!
            )
        }
    }
}

data class ServerUserList(
        val version: String,
        val data: List<ServerUserData>
) {
    companion object {
        private const val VERSION = "version"
        private const val DATA = "data"

        fun parse(reader: JsonReader): ServerUserList {
            var version: String? = null
            var data: List<ServerUserData>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    VERSION -> version = reader.nextString()
                    DATA -> data = ServerUserData.parseList(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUserList(
                    version = version!!,
                    data = data!!
            )
        }
    }
}

data class ServerUserData(
        val id: String,
        val name: String,
        val password: String,
        val secondPasswordSalt: String,
        val type: UserType,
        val timeZone: String,
        val disableLimitsUntil: Long,
        val mail: String,
        val currentDevice: String,
        val categoryForNotAssignedApps: String,
        val relaxPrimaryDevice: Boolean,
        val mailNotificationFlags: Int,
        val flags: Long,
        val limitLoginCategory: String?
) {
    companion object {
        private const val ID = "id"
        private const val NAME = "name"
        private const val PASSWORD = "password"
        private const val SECOND_PASSWORD_SALT = "secondPasswordSalt"
        private const val TYPE = "type"
        private const val TIMEZONE = "timeZone"
        private const val DISABLE_LIMITS_UNTIL = "disableLimitsUntil"
        private const val MAIL = "mail"
        private const val CURRENT_DEVICE = "currentDevice"
        private const val CATEGORY_FOR_NOT_ASSIGNED_APPS = "categoryForNotAssignedApps"
        private const val RELAX_PRIMARY_DEVICE = "relaxPrimaryDevice"
        private const val MAIL_NOTIFICATION_FLAGS = "mailNotificationFlags"
        private const val FLAGS = "flags"
        private const val USER_LIMIT_LOGIN_CATEGORY = "llc"

        fun parse(reader: JsonReader): ServerUserData {
            var id: String? = null
            var name: String? = null
            var password: String? = null
            var secondPasswordSalt: String? = null
            var type: UserType? = null
            var timeZone: String? = null
            var disableLimitsUntil: Long? = null
            var mail: String? = null
            var currentDevice: String? = null
            var categoryForNotAssignedApps = ""
            var relaxPrimaryDevice = false
            var mailNotificationFlags = 0
            var blockedTimes = ImmutableBitmask(BitSet())
            var flags = 0L
            var limitLoginCategory: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when(reader.nextName()) {
                    ID -> id = reader.nextString()
                    NAME -> name = reader.nextString()
                    PASSWORD -> password = reader.nextString()
                    SECOND_PASSWORD_SALT -> secondPasswordSalt = reader.nextString()
                    TYPE -> type = UserTypeJson.parse(reader.nextString())
                    TIMEZONE -> timeZone = reader.nextString()
                    DISABLE_LIMITS_UNTIL -> disableLimitsUntil = reader.nextLong()
                    MAIL -> mail = reader.nextString()
                    CURRENT_DEVICE -> currentDevice = reader.nextString()
                    CATEGORY_FOR_NOT_ASSIGNED_APPS -> categoryForNotAssignedApps = reader.nextString()
                    RELAX_PRIMARY_DEVICE -> relaxPrimaryDevice = reader.nextBoolean()
                    MAIL_NOTIFICATION_FLAGS -> mailNotificationFlags = reader.nextInt()
                    FLAGS -> flags = reader.nextLong()
                    USER_LIMIT_LOGIN_CATEGORY -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else limitLoginCategory = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUserData(
                    id = id!!,
                    name = name!!,
                    password = password!!,
                    secondPasswordSalt = secondPasswordSalt!!,
                    type = type!!,
                    timeZone = timeZone!!,
                    disableLimitsUntil = disableLimitsUntil!!,
                    mail = mail!!,
                    currentDevice = currentDevice!!,
                    categoryForNotAssignedApps = categoryForNotAssignedApps,
                    relaxPrimaryDevice = relaxPrimaryDevice,
                    mailNotificationFlags = mailNotificationFlags,
                    flags = flags,
                    limitLoginCategory = limitLoginCategory
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}

data class ServerDeviceData(
        val deviceId: String,
        val name: String,
        val model: String,
        val addedAt: Long,
        val currentUserId: String,
        val networkTime: NetworkTime,
        val currentProtectionLevel: ProtectionLevel,
        val highestProtectionLevel: ProtectionLevel,
        val currentUsageStatsPermission: RuntimePermissionStatus,
        val highestUsageStatsPermission: RuntimePermissionStatus,
        val currentNotificationAccessPermission: NewPermissionStatus,
        val highestNotificationAccessPermission: NewPermissionStatus,
        val currentAppVersion: Int,
        val highestAppVersion: Int,
        val triedDisablingAdmin: Boolean,
        val didReboot: Boolean,
        val hadManipulation: Boolean,
        val hadManipulationFlags: Long,
        val didReportUninstall: Boolean,
        val isUserKeptSignedIn: Boolean,
        val showDeviceConnected: Boolean,
        val defaultUser: String,
        val defaultUserTimeout: Int,
        val considerRebootManipulation: Boolean,
        val currentOverlayPermission: RuntimePermissionStatus,
        val highestOverlayPermission: RuntimePermissionStatus,
        val accessibilityServiceEnabled: Boolean,
        val wasAccessibilityServiceEnabled: Boolean,
        val enableActivityLevelBlocking: Boolean,
        val qOrLater: Boolean
) {
    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val NAME = "name"
        private const val MODEL = "model"
        private const val ADDED_AT = "addedAt"
        private const val CURRENT_USER_ID = "currentUserId"
        private const val NETWORK_TIME = "networkTime"
        private const val CURRENT_PROTECTION_LEVEL = "cProtectionLevel"
        private const val HIGHEST_PROTECTION_LEVEL = "hProtectionLevel"
        private const val CURRENT_USAGE_STATS_PERMISSION = "cUsageStats"
        private const val HIGHEST_USAGE_STATS_PERMISSION = "hUsageStats"
        private const val CURRENT_NOTIFICATION_ACCESS = "cNotificationAccess"
        private const val HIGHEST_NOTIFICATION_ACCESS = "hNotificationAccess"
        private const val CURRENT_APP_VERSION = "cAppVersion"
        private const val HIGHEST_APP_VERSION = "hAppVersion"
        private const val TRIED_DISABLING_ADMIN = "tDisablingAdmin"
        private const val DID_REBOOT = "reboot"
        private const val HAD_MANIPULATION = "hadManipulation"
        private const val HAD_MANIPULATION_FLAGS = "hadManipulationFlags"
        private const val DID_REPORT_UNINSTALL = "reportUninstall"
        private const val IS_USER_KEPT_SIGNED_IN = "isUserKeptSignedIn"
        private const val SHOW_DEVICE_CONNECTED = "showDeviceConnected"
        private const val DEFAULT_USER = "defUser"
        private const val DEFAULT_USER_TIMEOUT = "defUserTimeout"
        private const val CONSIDER_REBOOT_MANIPULATION = "rebootIsManipulation"
        private const val CURRENT_OVERLAY_PERMISSION = "cOverlay"
        private const val HIGHEST_OVERLAY_PERMISSION = "hOverlay"
        private const val ACCESSIBILITY_SERVICE_ENABLED = "asEnabled"
        private const val WAS_ACCESSIBILITY_SERVICE_ENABLED = "wasAsEnabled"
        private const val ENABLE_ACTIVITY_LEVEL_BLOCKING = "activityLevelBlocking"
        private const val Q_OR_LATER = "qOrLater"

        fun parse(reader: JsonReader): ServerDeviceData {
            var deviceId: String? = null
            var name: String? = null
            var model: String? = null
            var addedAt: Long? = null
            var currentUserId: String? = null
            var networkTime: NetworkTime? = null
            var currentProtectionLevel: ProtectionLevel? = null
            var highestProtectionLevel: ProtectionLevel? = null
            var currentUsageStatsPermission: RuntimePermissionStatus? = null
            var highestUsageStatsPermission: RuntimePermissionStatus? = null
            var currentNotificationAccessPermission: NewPermissionStatus? = null
            var highestNotificationAccessPermission: NewPermissionStatus? = null
            var currentAppVersion: Int? = null
            var highestAppVersion: Int? = null
            var triedDisablingAdmin: Boolean? = null
            var didReboot: Boolean? = null
            var hadManipulationFlags: Long = 0
            var hadManipulation: Boolean? = null
            var didReportUninstall: Boolean? = null
            var isUserKeptSignedIn: Boolean? = null
            var showDeviceConnected: Boolean? = null
            var defaultUser: String? = null
            var defaultUserTimeout: Int? = null
            var considerRebootManipulation: Boolean? = null
            var currentOverlayPermission: RuntimePermissionStatus? = null
            var highestOverlayPermission: RuntimePermissionStatus? = null
            var accessibilityServiceEnabled: Boolean? = null
            var wasAccessibilityServiceEnabled: Boolean? = null
            var enableActivityLevelBlocking = false
            var qOrLater = false

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_ID -> deviceId = reader.nextString()
                    NAME -> name = reader.nextString()
                    MODEL -> model = reader.nextString()
                    ADDED_AT -> addedAt = reader.nextLong()
                    CURRENT_USER_ID -> currentUserId = reader.nextString()
                    NETWORK_TIME -> networkTime = NetworkTimeJson.parse(reader.nextString())
                    CURRENT_PROTECTION_LEVEL -> currentProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    HIGHEST_PROTECTION_LEVEL -> highestProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    CURRENT_USAGE_STATS_PERMISSION -> currentUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_USAGE_STATS_PERMISSION -> highestUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    CURRENT_NOTIFICATION_ACCESS -> currentNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_NOTIFICATION_ACCESS -> highestNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    CURRENT_APP_VERSION -> currentAppVersion = reader.nextInt()
                    HIGHEST_APP_VERSION -> highestAppVersion = reader.nextInt()
                    TRIED_DISABLING_ADMIN -> triedDisablingAdmin = reader.nextBoolean()
                    DID_REBOOT -> didReboot = reader.nextBoolean()
                    HAD_MANIPULATION -> hadManipulation = reader.nextBoolean()
                    HAD_MANIPULATION_FLAGS -> hadManipulationFlags = reader.nextLong()
                    DID_REPORT_UNINSTALL -> didReportUninstall = reader.nextBoolean()
                    IS_USER_KEPT_SIGNED_IN -> isUserKeptSignedIn = reader.nextBoolean()
                    SHOW_DEVICE_CONNECTED -> showDeviceConnected = reader.nextBoolean()
                    DEFAULT_USER -> defaultUser = reader.nextString()
                    DEFAULT_USER_TIMEOUT -> defaultUserTimeout = reader.nextInt()
                    CONSIDER_REBOOT_MANIPULATION -> considerRebootManipulation = reader.nextBoolean()
                    CURRENT_OVERLAY_PERMISSION -> currentOverlayPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_OVERLAY_PERMISSION -> highestOverlayPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    ACCESSIBILITY_SERVICE_ENABLED -> accessibilityServiceEnabled = reader.nextBoolean()
                    WAS_ACCESSIBILITY_SERVICE_ENABLED -> wasAccessibilityServiceEnabled = reader.nextBoolean()
                    ENABLE_ACTIVITY_LEVEL_BLOCKING -> enableActivityLevelBlocking = reader.nextBoolean()
                    Q_OR_LATER -> qOrLater = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerDeviceData(
                    deviceId = deviceId!!,
                    name = name!!,
                    model = model!!,
                    addedAt = addedAt!!,
                    currentUserId = currentUserId!!,
                    networkTime = networkTime!!,
                    currentProtectionLevel = currentProtectionLevel!!,
                    highestProtectionLevel = highestProtectionLevel!!,
                    currentUsageStatsPermission = currentUsageStatsPermission!!,
                    highestUsageStatsPermission = highestUsageStatsPermission!!,
                    currentNotificationAccessPermission = currentNotificationAccessPermission!!,
                    highestNotificationAccessPermission = highestNotificationAccessPermission!!,
                    currentAppVersion = currentAppVersion!!,
                    highestAppVersion = highestAppVersion!!,
                    triedDisablingAdmin = triedDisablingAdmin!!,
                    didReboot = didReboot!!,
                    hadManipulation = hadManipulation!!,
                    hadManipulationFlags = hadManipulationFlags,
                    didReportUninstall = didReportUninstall!!,
                    isUserKeptSignedIn = isUserKeptSignedIn!!,
                    showDeviceConnected = showDeviceConnected!!,
                    defaultUser = defaultUser!!,
                    defaultUserTimeout = defaultUserTimeout!!,
                    considerRebootManipulation = considerRebootManipulation!!,
                    currentOverlayPermission = currentOverlayPermission!!,
                    highestOverlayPermission = highestOverlayPermission!!,
                    accessibilityServiceEnabled = accessibilityServiceEnabled!!,
                    wasAccessibilityServiceEnabled = wasAccessibilityServiceEnabled!!,
                    enableActivityLevelBlocking = enableActivityLevelBlocking,
                    qOrLater = qOrLater
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}

data class ServerUpdatedCategoryBaseData(
        val categoryId: String,
        val childId: String,
        val title: String,
        val blockedMinutesInWeek: ImmutableBitmask,
        val extraTimeInMillis: Long,
        val extraTimeDay: Int,
        val temporarilyBlocked: Boolean,
        val temporarilyBlockedEndTime: Long,
        val baseDataVersion: String,
        val parentCategoryId: String,
        val blockAllNotifications: Boolean,
        val timeWarnings: Int,
        val minBatteryLevelCharging: Int,
        val minBatteryLevelMobile: Int,
        val sort: Int,
        val networks: List<ServerCategoryNetworkId>,
        val disableLimitsUntil: Long
) {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val CHILD_ID = "childId"
        private const val TITLE = "title"
        private const val BLOCKED_MINUTES_IN_WEEK = "blockedTimes"
        private const val EXTRA_TIME_IN_MILLIS = "extraTime"
        private const val EXTRA_TIME_DAY = "extraTimeDay"
        private const val TEMPORARILY_BLOCKED = "tempBlocked"
        private const val TEMPORARILY_BLOCKED_END_TIME = "tempBlockTime"
        private const val BASE_DATA_VERSION = "version"
        private const val PARENT_CATEGORY_ID = "parentCategoryId"
        private const val BLOCK_ALL_NOTIFICATIONS = "blockAllNotifications"
        private const val TIME_WARNINGS = "timeWarnings"
        private const val MIN_BATTERY_LEVEL_MOBILE = "mblMobile"
        private const val MIN_BATTERY_LEVEL_CHARGING = "mblCharging"
        private const val SORT = "sort"
        private const val NETWORKS = "networks"
        private const val DISABLE_LIMITS_UNTIL = "dlu"

        fun parse(reader: JsonReader): ServerUpdatedCategoryBaseData {
            var categoryId: String? = null
            var childId: String? = null
            var title: String? = null
            var blockedMinutesInWeek: ImmutableBitmask? = null
            var extraTimeInMillis: Long? = null
            var extraTimeDay = -1
            var temporarilyBlocked: Boolean? = null
            var temporarilyBlockedEndTime: Long = 0
            var baseDataVersion: String? = null
            var parentCategoryId: String? = null
            // added later -> default values
            var blockAllNotifications = false
            var timeWarnings = 0
            var minBatteryLevelCharging = 0
            var minBatteryLevelMobile = 0
            var sort = 0
            var networks: List<ServerCategoryNetworkId> = emptyList()
            var disableLimitsUntil = 0L

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    CHILD_ID -> childId = reader.nextString()
                    TITLE -> title = reader.nextString()
                    BLOCKED_MINUTES_IN_WEEK -> blockedMinutesInWeek = ImmutableBitmaskJson.parse(reader.nextString(), Category.BLOCKED_MINUTES_IN_WEEK_LENGTH)
                    EXTRA_TIME_IN_MILLIS -> extraTimeInMillis = reader.nextLong()
                    EXTRA_TIME_DAY -> extraTimeDay = reader.nextInt()
                    TEMPORARILY_BLOCKED -> temporarilyBlocked = reader.nextBoolean()
                    TEMPORARILY_BLOCKED_END_TIME -> temporarilyBlockedEndTime = reader.nextLong()
                    BASE_DATA_VERSION -> baseDataVersion = reader.nextString()
                    PARENT_CATEGORY_ID -> parentCategoryId = reader.nextString()
                    BLOCK_ALL_NOTIFICATIONS -> blockAllNotifications = reader.nextBoolean()
                    TIME_WARNINGS -> timeWarnings = reader.nextInt()
                    MIN_BATTERY_LEVEL_CHARGING -> minBatteryLevelCharging = reader.nextInt()
                    MIN_BATTERY_LEVEL_MOBILE -> minBatteryLevelMobile = reader.nextInt()
                    SORT -> sort = reader.nextInt()
                    NETWORKS -> networks = ServerCategoryNetworkId.parseList(reader)
                    DISABLE_LIMITS_UNTIL -> disableLimitsUntil = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUpdatedCategoryBaseData(
                    categoryId = categoryId!!,
                    childId = childId!!,
                    title = title!!,
                    blockedMinutesInWeek = blockedMinutesInWeek!!,
                    extraTimeInMillis = extraTimeInMillis!!,
                    extraTimeDay = extraTimeDay,
                    temporarilyBlocked = temporarilyBlocked!!,
                    temporarilyBlockedEndTime = temporarilyBlockedEndTime,
                    baseDataVersion = baseDataVersion!!,
                    parentCategoryId = parentCategoryId!!,
                    blockAllNotifications = blockAllNotifications,
                    timeWarnings = timeWarnings,
                    minBatteryLevelCharging = minBatteryLevelCharging,
                    minBatteryLevelMobile = minBatteryLevelMobile,
                    sort = sort,
                    networks = networks,
                    disableLimitsUntil = disableLimitsUntil
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}

data class ServerCategoryNetworkId(val itemId: String, val hashedNetworkId: String) {
    companion object {
        private const val ITEM_ID = "itemId"
        private const val HASHED_NETWORK_ID = "hashedNetworkId"

        fun parse(reader: JsonReader): ServerCategoryNetworkId {
            var itemId: String? = null
            var hashedNetworkId: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ITEM_ID -> itemId = reader.nextString()
                    HASHED_NETWORK_ID -> hashedNetworkId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerCategoryNetworkId(
                    itemId = itemId!!,
                    hashedNetworkId = hashedNetworkId!!
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }

    init {
        IdGenerator.assertIdValid(itemId)
        HexString.assertIsHexString(hashedNetworkId)
        if (hashedNetworkId.length != CategoryNetworkId.ANONYMIZED_NETWORK_ID_LENGTH) throw IllegalArgumentException()
    }
}

data class ServerUpdatedCategoryAssignedApps(
        val categoryId: String,
        val assignedApps: List<String>,
        val version: String
) {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val ASSIGNED_APPS = "apps"
        private const val VERSION = "version"

        fun parse(reader: JsonReader): ServerUpdatedCategoryAssignedApps {
            var categoryId: String? = null
            var assignedApps: List<String>? = null
            var version: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    ASSIGNED_APPS -> {
                        reader.beginArray()
                        assignedApps = ArrayList<String>()

                        while (reader.hasNext()) {
                            assignedApps.add(reader.nextString())
                        }

                        assignedApps = Collections.unmodifiableList(assignedApps)
                        reader.endArray()
                    }
                    VERSION -> version = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUpdatedCategoryAssignedApps(
                    categoryId = categoryId!!,
                    assignedApps = assignedApps!!,
                    version = version!!
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) } }
}

data class ServerUpdatedCategoryUsedTimes(
        val categoryId: String,
        val usedTimeItems: List<ServerUsedTimeItem>,
        val sessionDurations: List<ServerSessionDuration>,
        val version: String
) {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val USED_TIMES_ITEMS = "times"
        private const val SESSION_DURATIONS = "sessionDurations"
        private const val VERSION = "version"

        fun parse(reader: JsonReader): ServerUpdatedCategoryUsedTimes {
            var categoryId: String? = null
            var usedTimeItems: List<ServerUsedTimeItem>? = null
            var sessionDurations = emptyList<ServerSessionDuration>()
            var version: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    USED_TIMES_ITEMS -> usedTimeItems = ServerUsedTimeItem.parseList(reader)
                    SESSION_DURATIONS -> sessionDurations = ServerSessionDuration.parseList(reader)
                    VERSION -> version = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUpdatedCategoryUsedTimes(
                    categoryId = categoryId!!,
                    usedTimeItems = usedTimeItems!!,
                    sessionDurations = sessionDurations,
                    version = version!!
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}

data class ServerUsedTimeItem(
        val dayOfEpoch: Int,
        val usedMillis: Long,
        val startTimeOfDay: Int,
        val endTimeOfDay: Int
) {
    companion object {
        private const val DAY_OF_EPOCH = "day"
        private const val USED_MILLIS = "time"
        private const val START_TIME_OF_DAY = "start"
        private const val END_TIME_OF_DAY = "end"

        fun parse(reader: JsonReader): ServerUsedTimeItem {
            var dayOfEpoch: Int? = null
            var usedMillis: Long? = null
            var startTimeOfDay: Int = MinuteOfDay.MIN
            var endTimeOfDay: Int = MinuteOfDay.MAX

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DAY_OF_EPOCH -> dayOfEpoch = reader.nextInt()
                    USED_MILLIS -> usedMillis = reader.nextLong()
                    START_TIME_OF_DAY -> startTimeOfDay = reader.nextInt()
                    END_TIME_OF_DAY -> endTimeOfDay = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUsedTimeItem(
                    dayOfEpoch = dayOfEpoch!!,
                    usedMillis = usedMillis!!,
                    startTimeOfDay = startTimeOfDay,
                    endTimeOfDay = endTimeOfDay
            )
        }

        fun parseList(reader: JsonReader): List<ServerUsedTimeItem> {
            val result = ArrayList<ServerUsedTimeItem>()

            reader.beginArray()
            while (reader.hasNext()) {
                result.add(parse(reader))
            }
            reader.endArray()

            return Collections.unmodifiableList(result)
        }
    }
}

data class ServerSessionDuration(
        val maxSessionDuration: Int,
        val sessionPauseDuration: Int,
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val lastUsage: Long,
        val lastSessionDuration: Long
) {
    companion object {
        private const val MAX_SESSION_DURATION = "md"
        private const val SESSION_PAUSE_DURATION = "spd"
        private const val START_MINUTE_OF_DAY = "sm"
        private const val END_MINUTE_OF_DAY = "em"
        private const val LAST_USAGE = "l"
        private const val LAST_SESSION_DURATION = "d"

        fun parse(reader: JsonReader): ServerSessionDuration {
            var maxSessionDuration: Int? = null
            var sessionPauseDuration: Int? = null
            var startMinuteOfDay: Int? = null
            var endMinuteOfDay: Int? = null
            var lastUsage: Long? = null
            var lastSessionDuration: Long? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    MAX_SESSION_DURATION -> maxSessionDuration = reader.nextInt()
                    SESSION_PAUSE_DURATION -> sessionPauseDuration = reader.nextInt()
                    START_MINUTE_OF_DAY -> startMinuteOfDay = reader.nextInt()
                    END_MINUTE_OF_DAY -> endMinuteOfDay = reader.nextInt()
                    LAST_USAGE -> lastUsage = reader.nextLong()
                    LAST_SESSION_DURATION -> lastSessionDuration = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerSessionDuration(
                    maxSessionDuration = maxSessionDuration!!,
                    sessionPauseDuration = sessionPauseDuration!!,
                    startMinuteOfDay = startMinuteOfDay!!,
                    endMinuteOfDay = endMinuteOfDay!!,
                    lastUsage = lastUsage!!,
                    lastSessionDuration = lastSessionDuration!!
            )
        }

        fun parseList(reader: JsonReader): List<ServerSessionDuration> {
            val result = ArrayList<ServerSessionDuration>()

            reader.beginArray()
            while (reader.hasNext()) {
                result.add(parse(reader))
            }
            reader.endArray()

            return Collections.unmodifiableList(result)
        }
    }
}

data class ServerUpdatedTimeLimitRules(
        val categoryId: String,
        val version: String,
        val rules: List<ServerTimeLimitRule>
) {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val VERSION = "version"
        private const val RULES = "rules"

        fun parse(reader: JsonReader): ServerUpdatedTimeLimitRules {
            var categoryId: String? = null
            var version: String? = null
            var rules: List<ServerTimeLimitRule>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when(reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    VERSION -> version = reader.nextString()
                    RULES -> rules = ServerTimeLimitRule.parseArray(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerUpdatedTimeLimitRules(
                    categoryId = categoryId!!,
                    version = version!!,
                    rules = rules!!
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}

data class ServerTimeLimitRule(
        val id: String,
        val applyToExtraTimeUsage: Boolean,
        val dayMask: Byte,
        val maximumTimeInMillis: Int,
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val sessionDurationMilliseconds: Int,
        val sessionPauseMilliseconds: Int
) {
    companion object {
        private const val ID = "id"
        private const val APPLY_TO_EXTRA_TIME_USAGE = "extraTime"
        private const val DAY_MASK = "dayMask"
        private const val MAXIMUM_TIME_IN_MILLIS = "maxTime"
        private const val START_MINUTE_OF_DAY = "start"
        private const val END_MINUTE_OF_DAY = "end"
        private const val SESSION_DURATION_MILLISECONDS = "session"
        private const val SESSION_PAUSE_MILLISECONDS = "pause"

        fun parse(reader: JsonReader): ServerTimeLimitRule {
            var id: String? = null
            var applyToExtraTimeUsage: Boolean? = null
            var dayMask: Byte? = null
            var maximumTimeInMillis: Int? = null
            var startMinuteOfDay = TimeLimitRule.MIN_START_MINUTE
            var endMinuteOfDay = TimeLimitRule.MAX_END_MINUTE
            var sessionDurationMilliseconds: Int = 0
            var sessionPauseMilliseconds: Int = 0

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    APPLY_TO_EXTRA_TIME_USAGE -> applyToExtraTimeUsage = reader.nextBoolean()
                    DAY_MASK -> dayMask = reader.nextInt().toByte()
                    MAXIMUM_TIME_IN_MILLIS -> maximumTimeInMillis = reader.nextInt()
                    START_MINUTE_OF_DAY -> startMinuteOfDay = reader.nextInt()
                    END_MINUTE_OF_DAY -> endMinuteOfDay = reader.nextInt()
                    SESSION_DURATION_MILLISECONDS -> sessionDurationMilliseconds = reader.nextInt()
                    SESSION_PAUSE_MILLISECONDS -> sessionPauseMilliseconds = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerTimeLimitRule(
                    id = id!!,
                    applyToExtraTimeUsage = applyToExtraTimeUsage!!,
                    dayMask = dayMask!!,
                    maximumTimeInMillis = maximumTimeInMillis!!,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    sessionDurationMilliseconds = sessionDurationMilliseconds,
                    sessionPauseMilliseconds = sessionPauseMilliseconds
            )
        }

        fun parseArray(reader: JsonReader): List<ServerTimeLimitRule> {
            val result = ArrayList<ServerTimeLimitRule>()

            reader.beginArray()
            while (reader.hasNext()) {
                result.add(parse(reader))
            }
            reader.endArray()

            return Collections.unmodifiableList(result)
        }
    }

    fun toRealRule(categoryId: String) = TimeLimitRule(
            id = id,
            applyToExtraTimeUsage = applyToExtraTimeUsage,
            dayMask = dayMask,
            maximumTimeInMillis = maximumTimeInMillis,
            categoryId = categoryId,
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            sessionDurationMilliseconds = sessionDurationMilliseconds,
            sessionPauseMilliseconds = sessionPauseMilliseconds
    )
}

data class ServerInstalledAppsData(
        val deviceId: String,
        val version: String,
        val apps: List<InstalledApp>,
        val activities: List<AppActivityItem>
) {
    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val VERSION = "version"
        private const val APPS = "apps"
        private const val ACTIVITIES = "activities"

        fun parse(reader: JsonReader): ServerInstalledAppsData {
            var deviceId: String? = null
            var version: String? = null
            var apps: List<InstalledApp>? = null
            var activities: List<AppActivityItem>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when(reader.nextName()) {
                    DEVICE_ID -> deviceId = reader.nextString()
                    VERSION -> version = reader.nextString()
                    APPS -> apps = InstalledApp.parseList(reader)
                    ACTIVITIES -> activities = reader.parseList { AppActivityItem.parse(it) }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ServerInstalledAppsData(
                    deviceId = deviceId!!,
                    version = version!!,
                    apps = apps!!,
                    activities = activities!!
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }
}
