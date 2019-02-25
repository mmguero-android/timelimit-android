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

import android.util.JsonWriter
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.PasswordHashing
import org.json.JSONObject

data class ParentPassword (
        val parentPasswordHash: String,
        val parentPasswordSecondHash: String,
        val parentPasswordSecondSalt: String
) {
    companion object {
        private const val HASH = "hash"
        private const val SECOND_HASH = "secondHash"
        private const val SECOND_SALT = "secondSalt"

        fun createSync(password: String): ParentPassword {
            val secondSalt = PasswordHashing.generateSalt()

            return ParentPassword(
                    parentPasswordHash = PasswordHashing.hashSync(password),
                    parentPasswordSecondSalt = secondSalt,
                    parentPasswordSecondHash = PasswordHashing.hashSyncWithSalt(password, secondSalt)
            )
        }

        suspend fun createCoroutine(password: String) = Threads.crypto.executeAndWait {
            createSync(password)
        }

        fun parse(obj: JSONObject) = ParentPassword(
                parentPasswordHash = obj.getString(HASH),
                parentPasswordSecondHash = obj.getString(SECOND_HASH),
                parentPasswordSecondSalt = obj.getString(SECOND_SALT)
        )
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(HASH).value(parentPasswordHash)
        writer.name(SECOND_HASH).value(parentPasswordSecondHash)
        writer.name(SECOND_SALT).value(parentPasswordSecondSalt)

        writer.endObject()
    }
}