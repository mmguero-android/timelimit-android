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
package io.timelimit.android.logic

import android.util.SparseArray
import androidx.lifecycle.LiveData
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.livedata.*
import java.util.*

class BackgroundTaskLogicCache (private val appLogic: AppLogic) {
    val deviceUserEntryLive = SingleItemLiveDataCache(appLogic.deviceUserEntry.ignoreUnchanged())
    val isThisDeviceTheCurrentDeviceLive = SingleItemLiveDataCache(appLogic.currentDeviceLogic.isThisDeviceTheCurrentDevice)
    val childCategories = object: MultiKeyLiveDataCache<List<Category>, String?>() {
        // key = child id
        override fun createValue(key: String?): LiveData<List<Category>> {
            if (key == null) {
                // this should rarely happen
                return liveDataFromValue(Collections.emptyList())
            } else {
                return appLogic.database.category().getCategoriesByChildId(key).ignoreUnchanged()
            }
        }
    }
    val appCategories = object: MultiKeyLiveDataCache<CategoryApp?, Pair<String, List<String>>>() {
        // key = package name, category ids
        override fun createValue(key: Pair<String, List<String>>): LiveData<CategoryApp?> {
            return appLogic.database.categoryApp().getCategoryApp(key.second, key.first)
        }
    }
    val timeLimitRules = object: MultiKeyLiveDataCache<List<TimeLimitRule>, String>() {
        override fun createValue(key: String): LiveData<List<TimeLimitRule>> {
            return appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(key)
        }
    }
    val usedTimesOfCategoryAndWeekByFirstDayOfWeek = object: MultiKeyLiveDataCache<SparseArray<UsedTimeItem>, Pair<String, Int>>() {
        override fun createValue(key: Pair<String, Int>): LiveData<SparseArray<UsedTimeItem>> {
            return appLogic.database.usedTimes().getUsedTimesOfWeek(key.first, key.second)
        }
    }
    val usedTimesOfCategoryAndDayOfEpoch = object: MultiKeyLiveDataCache<UsedTimeItem?, Pair<String, Int>>() {
        override fun createValue(key: Pair<String, Int>): LiveData<UsedTimeItem?> {
            return appLogic.database.usedTimes().getUsedTimesByCategoryIdAndDayOfEpoch(key.first, key.second)
        }
    }
    val shouldDoAutomaticSignOut = SingleItemLiveDataCacheWithRequery { -> appLogic.defaultUserLogic.hasAutomaticSignOut()}

    val liveDataCaches = LiveDataCaches(arrayOf(
            deviceUserEntryLive,
            isThisDeviceTheCurrentDeviceLive,
            childCategories,
            appCategories,
            timeLimitRules,
            usedTimesOfCategoryAndWeekByFirstDayOfWeek,
            usedTimesOfCategoryAndDayOfEpoch,
            shouldDoAutomaticSignOut
    ))
}