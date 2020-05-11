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
package io.timelimit.android.extensions

import android.util.JsonReader
import java.io.StringReader

fun <T> JsonReader.parseList(parseItem: (JsonReader) -> T): List<T> {
    val result = mutableListOf<T>()

    this.beginArray()
    while (this.hasNext()) {
        result.add(parseItem(this))
    }
    this.endArray()

    return result
}

fun String.toJsonReader(): JsonReader = JsonReader(StringReader(this))