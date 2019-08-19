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
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson
import io.timelimit.android.util.parseJsonArray
import java.util.*

@Entity(tableName = "user")
@TypeConverters(
        UserTypeConverter::class,
        ImmutableBitmaskAdapter::class
)
data class User(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "password")
        val password: String,   // protected using bcrypt, can be empty if not configured
        @ColumnInfo(name = "second_password_salt")
        val secondPasswordSalt: String,
        @ColumnInfo(name = "type")
        val type: UserType,
        @ColumnInfo(name = "timezone")
        val timeZone: String,
        // 0 = time limits enabled
        @ColumnInfo(name = "disable_limits_until")
        val disableLimitsUntil: Long,
        @ColumnInfo(name = "mail")
        val mail: String,
        // empty = unset; can contain an invalid device id or the id of an device which is not used by this user
        // in this case, it should be treated like unset
        @ColumnInfo(name = "current_device")
        val currentDevice: String,
        @ColumnInfo(name = "category_for_not_assigned_apps")
        // empty or invalid = no category
        val categoryForNotAssignedApps: String,
        @ColumnInfo(name = "relax_primary_device")
        val relaxPrimaryDevice: Boolean,
        @ColumnInfo(name = "mail_notification_flags")
        val mailNotificationFlags: Int,
        @ColumnInfo(name = "blocked_times")
        val blockedTimes: ImmutableBitmask
): JsonSerializable {
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
        private const val BLOCKED_TIMES = "blockedTimes"

        fun parse(reader: JsonReader): User {
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
                    BLOCKED_TIMES -> blockedTimes = ImmutableBitmaskJson.parse(reader.nextString(), Category.BLOCKED_MINUTES_IN_WEEK_LENGTH)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return User(
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
                    blockedTimes = blockedTimes
            )
        }

        fun parseList(reader: JsonReader) = parseJsonArray(reader) { parse(reader) }
    }

    init {
        IdGenerator.assertIdValid(id)

        if (disableLimitsUntil < 0) {
            throw IllegalArgumentException()
        }

        if (name.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (timeZone.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (currentDevice.isNotEmpty()) {
            IdGenerator.assertIdValid(currentDevice)
        }

        if (categoryForNotAssignedApps.isNotEmpty()) {
            IdGenerator.assertIdValid(categoryForNotAssignedApps)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(NAME).value(name)
        writer.name(PASSWORD).value(password)
        writer.name(SECOND_PASSWORD_SALT).value(secondPasswordSalt)
        writer.name(TYPE).value(UserTypeJson.serialize(type))
        writer.name(TIMEZONE).value(timeZone)
        writer.name(DISABLE_LIMITS_UNTIL).value(disableLimitsUntil)
        writer.name(MAIL).value(mail)
        writer.name(CURRENT_DEVICE).value(currentDevice)
        writer.name(CATEGORY_FOR_NOT_ASSIGNED_APPS).value(categoryForNotAssignedApps)
        writer.name(RELAX_PRIMARY_DEVICE).value(relaxPrimaryDevice)
        writer.name(MAIL_NOTIFICATION_FLAGS).value(mailNotificationFlags)
        writer.name(BLOCKED_TIMES).value(ImmutableBitmaskJson.serialize(blockedTimes))

        writer.endObject()
    }
}

enum class UserType {
    Parent, Child
}

object UserTypeJson {
    private const val PARENT = "parent"
    private const val CHILD = "child"

    fun parse(value: String) = when(value) {
        PARENT -> UserType.Parent
        CHILD -> UserType.Child
        else -> throw IllegalArgumentException()
    }

    fun serialize(value: UserType) = when(value) {
        UserType.Parent -> PARENT
        UserType.Child -> CHILD
    }
}

class UserTypeConverter {
    @TypeConverter
    fun toUserType(value: String) = UserTypeJson.parse(value)

    @TypeConverter
    fun toString(value: UserType) = UserTypeJson.serialize(value)
}
