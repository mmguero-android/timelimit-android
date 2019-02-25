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

/*
 * parent and child actions must be signed
 * This happens with the following:
 * 1. The passwords for parents are stored 2 times
 *    1. encrypted with bcrypt
 *    2. encrypted with bcrypt, but the clients only keep the salt, only the server stores the full hash
 * 2. The full hash of the second sample (KEY) is used as a base for signing
 * 3. The integrity data is SHA512asHexString(sequenceNumber.toString() + deviceId + encodedAction + KEY).substring(0, 32)
 *
 * The integrity for child actions is empty
 */

@Entity(tableName = "pending_sync_action")
@TypeConverters(PendingSyncActionAdapter::class)
data class PendingSyncAction(
        @ColumnInfo(name = "sequence_number")
        @PrimaryKey
        val sequenceNumber: Long,
        @ColumnInfo(name = "action")
        val encodedAction: String,
        @ColumnInfo(name = "integrity")
        val integrity: String,
        @ColumnInfo(name = "scheduled_for_upload", index = true)
        // actions can be modified/ merged if they were not yet scheduled for an upload
        // this merging is made by the upload function
        val scheduledForUpload: Boolean,
        @ColumnInfo(name = "type")
        val type: PendingSyncActionType,
        @ColumnInfo(name = "user_id")
        val userId: String
): JsonSerializable {
    companion object {
        private const val SEQUENCE_NUMBER = "n"
        private const val ENCODED_ACTION = "a"
        private const val INTEGRITY = "i"
        private const val SCHEDULED_FOR_UPLOAD = "s"
        private const val TYPE = "t"
        private const val TYPE_NEW = "t2"
        private const val USER_ID = "u"

        fun parse(reader: JsonReader): PendingSyncAction {
            var sequenceNumber: Long? = null
            var encodedAction: String? = null
            var integrity: String? = null
            var scheduledForUpload: Boolean? = null
            var type: PendingSyncActionType? = null
            var typeNew: PendingSyncActionType? = null
            var userId: String? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    SEQUENCE_NUMBER -> sequenceNumber = reader.nextLong()
                    ENCODED_ACTION -> encodedAction = reader.nextString()
                    INTEGRITY -> integrity = reader.nextString()
                    SCHEDULED_FOR_UPLOAD -> scheduledForUpload = reader.nextBoolean()
                    TYPE -> type = PendingSyncActionTypeConverter.parse(reader.nextString())
                    TYPE_NEW -> typeNew = PendingSyncActionTypeConverter.parse(reader.nextString())
                    USER_ID -> userId = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return PendingSyncAction(
                    sequenceNumber = sequenceNumber!!,
                    encodedAction = encodedAction!!,
                    integrity = integrity!!,
                    scheduledForUpload = scheduledForUpload!!,
                    type = (typeNew ?: type)!!,
                    userId = userId!!
            )
        }
    }

    init {
        if (userId.isNotEmpty()) {
            IdGenerator.assertIdValid(userId)
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(SEQUENCE_NUMBER).value(sequenceNumber)
        writer.name(ENCODED_ACTION).value(encodedAction)
        writer.name(INTEGRITY).value(integrity)
        writer.name(SCHEDULED_FOR_UPLOAD).value(scheduledForUpload)
        writer.name(TYPE).value(
                PendingSyncActionTypeConverter.serialize(
                        if (type != PendingSyncActionType.Child) {
                            type
                        } else {
                            // this will make the actions fail, but prevents a crash after downgrades
                            PendingSyncActionType.Parent
                        }
                )
        )
        writer.name(TYPE_NEW).value(PendingSyncActionTypeConverter.serialize(type))
        writer.name(USER_ID).value(userId)

        writer.endObject()
    }
}

enum class PendingSyncActionType {
    Parent, AppLogic, Child
}

object PendingSyncActionTypeConverter {
    private const val APP_LOGIC = "appLogic"
    private const val PARENT = "parent"
    private const val CHILD = "child"

    fun serialize(type: PendingSyncActionType) = when(type) {
        PendingSyncActionType.Parent -> PARENT
        PendingSyncActionType.AppLogic -> APP_LOGIC
        PendingSyncActionType.Child -> CHILD
    }

    fun parse(value: String) = when(value) {
        PARENT -> PendingSyncActionType.Parent
        APP_LOGIC -> PendingSyncActionType.AppLogic
        CHILD -> PendingSyncActionType.Child
        else -> throw IllegalStateException()
    }
}

class PendingSyncActionAdapter {
    @TypeConverter
    fun toString(value: PendingSyncActionType) = PendingSyncActionTypeConverter.serialize(value)

    @TypeConverter
    fun toPendingSyncActionType(value: String) = PendingSyncActionTypeConverter.parse(value)
}
