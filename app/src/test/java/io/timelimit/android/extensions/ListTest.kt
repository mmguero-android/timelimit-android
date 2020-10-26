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

import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ListTest (private val listSize: Int, private val maxLength: Int) {
    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun input(): List<Array<Int>> = mutableListOf<Array<Int>>().also { result ->
            (0..64).forEach { a ->
                (0..64).forEach { b ->
                    result.add(arrayOf(a, b))
                }
            }
        }
    }

    @Test
    fun testTakeDistributedElements() {
        val list = (0 until listSize).map { it }
        val shortenedList = list.takeDistributedElements(maxLength)

        assertEquals(list.size, listSize)
        assertEquals("listSize: $listSize; maxLength: $maxLength; resultLength: ${shortenedList.size}", listSize.coerceAtMost(maxLength), shortenedList.size)
    }
}
