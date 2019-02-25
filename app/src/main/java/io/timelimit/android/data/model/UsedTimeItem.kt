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
import androidx.room.ColumnInfo
import androidx.room.Entity
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(primaryKeys = ["category_id", "day_of_epoch"], tableName = "used_time")
data class UsedTimeItem(
        @ColumnInfo(name = "day_of_epoch")
        val dayOfEpoch: Int,
        @ColumnInfo(name = "used_time")
        val usedMillis: Long,
        @ColumnInfo(name = "category_id")
        val categoryId: String
): JsonSerializable {
    companion object {
        private const val DAY_OF_EPOCH = "day"
        private const val USED_TIME_MILLIS = "time"
        private const val CATEGORY_ID = "category"

        fun parse(reader: JsonReader): UsedTimeItem {
            reader.beginObject()

            var dayOfEpoch: Int? = null
            var usedMillis: Long? = null
            var categoryId: String? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DAY_OF_EPOCH -> dayOfEpoch = reader.nextInt()
                    USED_TIME_MILLIS -> usedMillis = reader.nextLong()
                    CATEGORY_ID -> categoryId = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return UsedTimeItem(
                    dayOfEpoch = dayOfEpoch!!,
                    usedMillis = usedMillis!!,
                    categoryId = categoryId!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (dayOfEpoch < 0) {
            throw IllegalArgumentException()
        }

        if (usedMillis < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DAY_OF_EPOCH).value(dayOfEpoch)
        writer.name(USED_TIME_MILLIS).value(usedMillis)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.endObject()
    }
}
