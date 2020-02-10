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
package io.timelimit.android.ui.manage.child.advanced.manageblocktemporarily

import androidx.lifecycle.LiveData
import io.timelimit.android.data.extensions.sorted
import io.timelimit.android.data.model.Category
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.RealTimeLogic

data class ManageBlockTemporarilyItem(
        val categoryId: String,
        val categoryTitle: String,
        val checked: Boolean,
        val endTime: Long
)

object ManageBlockTemporarilyItems {
    fun build(
            categories: LiveData<List<Category>>,
            realTimeLogic: RealTimeLogic
    ): LiveData<List<ManageBlockTemporarilyItem>> {
        val time = liveDataFromFunction { realTimeLogic.getCurrentTimeInMillis() }

        return categories.map { categories ->
            categories.sorted().map { category ->
                ManageBlockTemporarilyItem(
                        categoryId = category.id,
                        categoryTitle = category.title,
                        endTime = category.temporarilyBlockedEndTime,
                        checked = category.temporarilyBlocked
                )
            }
        }.switchMap { items ->
            val hasEndtimes = items.find { it.endTime != 0L } != null

            if (hasEndtimes) {
                time.map { time ->
                    items.map { item ->
                        if (item.endTime == 0L || item.endTime >= time) {
                            item
                        } else {
                            item.copy(checked = false)
                        }
                    }
                }
            } else {
                liveDataFromValue(items)
            }
        }.ignoreUnchanged()
    }
}