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
package io.timelimit.android.sync.network

import android.util.JsonReader

data class StatusOfMailAddressResponse(
        val mail: String,
        val status: StatusOfMailAddress,
        val canCreateFamily: Boolean
) {
    companion object {
        fun parse(reader: JsonReader): StatusOfMailAddressResponse {
            var mail: String? = null
            var status: StatusOfMailAddress? = null
            var canCreateFamily = true

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "status" -> status = when(reader.nextString()) {
                        "with family" -> StatusOfMailAddress.MailAddressWithFamily
                        "without family" -> StatusOfMailAddress.MailAddressWithoutFamily
                        else -> throw IllegalArgumentException()
                    }
                    "mail" -> mail = reader.nextString()
                    "canCreateFamily" -> canCreateFamily = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return StatusOfMailAddressResponse(
                    mail = mail!!,
                    status = status!!,
                    canCreateFamily = canCreateFamily
            )
        }
    }
}

enum class StatusOfMailAddress {
    MailAddressWithoutFamily,
    MailAddressWithFamily
}