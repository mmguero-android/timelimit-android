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

data class CreateAddDeviceTokenResponse (
        val token: String,
        val deviceId: String
) {
    companion object {
        private const val TOKEN = "token"
        private const val DEVICE_ID = "deviceId"

        fun parse(reader: JsonReader): CreateAddDeviceTokenResponse {
            var token: String? = null
            var deviceId: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    TOKEN -> token = reader.nextString()
                    DEVICE_ID -> deviceId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return CreateAddDeviceTokenResponse(
                    token = token!!,
                    deviceId = deviceId!!
            )
        }
    }
}