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
package io.timelimit.android.util

import android.util.JsonReader

fun <T> parseJsonArray(parser: JsonReader, parseItem: () -> T): List<T> {
    val result = mutableListOf<T>()

    parser.beginArray()
    while (parser.hasNext()) {
        result.add(parseItem())
    }
    parser.endArray()

    return result.toList()
}

fun parseJsonStringArray(parser: JsonReader) = parseJsonArray(parser) { parser.nextString() }
