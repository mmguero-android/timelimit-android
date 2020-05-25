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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import io.timelimit.android.data.JsonSerializable

@Entity(
        tableName = "notification",
        primaryKeys = ["type", "id"]
)
data class Notification(
    val type: Int,
    val id: String,
    @ColumnInfo(name = "first_notify_time")
    val firstNotifyTime: Long,
    @ColumnInfo(name = "dismissed")
    val isDismissed: Boolean
): JsonSerializable {
    companion object {
        private const val TYPE = "t"
        private const val ID = "i"
        private const val FIRST_NOTIFY_TIME = "f"
        private const val IS_DISMISSED = "d"

        fun parse(reader: JsonReader): Notification {
            var type: Int? = null
            var id: String? = null
            var firstNotifyTime: Long? = null
            var isDismissed: Boolean? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    TYPE -> type = reader.nextInt()
                    ID -> id = reader.nextString()
                    FIRST_NOTIFY_TIME -> firstNotifyTime = reader.nextLong()
                    IS_DISMISSED -> isDismissed = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return Notification(
                    type = type!!,
                    id = id!!,
                    firstNotifyTime = firstNotifyTime!!,
                    isDismissed = isDismissed!!
            )
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TYPE).value(type)
        writer.name(ID).value(id)
        writer.name(FIRST_NOTIFY_TIME).value(firstNotifyTime)
        writer.name(IS_DISMISSED).value(isDismissed)

        writer.endObject()
    }
}

object NotificationTypes {
    const val UPDATE_MISSING = 1 shl 1
    const val MANIPULATION = 1 shl 2
    const val PREMIUM_EXPIRES = 1 shl 3
}