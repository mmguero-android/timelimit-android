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
import androidx.room.*
import io.timelimit.android.data.model.ConfigurationItem
import io.timelimit.android.data.model.ConfigurationItemType
import io.timelimit.android.data.model.ConfigurationItemTypeConverter
import io.timelimit.android.data.model.ConfigurationItemTypeUtil
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map
import java.lang.IllegalArgumentException

@Dao
@TypeConverters(ConfigurationItemTypeConverter::class)
abstract class ConfigDao {
    @Query("SELECT * FROM config WHERE id IN (:keys)")
    protected abstract fun getConfigItemsSync(keys: List<Int>): List<ConfigurationItem>

    fun getConfigItemsSync() = getConfigItemsSync(ConfigurationItemTypeUtil.TYPES.map { ConfigurationItemTypeUtil.serialize(it) })

    @Query("SELECT * FROM config WHERE id = :key")
    protected abstract fun getRowByKeyAsync(key: ConfigurationItemType): LiveData<ConfigurationItem?>

    private fun getValueOfKeyAsync(key: ConfigurationItemType): LiveData<String?> {
        return Transformations.map(getRowByKeyAsync(key)) { it?.value }.ignoreUnchanged()
    }

    @Query("SELECT * FROM config WHERE id = :key")
    protected abstract fun getRowByKeySync(key: ConfigurationItemType): ConfigurationItem?

    private fun getValueOfKeySync(key: ConfigurationItemType): String? {
        return getRowByKeySync(key)?.value
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateValueOfKeySync(item: ConfigurationItem)

    @Query("DELETE FROM config WHERE id = :key")
    protected abstract fun removeConfigItemSync(key: ConfigurationItemType)

    private fun updateValueSync(key: ConfigurationItemType, value: String?) {
        if (value != null) {
            updateValueOfKeySync(ConfigurationItem(key, value))
        } else {
            removeConfigItemSync(key)
        }
    }

    fun getOwnDeviceId(): LiveData<String?> {
        return getValueOfKeyAsync(ConfigurationItemType.OwnDeviceId)
    }

    fun getOwnDeviceIdSync(): String? {
        return getValueOfKeySync(ConfigurationItemType.OwnDeviceId)
    }

    fun setOwnDeviceIdSync(deviceId: String) {
        updateValueSync(ConfigurationItemType.OwnDeviceId, deviceId)
    }

    fun getDeviceListVersion(): LiveData<String> {
        return getValueOfKeyAsync(ConfigurationItemType.DeviceListVersion).map {
            if (it == null) {
                ""
            } else {
                it
            }
        }
    }

    fun setDeviceListVersionSync(deviceListVersion: String) {
        updateValueSync(ConfigurationItemType.DeviceListVersion, deviceListVersion)
    }

    fun getUserListVersion(): LiveData<String> {
        return getValueOfKeyAsync(ConfigurationItemType.UserListVersion).map {
            if (it == null) {
                ""
            } else {
                it
            }
        }
    }

    fun setUserListVersionSync(userListVersion: String) {
        updateValueSync(ConfigurationItemType.UserListVersion, userListVersion)
    }

    private fun getNextSyncActionSequenceNumberSync(): Long {
        val row = getValueOfKeySync(ConfigurationItemType.NextSyncSequenceNumber)

        if (row == null) {
            return 0
        } else {
            return row.toLong()
        }
    }

    private fun setNextSyncActionSequenceNumberSync(nextSyncSequenceNumber: Long) {
        updateValueSync(ConfigurationItemType.NextSyncSequenceNumber, nextSyncSequenceNumber.toString())
    }

    fun getNextSyncActionSequenceActionAndIncrementIt(): Long {
        val current = getNextSyncActionSequenceNumberSync()
        setNextSyncActionSequenceNumberSync(current + 1)

        return current
    }

    fun getDeviceAuthTokenSync(): String {
        val value = getValueOfKeySync(ConfigurationItemType.DeviceAuthToken)

        if (value == null) {
            return ""
        } else {
            return value
        }
    }

    fun getDeviceAuthTokenAsync(): LiveData<String> = getValueOfKeyAsync(ConfigurationItemType.DeviceAuthToken).map {
        if (it == null) {
            ""
        } else {
            it
        }
    }

    fun setDeviceAuthTokenSync(token: String) {
        updateValueSync(ConfigurationItemType.DeviceAuthToken, token)
    }

    fun getFullVersionUntilAsync() = getValueOfKeyAsync(ConfigurationItemType.FullVersionUntil).map {
        if (it == null || it.isEmpty()) {
            0L
        } else {
            it.toLong()
        }
    }

    fun setFullVersionUntilSync(fullVersionUntil: Long) {
        updateValueSync(ConfigurationItemType.FullVersionUntil, fullVersionUntil.toString())
    }

    private fun getShownHintsLive(): LiveData<Long> {
        return getValueOfKeyAsync(ConfigurationItemType.ShownHints).map {
            if (it == null) {
                0
            } else {
                it.toLong(16)
            }
        }
    }

    private fun getShownHintsSync(): Long {
        val v = getValueOfKeySync(ConfigurationItemType.ShownHints)

        if (v == null) {
            return 0
        } else {
            return v.toLong(16)
        }
    }

    fun wereHintsShown(flags: Long) = getShownHintsLive().map {
        (it and flags) == flags
    }.ignoreUnchanged()

    fun wereAnyHintsShown() = getShownHintsLive().map { it != 0L }.ignoreUnchanged()

    fun setHintsShownSync(flags: Long) {
        updateValueSync(
                ConfigurationItemType.ShownHints,
                (getShownHintsSync() or flags).toString(16)
        )
    }

    fun resetShownHintsSync() {
        updateValueSync(ConfigurationItemType.ShownHints, null)
    }

    fun wasDeviceLockedSync() = getValueOfKeySync(ConfigurationItemType.WasDeviceLocked) == "true"
    fun setWasDeviceLockedSync(value: Boolean) = updateValueSync(ConfigurationItemType.WasDeviceLocked, if (value) "true" else "false")

    fun getLastAppVersionWhichSynced() = getValueOfKeySync(ConfigurationItemType.LastAppVersionWhichSynced)
    fun setLastAppVersionWhichSynced(version: String) = updateValueSync(ConfigurationItemType.LastAppVersionWhichSynced, version)

    fun setLastScreenOnTime(time: Long) = updateValueSync(ConfigurationItemType.LastScreenOnTime, time.toString())
    fun getLastScreenOnTime() = getValueOfKeySync(ConfigurationItemType.LastScreenOnTime)?.toLong() ?: 0L

    fun setServerMessage(message: String?) = updateValueSync(ConfigurationItemType.ServerMessage, message ?: "")
    fun getServerMessage() = getValueOfKeyAsync(ConfigurationItemType.ServerMessage).map { if (it.isNullOrBlank()) null else it }

    fun getCustomServerUrlSync() = getValueOfKeySync(ConfigurationItemType.CustomServerUrl) ?: ""
    fun getCustomServerUrlAsync() = getValueOfKeyAsync(ConfigurationItemType.CustomServerUrl).map { it ?: "" }
    fun setCustomServerUrlSync(url: String) = updateValueSync(ConfigurationItemType.CustomServerUrl, url)

    fun getForegroundAppQueryIntervalAsync(): LiveData<Long> = getValueOfKeyAsync(ConfigurationItemType.ForegroundAppQueryRange).map { (it ?: "0").toLong() }
    fun setForegroundAppQueryIntervalSync(interval: Long) {
        if (interval < 0) {
            throw IllegalArgumentException()
        }

        updateValueSync(ConfigurationItemType.ForegroundAppQueryRange, interval.toString())
    }
}
