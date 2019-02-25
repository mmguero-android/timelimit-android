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

data class UpdatePrimaryDeviceRequest(
        val action: UpdatePrimaryDeviceRequestType,
        val currentUserId: String,
        val deviceAuthToken: String
) {
    companion object {
        private const val ACTION = "action"
        private const val CURRENT_USER = "currentUserId"
        private const val AUTH_TOKEN = "authToken"
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ACTION).value(when(action) {
            UpdatePrimaryDeviceRequestType.SetThisDevice -> "set this device"
            UpdatePrimaryDeviceRequestType.UnsetThisDevice -> "unset this device"
        })
        writer.name(CURRENT_USER).value(currentUserId)
        writer.name(AUTH_TOKEN).value(deviceAuthToken)

        writer.endObject()
    }
}

enum class UpdatePrimaryDeviceRequestType {
    SetThisDevice,
    UnsetThisDevice
}

data class UpdatePrimaryDeviceResponse(
        val status: UpdatePrimaryDeviceResponseType
) {
    companion object {
        private const val STATUS = "status"

        fun parse(reader: JsonReader): UpdatePrimaryDeviceResponse {
            var status: UpdatePrimaryDeviceResponseType? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    STATUS -> status = UpdatePrimaryDeviceResponseTypeParser.parse(reader.nextString())
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return UpdatePrimaryDeviceResponse(
                    status = status!!
            )
        }
    }
}

enum class UpdatePrimaryDeviceResponseType {
    Success,
    AssignedToOtherDevice,
    RequiresFullVersion,
    UnknownError
}

object UpdatePrimaryDeviceResponseTypeParser {
    fun parse(value: String) = when (value) {
        "success" -> UpdatePrimaryDeviceResponseType.Success
        "assigned to other device" -> UpdatePrimaryDeviceResponseType.AssignedToOtherDevice
        "requires full version" -> UpdatePrimaryDeviceResponseType.RequiresFullVersion
        else -> UpdatePrimaryDeviceResponseType.UnknownError
    }
}
