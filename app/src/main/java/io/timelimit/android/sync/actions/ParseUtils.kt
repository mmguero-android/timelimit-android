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
package io.timelimit.android.sync.actions

import org.json.JSONArray
import java.io.IOException

object ParseUtils {
    fun readStringArray(array: JSONArray) = (0..(array.length() - 1)).map { array.getString(it) }
    fun readObjectArray(array: JSONArray) = (0..(array.length() - 1)).map { array.getJSONObject(it) }
    fun readArrayArray(array: JSONArray) = (0..(array.length() - 1)).map { array.getJSONArray(it) }
    fun readStringPair(array: JSONArray): Pair<String, String> {
        if (array.length() != 2) {
            throw IOException("invalid pair")
        }

        return array.getString(0) to array.getString(1)
    }
}

fun JSONArray.toJsonObjectArray() = ParseUtils.readObjectArray(this)
fun JSONArray.toJsonArrayArray() = ParseUtils.readArrayArray(this)