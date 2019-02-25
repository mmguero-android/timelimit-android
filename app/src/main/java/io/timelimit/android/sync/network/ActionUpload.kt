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
package io.timelimit.android.sync.network

import android.util.JsonReader
import android.util.JsonWriter
import io.timelimit.android.data.model.PendingSyncActionType
import io.timelimit.android.data.model.PendingSyncActionTypeConverter

data class ActionUploadRequest(
        val deviceAuthToken: String,
        val actions: List<ActionUploadItem>
) {
    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name("deviceAuthToken").value(deviceAuthToken)

        writer.name("actions")
        writer.beginArray()
        actions.forEach { it.serialize(writer) }
        writer.endArray()

        writer.endObject()
    }
}

data class ActionUploadItem(
        val encodedAction: String,
        val sequenceNumber: Long,
        val integrity: String,
        val type: PendingSyncActionType,
        val userId: String
) {
    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name("encodedAction").value(encodedAction)
        writer.name("sequenceNumber").value(sequenceNumber)
        writer.name("integrity").value(integrity)
        writer.name("type").value(PendingSyncActionTypeConverter.serialize(type))
        writer.name("userId").value(userId)

        writer.endObject()
    }
}

data class ActionUploadResponse(
        val shouldDoFullSync: Boolean
) {
    companion object {
        fun parse(reader: JsonReader): ActionUploadResponse {
            var shouldDoFullSync: Boolean? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "shouldDoFullSync" -> shouldDoFullSync = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ActionUploadResponse(
                    shouldDoFullSync = shouldDoFullSync!!
            )
        }
    }
}