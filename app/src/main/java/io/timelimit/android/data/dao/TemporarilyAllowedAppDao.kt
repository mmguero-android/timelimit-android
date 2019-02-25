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
import androidx.lifecycle.Transformations
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.TemporarilyAllowedApp

@Dao
abstract class TemporarilyAllowedAppDao {
    @Query("SELECT * FROM temporarily_allowed_app WHERE device_id = :deviceId")
    abstract fun getTemporarilyAllowedAppsInternal(deviceId: String): LiveData<List<TemporarilyAllowedApp>>

    fun getTemporarilyAllowedApps(deviceId: String): LiveData<List<String>> {
        return Transformations.map(getTemporarilyAllowedAppsInternal(deviceId)) { it.map { it.packageName } }
    }

    @Insert
    abstract fun addTemporarilyAllowedAppSync(app: TemporarilyAllowedApp)

    @Query("DELETE FROM temporarily_allowed_app WHERE device_id = :deviceId")
    abstract fun removeAllTemporarilyAllowedAppsSync(deviceId: String)
}
