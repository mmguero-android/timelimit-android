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
import io.timelimit.android.data.JsonSerializable

@Entity(tableName = "config")
@TypeConverters(ConfigurationItemTypeConverter::class)
data class ConfigurationItem(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val key: ConfigurationItemType,
        @ColumnInfo(name = "value")
        val value: String
): JsonSerializable {
    companion object {
        private const val KEY = "k"
        private const val VALUE = "v"

        // returns null if parsing failed
        fun parse(reader: JsonReader): ConfigurationItem? {
            var key: Int? = null
            var value: String? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    KEY -> key = reader.nextInt()
                    VALUE -> value = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            key!!
            value!!

            try {
                return ConfigurationItem(
                        key = ConfigurationItemTypeUtil.parse(key),
                        value = value
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(KEY).value(ConfigurationItemTypeUtil.serialize(key))
        writer.name(VALUE).value(value)

        writer.endObject()
    }
}

// TODO: validate config item values

enum class ConfigurationItemType {
    OwnDeviceId,
    UserListVersion,
    DeviceListVersion,
    NextSyncSequenceNumber,
    DeviceAuthToken,
    FullVersionUntil,
    ShownHints,
    WasDeviceLocked,
    LastAppVersionWhichSynced,
    LastScreenOnTime,
    ServerMessage,
    CustomServerUrl,
    ForegroundAppQueryRange,
    EnableBackgroundSync,
    EnableAlternativeDurationSelection,
    ExperimentalFlags
}

object ConfigurationItemTypeUtil {
    private const val OWN_DEVICE_ID = 1
    private const val USER_LIST_VERSION = 2
    private const val DEVICE_LIST_VERSION = 3
    private const val NEXT_SYNC_SEQUENCE_NUMBER = 4
    private const val DEVICE_AUTH_TOKEN = 5
    private const val FULL_VERSION_UNTIL = 6
    private const val SHOWN_HINTS = 7
    private const val WAS_DEVICE_LOCKED = 9
    private const val LAST_APP_VERSION_WHICH_SYNCED = 10
    private const val LAST_SCREEN_ON_TIME = 11
    private const val SERVER_MESSAGE = 12
    private const val CUSTOM_SERVER_URL = 13
    private const val FOREGROUND_APP_QUERY_RANGE = 14
    private const val ENABLE_BACKGROUND_SYNC = 15
    private const val ENABLE_ALTERNATIVE_DURATION_SELECTION = 16
    private const val EXPERIMENTAL_FLAGS = 17

    val TYPES = listOf(
            ConfigurationItemType.OwnDeviceId,
            ConfigurationItemType.UserListVersion,
            ConfigurationItemType.DeviceListVersion,
            ConfigurationItemType.NextSyncSequenceNumber,
            ConfigurationItemType.DeviceAuthToken,
            ConfigurationItemType.FullVersionUntil,
            ConfigurationItemType.ShownHints,
            ConfigurationItemType.WasDeviceLocked,
            ConfigurationItemType.LastAppVersionWhichSynced,
            ConfigurationItemType.LastScreenOnTime,
            ConfigurationItemType.ServerMessage,
            ConfigurationItemType.CustomServerUrl,
            ConfigurationItemType.ForegroundAppQueryRange,
            ConfigurationItemType.EnableBackgroundSync,
            ConfigurationItemType.EnableAlternativeDurationSelection,
            ConfigurationItemType.ExperimentalFlags
    )

    fun serialize(value: ConfigurationItemType) = when(value) {
        ConfigurationItemType.OwnDeviceId -> OWN_DEVICE_ID
        ConfigurationItemType.UserListVersion -> USER_LIST_VERSION
        ConfigurationItemType.DeviceListVersion -> DEVICE_LIST_VERSION
        ConfigurationItemType.NextSyncSequenceNumber -> NEXT_SYNC_SEQUENCE_NUMBER
        ConfigurationItemType.DeviceAuthToken -> DEVICE_AUTH_TOKEN
        ConfigurationItemType.FullVersionUntil -> FULL_VERSION_UNTIL
        ConfigurationItemType.ShownHints -> SHOWN_HINTS
        ConfigurationItemType.WasDeviceLocked -> WAS_DEVICE_LOCKED
        ConfigurationItemType.LastAppVersionWhichSynced -> LAST_APP_VERSION_WHICH_SYNCED
        ConfigurationItemType.LastScreenOnTime -> LAST_SCREEN_ON_TIME
        ConfigurationItemType.ServerMessage -> SERVER_MESSAGE
        ConfigurationItemType.CustomServerUrl -> CUSTOM_SERVER_URL
        ConfigurationItemType.ForegroundAppQueryRange -> FOREGROUND_APP_QUERY_RANGE
        ConfigurationItemType.EnableBackgroundSync -> ENABLE_BACKGROUND_SYNC
        ConfigurationItemType.EnableAlternativeDurationSelection -> ENABLE_ALTERNATIVE_DURATION_SELECTION
        ConfigurationItemType.ExperimentalFlags -> EXPERIMENTAL_FLAGS
    }

    fun parse(value: Int) = when(value) {
        OWN_DEVICE_ID -> ConfigurationItemType.OwnDeviceId
        USER_LIST_VERSION -> ConfigurationItemType.UserListVersion
        DEVICE_LIST_VERSION -> ConfigurationItemType.DeviceListVersion
        NEXT_SYNC_SEQUENCE_NUMBER -> ConfigurationItemType.NextSyncSequenceNumber
        DEVICE_AUTH_TOKEN -> ConfigurationItemType.DeviceAuthToken
        FULL_VERSION_UNTIL -> ConfigurationItemType.FullVersionUntil
        SHOWN_HINTS -> ConfigurationItemType.ShownHints
        WAS_DEVICE_LOCKED -> ConfigurationItemType.WasDeviceLocked
        LAST_APP_VERSION_WHICH_SYNCED -> ConfigurationItemType.LastAppVersionWhichSynced
        LAST_SCREEN_ON_TIME -> ConfigurationItemType.LastScreenOnTime
        SERVER_MESSAGE -> ConfigurationItemType.ServerMessage
        CUSTOM_SERVER_URL -> ConfigurationItemType.CustomServerUrl
        FOREGROUND_APP_QUERY_RANGE -> ConfigurationItemType.ForegroundAppQueryRange
        ENABLE_BACKGROUND_SYNC -> ConfigurationItemType.EnableBackgroundSync
        ENABLE_ALTERNATIVE_DURATION_SELECTION -> ConfigurationItemType.EnableAlternativeDurationSelection
        EXPERIMENTAL_FLAGS -> ConfigurationItemType.ExperimentalFlags
        else -> throw IllegalArgumentException()
    }
}

class ConfigurationItemTypeConverter {
    @TypeConverter
    fun toInt(value: ConfigurationItemType) = ConfigurationItemTypeUtil.serialize(value)

    @TypeConverter
    fun toConfigurationItemType(value: Int) = ConfigurationItemTypeUtil.parse(value)
}

object HintsToShow {
    const val OVERVIEW_INTRODUCTION = 1L
    const val DEVICE_SCREEN_INTRODUCTION = 2L
    const val CATEGORIES_INTRODUCTION = 4L
    const val TIME_LIMIT_RULE_INTRODUCTION = 8L
    const val CONTACTS_INTRO = 16L
    const val TIMELIMIT_RULE_MUSTREAD = 32L
}

object ExperimentalFlags {
    const val DISABLE_BLOCK_ON_MANIPULATION = 1L
    const val SYSTEM_LEVEL_BLOCKING = 2L
}