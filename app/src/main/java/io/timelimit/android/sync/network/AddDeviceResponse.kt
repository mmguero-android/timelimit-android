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

data class AddDeviceResponse (
    val deviceAuthToken: String,
    val ownDeviceId: String
) {
    companion object {
        private const val DEVICE_AUTH_TOKEN = "deviceAuthToken"
        private const val OWN_DEVICE_ID = "ownDeviceId"

        fun parse(reader: JsonReader): AddDeviceResponse {
            var deviceAuthToken: String? = null
            var ownDeviceId: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_AUTH_TOKEN -> deviceAuthToken = reader.nextString()
                    OWN_DEVICE_ID -> ownDeviceId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AddDeviceResponse(
                    deviceAuthToken = deviceAuthToken!!,
                    ownDeviceId = ownDeviceId!!
            )
        }
    }
}