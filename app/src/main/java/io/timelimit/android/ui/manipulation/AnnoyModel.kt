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
package io.timelimit.android.ui.manipulation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import kotlinx.coroutines.delay

class AnnoyModel: ViewModel() {
    private val countdownInternal = MutableLiveData<Long>()
    private var hadInit = false

    val countdown = countdownInternal.castDown()

    fun init(duration: Long) {
        if (!hadInit) {
            hadInit = true

            countdownInternal.value = duration

            runAsync {
                var timer = duration

                while (timer >= 0) {
                    delay(1000)
                    timer--

                    countdownInternal.value = timer
                }
            }
        }
    }
}