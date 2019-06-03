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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.*
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.integration.platform.*

@Entity(tableName = "device")
@TypeConverters(
        NetworkTimeAdapter::class,
        ProtectionLevelConverter::class,
        RuntimePermissionStatusConverter::class,
        NewPermissionStatusConverter::class
)
data class Device(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "model")
        val model: String,
        @ColumnInfo(name = "added_at")
        val addedAt: Long,
        @ColumnInfo(name = "current_user_id")
        val currentUserId: String,      // empty if not set
        @ColumnInfo(name = "apps_version")
        val installedAppsVersion: String,
        @ColumnInfo(name = "network_time")
        val networkTime: NetworkTime,
        @ColumnInfo(name = "current_protection_level")
        val currentProtectionLevel: ProtectionLevel,
        @ColumnInfo(name = "highest_permission_level")
        val highestProtectionLevel: ProtectionLevel,
        @ColumnInfo(name = "current_usage_stats_permission")
        val currentUsageStatsPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "highest_usage_stats_permission")
        val highestUsageStatsPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "current_notification_access_permission")
        val currentNotificationAccessPermission: NewPermissionStatus,
        @ColumnInfo(name = "highest_notification_access_permission")
        val highestNotificationAccessPermission: NewPermissionStatus,
        @ColumnInfo(name = "current_app_version")
        val currentAppVersion: Int,
        @ColumnInfo(name = "highest_app_version")
        val highestAppVersion: Int,
        @ColumnInfo(name = "tried_disabling_device_admin")
        val manipulationTriedDisablingDeviceAdmin: Boolean,
        @ColumnInfo(name = "did_reboot")
        val manipulationDidReboot: Boolean,
        @ColumnInfo(name = "had_manipulation")
        val hadManipulation: Boolean,
        @ColumnInfo(name = "did_report_uninstall")
        val didReportUninstall: Boolean,
        @ColumnInfo(name = "is_user_kept_signed_in")
        val isUserKeptSignedIn: Boolean,
        @ColumnInfo(name = "show_device_connected")
        val showDeviceConnected: Boolean,
        @ColumnInfo(name = "default_user")
        val defaultUser: String,
        @ColumnInfo(name = "default_user_timeout")
        val defaultUserTimeout: Int,
        @ColumnInfo(name = "consider_reboot_manipulation")
        val considerRebootManipulation: Boolean,
        @ColumnInfo(name = "current_overlay_permission")
        val currentOverlayPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "highest_overlay_permission")
        val highestOverlayPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "current_accessibility_service_permission")
        val accessibilityServiceEnabled: Boolean,
        @ColumnInfo(name = "was_accessibility_service_permission")
        val wasAccessibilityServiceEnabled: Boolean,
        @ColumnInfo(name = "enable_activity_level_blocking")
        val enableActivityLevelBlocking: Boolean,
        @ColumnInfo(name = "q_or_later")
        val qOrLater: Boolean
): JsonSerializable {
    companion object {
        private const val ID = "id"
        private const val NAME = "n"
        private const val MODEL = "m"
        private const val ADDED_AT = "aa"
        private const val CURRENT_USER_ID = "u"
        private const val INSTALLED_APPS_VERSION = "va"
        private const val NETWORK_TIME = "t"
        private const val CURRENT_PROTECTION_LEVEL = "pc"
        private const val HIGHEST_PROTECTION_LEVEL = "pm"
        private const val CURRENT_USAGE_STATS_PERMISSION = "uc"
        private const val HIGHEST_USAGE_STATS_PERMISSION = "um"
        private const val CURRENT_NOTIFICATION_ACCESS_PERMISSION = "nc"
        private const val HIGHEST_NOTIFICATION_ACCESS_PERMISSION = "nm"
        private const val CURRENT_APP_VERSION = "ac"
        private const val HIGHEST_APP_VERSION = "am"
        private const val TRIED_DISABLING_DEVICE_ADMIN = "tdda"
        private const val MANIPULATION_DID_REBOOT = "mdr"
        private const val HAD_MANIPULATION = "hm"
        private const val DID_REPORT_UNINSTALL = "dru"
        private const val IS_USER_KEPT_SIGNED_IN = "iuksi"
        private const val SHOW_DEVICE_CONNECTED = "sdc"
        private const val DEFAULT_USER = "du"
        private const val DEFAULT_USER_TIMEOUT = "dut"
        private const val CONSIDER_REBOOT_A_MANIPULATION = "cram"
        private const val CURRENT_OVERLAY_PERMISSION = "cop"
        private const val HIGHEST_OVERLAY_PERMISSION = "hop"
        private const val ACCESSIBILITY_SERVICE_ENABLED = "ase"
        private const val WAS_ACCESSIBILITY_SERVICE_ENABLED = "wase"
        private const val ENABLE_ACTIVITY_LEVEL_BLOCKING = "ealb"
        private const val Q_OR_LATER = "qol"

        fun parse(reader: JsonReader): Device {
            var id: String? = null
            var name: String? = null
            var model: String? = null
            var addedAt: Long? = null
            var currentUserId: String? = null
            var installedAppsVersion: String? = null
            var networkTime: NetworkTime? = null
            var currentProtectionLevel: ProtectionLevel? = null
            var highestProtectionLevel: ProtectionLevel? = null
            var currentUsageStatsPermission: RuntimePermissionStatus? = null
            var highestUsageStatsPermission: RuntimePermissionStatus? = null
            var currentNotificationAccessPermission: NewPermissionStatus? = null
            var highestNotificationAccessPermission: NewPermissionStatus? = null
            var currentAppVersion: Int? = null
            var highestAppVersion: Int? = null
            var manipulationTriedDisablingDeviceAdmin: Boolean? = null
            var manipulationDidReboot: Boolean = false
            var hadManipulation: Boolean? = null
            var didReportUninstall = false  // this was added later, so it has got a default value
            var isUserKeptSignedIn = false
            var showDeviceConnected = false
            var defaultUser = ""
            var defaultUserTimeout = 0
            var considerRebootManipulation = false
            var currentOverlayPermission = RuntimePermissionStatus.NotGranted
            var highestOverlayPermission = RuntimePermissionStatus.NotGranted
            var accessibilityServiceEnabled = false
            var wasAccessibilityServiceEnabled = false
            var enableActivityLevelBlocking = false
            var qOrLater = false

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    NAME -> name = reader.nextString()
                    MODEL -> model = reader.nextString()
                    ADDED_AT -> addedAt = reader.nextLong()
                    CURRENT_USER_ID -> currentUserId = reader.nextString()
                    INSTALLED_APPS_VERSION -> installedAppsVersion = reader.nextString()
                    NETWORK_TIME -> networkTime = NetworkTimeJson.parse(reader.nextString())
                    CURRENT_PROTECTION_LEVEL -> currentProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    HIGHEST_PROTECTION_LEVEL -> highestProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    CURRENT_USAGE_STATS_PERMISSION -> currentUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_USAGE_STATS_PERMISSION -> highestUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    CURRENT_NOTIFICATION_ACCESS_PERMISSION -> currentNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_NOTIFICATION_ACCESS_PERMISSION -> highestNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    CURRENT_APP_VERSION -> currentAppVersion = reader.nextInt()
                    HIGHEST_APP_VERSION -> highestAppVersion = reader.nextInt()
                    TRIED_DISABLING_DEVICE_ADMIN -> manipulationTriedDisablingDeviceAdmin = reader.nextBoolean()
                    MANIPULATION_DID_REBOOT -> manipulationDidReboot = reader.nextBoolean()
                    HAD_MANIPULATION -> hadManipulation = reader.nextBoolean()
                    DID_REPORT_UNINSTALL -> didReportUninstall = reader.nextBoolean()
                    IS_USER_KEPT_SIGNED_IN -> isUserKeptSignedIn = reader.nextBoolean()
                    SHOW_DEVICE_CONNECTED -> showDeviceConnected = reader.nextBoolean()
                    DEFAULT_USER -> defaultUser = reader.nextString()
                    DEFAULT_USER_TIMEOUT -> defaultUserTimeout = reader.nextInt()
                    CONSIDER_REBOOT_A_MANIPULATION -> considerRebootManipulation = reader.nextBoolean()
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

            return Device(
                    id = id!!,
                    name = name!!,
                    model = model!!,
                    addedAt = addedAt!!,
                    currentUserId = currentUserId!!,
                    installedAppsVersion = installedAppsVersion!!,
                    networkTime = networkTime!!,
                    currentProtectionLevel = currentProtectionLevel!!,
                    highestProtectionLevel = highestProtectionLevel!!,
                    currentUsageStatsPermission = currentUsageStatsPermission!!,
                    highestUsageStatsPermission = highestUsageStatsPermission!!,
                    currentNotificationAccessPermission = currentNotificationAccessPermission!!,
                    highestNotificationAccessPermission = highestNotificationAccessPermission!!,
                    currentAppVersion = currentAppVersion!!,
                    highestAppVersion = highestAppVersion!!,
                    manipulationTriedDisablingDeviceAdmin = manipulationTriedDisablingDeviceAdmin!!,
                    manipulationDidReboot = manipulationDidReboot,
                    hadManipulation = hadManipulation!!,
                    didReportUninstall = didReportUninstall,
                    isUserKeptSignedIn = isUserKeptSignedIn,
                    showDeviceConnected = showDeviceConnected,
                    defaultUser = defaultUser,
                    defaultUserTimeout = defaultUserTimeout,
                    considerRebootManipulation = considerRebootManipulation,
                    currentOverlayPermission = currentOverlayPermission,
                    highestOverlayPermission = highestOverlayPermission,
                    accessibilityServiceEnabled = accessibilityServiceEnabled,
                    wasAccessibilityServiceEnabled = wasAccessibilityServiceEnabled,
                    enableActivityLevelBlocking = enableActivityLevelBlocking,
                    qOrLater = qOrLater
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)

        if (currentUserId.isNotEmpty()) {
            IdGenerator.assertIdValid(currentUserId)
        }

        if (name.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (model.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (addedAt < 0) {
            throw IllegalArgumentException()
        }

        if (currentAppVersion < 0 || highestAppVersion < 0) {
            throw IllegalArgumentException()
        }

        if (defaultUser.isNotEmpty()) {
            IdGenerator.assertIdValid(defaultUser)
        }

        if (defaultUserTimeout < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(NAME).value(name)
        writer.name(MODEL).value(model)
        writer.name(ADDED_AT).value(addedAt)
        writer.name(CURRENT_USER_ID).value(currentUserId)
        writer.name(INSTALLED_APPS_VERSION).value(installedAppsVersion)
        writer.name(NETWORK_TIME).value(NetworkTimeJson.serialize(networkTime))
        writer.name(CURRENT_PROTECTION_LEVEL).value(ProtectionLevelUtil.serialize(currentProtectionLevel))
        writer.name(HIGHEST_PROTECTION_LEVEL).value(ProtectionLevelUtil.serialize(highestProtectionLevel))
        writer.name(CURRENT_USAGE_STATS_PERMISSION).value(RuntimePermissionStatusUtil.serialize(currentUsageStatsPermission))
        writer.name(HIGHEST_USAGE_STATS_PERMISSION).value(RuntimePermissionStatusUtil.serialize(highestUsageStatsPermission))
        writer.name(CURRENT_NOTIFICATION_ACCESS_PERMISSION).value(NewPermissionStatusUtil.serialize(currentNotificationAccessPermission))
        writer.name(HIGHEST_NOTIFICATION_ACCESS_PERMISSION).value(NewPermissionStatusUtil.serialize(highestNotificationAccessPermission))
        writer.name(CURRENT_APP_VERSION).value(currentAppVersion)
        writer.name(HIGHEST_APP_VERSION).value(highestAppVersion)
        writer.name(TRIED_DISABLING_DEVICE_ADMIN).value(manipulationTriedDisablingDeviceAdmin)
        writer.name(MANIPULATION_DID_REBOOT).value(manipulationDidReboot)
        writer.name(HAD_MANIPULATION).value(hadManipulation)
        writer.name(DID_REPORT_UNINSTALL).value(didReportUninstall)
        writer.name(IS_USER_KEPT_SIGNED_IN).value(isUserKeptSignedIn)
        writer.name(SHOW_DEVICE_CONNECTED).value(showDeviceConnected)
        writer.name(DEFAULT_USER).value(defaultUser)
        writer.name(DEFAULT_USER_TIMEOUT).value(defaultUserTimeout)
        writer.name(CONSIDER_REBOOT_A_MANIPULATION).value(considerRebootManipulation)
        writer.name(CURRENT_OVERLAY_PERMISSION).value(RuntimePermissionStatusUtil.serialize(currentOverlayPermission))
        writer.name(HIGHEST_OVERLAY_PERMISSION).value(RuntimePermissionStatusUtil.serialize(highestOverlayPermission))
        writer.name(ACCESSIBILITY_SERVICE_ENABLED).value(accessibilityServiceEnabled)
        writer.name(WAS_ACCESSIBILITY_SERVICE_ENABLED).value(wasAccessibilityServiceEnabled)
        writer.name(ENABLE_ACTIVITY_LEVEL_BLOCKING).value(enableActivityLevelBlocking)
        writer.name(Q_OR_LATER).value(qOrLater)

        writer.endObject()
    }

    @Transient
    val manipulationOfProtectionLevel = currentProtectionLevel != highestProtectionLevel
    @Transient
    val manipulationOfUsageStats = currentUsageStatsPermission != highestUsageStatsPermission
    @Transient
    val manipulationOfNotificationAccess = currentNotificationAccessPermission != highestNotificationAccessPermission
    @Transient
    val manipulationOfAppVersion = currentAppVersion != highestAppVersion
    @Transient
    val manipulationOfOverlayPermission = currentOverlayPermission != highestOverlayPermission
    @Transient
    val manipulationOfAccessibilityService = accessibilityServiceEnabled != wasAccessibilityServiceEnabled

    @Transient
    val hasActiveManipulationWarning = manipulationOfProtectionLevel ||
            manipulationOfUsageStats ||
            manipulationOfNotificationAccess ||
            manipulationOfAppVersion ||
            manipulationTriedDisablingDeviceAdmin ||
            manipulationDidReboot ||
            manipulationOfOverlayPermission ||
            manipulationOfAccessibilityService

    @Transient
    val hasAnyManipulation = hasActiveManipulationWarning || hadManipulation
}

enum class NetworkTime {
    Disabled, IfPossible, Enabled
}

object NetworkTimeJson {
    private const val DISABLED = "disabled"
    private const val IF_POSSIBLE = "if possible"
    private const val ENABLED = "enabled"

    fun parse(value: String) = when(value) {
        DISABLED -> NetworkTime.Disabled
        IF_POSSIBLE -> NetworkTime.IfPossible
        ENABLED -> NetworkTime.Enabled
        else -> throw IllegalArgumentException()
    }

    fun serialize(value: NetworkTime) = when(value) {
        NetworkTime.Disabled -> DISABLED
        NetworkTime.IfPossible -> IF_POSSIBLE
        NetworkTime.Enabled -> ENABLED
    }
}

class NetworkTimeAdapter {
    @TypeConverter
    fun toString(networkTime: NetworkTime) = NetworkTimeJson.serialize(networkTime)

    @TypeConverter
    fun toNetworkTime(value: String) = NetworkTimeJson.parse(value)
}
