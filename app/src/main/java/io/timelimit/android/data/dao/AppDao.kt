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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.AppRecommendationConverter

@Dao
@TypeConverters(
        AppRecommendationConverter::class
)
interface AppDao {
    @Query("DELETE FROM app WHERE device_id = :deviceId")
    fun deleteAllAppsByDeviceId(deviceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppsSync(apps: Collection<App>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppSync(app: App)

    @Query("DELETE FROM app WHERE device_id = :deviceId AND package_name IN (:packageNames)")
    fun removeAppsByDeviceIdAndPackageNamesSync(deviceId: String, packageNames: List<String>)

    @Query("SELECT * FROM app WHERE device_id IN (:deviceIds)")
    fun getAppsByDeviceIds(deviceIds: List<String>): LiveData<List<App>>

    @Query("SELECT * FROM app")
    fun getAllApps(): LiveData<List<App>>

    @Query("SELECT * FROM app WHERE device_id IN (:deviceIds) AND package_name = :packageName")
    fun getAppsByDeviceIdsAndPackageName(deviceIds: List<String>, packageName: String): LiveData<List<App>>

    @Query("SELECT * FROM app WHERE device_id = :deviceId")
    fun getAppsByDeviceIdAsync(deviceId: String): LiveData<List<App>>

    @Query("SELECT * FROM app LIMIT :pageSize OFFSET :offset")
    fun getAppPageSync(offset: Int, pageSize: Int): List<App>

    @Query("SELECT * FROM app WHERE recommendation = :recommendation")
    fun getAppsByRecommendationLive(recommendation: AppRecommendation): LiveData<List<App>>
}
