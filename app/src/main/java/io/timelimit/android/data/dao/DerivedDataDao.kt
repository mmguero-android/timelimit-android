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

package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import io.timelimit.android.data.Database
import io.timelimit.android.data.cache.multi.DataCacheHelperInterface
import io.timelimit.android.data.cache.multi.createCache
import io.timelimit.android.data.cache.multi.delayClosingItems
import io.timelimit.android.data.cache.single.*
import io.timelimit.android.data.model.derived.DeviceAndUserRelatedData
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData

class DerivedDataDao (private val database: Database) {
    private val userRelatedDataCache = object : DataCacheHelperInterface<String, UserRelatedData?, UserRelatedData?> {
        override fun openItemSync(key: String): UserRelatedData? {
            val user = database.user().getUserByIdSync(key) ?: return null

            return UserRelatedData.load(user, database)
        }

        override fun updateItemSync(key: String, item: UserRelatedData?): UserRelatedData? {
            return if (item != null) {
                item.update(database)
            } else {
                openItemSync(key)
            }
        }

        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }

        override fun disposeItemFast(key: String, item: UserRelatedData?) = Unit
        override fun prepareForUser(item: UserRelatedData?): UserRelatedData? = item
        override fun close() = Unit
    }.createCache()

    private val deviceRelatedDataCache = object: SingleItemDataCacheHelperInterface<DeviceRelatedData?, DeviceRelatedData?> {
        override fun openItemSync(): DeviceRelatedData? = DeviceRelatedData.load(database)

        override fun updateItemSync(item: DeviceRelatedData?): DeviceRelatedData? = if (item != null) {
            item.update(database)
        } else {
            openItemSync()
        }

        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }

        override fun prepareForUser(item: DeviceRelatedData?): DeviceRelatedData? = item
        override fun disposeItemFast(item: DeviceRelatedData?): Unit = Unit
    }.createCache()

    private val usableUserRelatedData = userRelatedDataCache.userInterface.delayClosingItems(15 * 1000 /* 15 seconds */)
    private val usableDeviceRelatedData = deviceRelatedDataCache.userInterface.delayClosingItem(60 * 1000 /* 1 minute */)

    private val deviceAndUserRelatedDataCache = object: SingleItemDataCacheHelperInterface<DeviceAndUserRelatedData?, DeviceAndUserRelatedData?> {
        override fun openItemSync(): DeviceAndUserRelatedData?  {
            val deviceRelatedData = usableDeviceRelatedData.openSync(null) ?: return null
            val userRelatedData = if (deviceRelatedData.deviceEntry.currentUserId.isNotEmpty())
                    usableUserRelatedData.openSync(deviceRelatedData.deviceEntry.currentUserId, null)
            else
                null

            return DeviceAndUserRelatedData(
                    deviceRelatedData = deviceRelatedData,
                    userRelatedData = userRelatedData
            )
        }

        override fun updateItemSync(item: DeviceAndUserRelatedData?): DeviceAndUserRelatedData? {
            val deviceRelatedData = usableDeviceRelatedData.openSync(null) ?: run {
                // close old listener instances
                disposeItemFast(item)

                return null
            }
            val userRelatedData = if (deviceRelatedData.deviceEntry.currentUserId.isNotEmpty())
                usableUserRelatedData.openSync(deviceRelatedData.deviceEntry.currentUserId, null)
            else
                null

            // close old listener instances
            disposeItemFast(item)

            return if (deviceRelatedData == item?.deviceRelatedData && userRelatedData == item.userRelatedData) {
                item
            } else {
                DeviceAndUserRelatedData(
                        deviceRelatedData = deviceRelatedData,
                        userRelatedData = userRelatedData
                )
            }
        }

        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }

        override fun prepareForUser(item: DeviceAndUserRelatedData?): DeviceAndUserRelatedData? = item

        override fun disposeItemFast(item: DeviceAndUserRelatedData?) {
            if (item != null) {
                usableDeviceRelatedData.close(null)
                item.userRelatedData?.user?.let { usableUserRelatedData.close(it.id, null) }
            }
        }
    }.createCache()

    private val usableDeviceAndUserRelatedDataCache = deviceAndUserRelatedDataCache.userInterface.delayClosingItem(5000)
    private val deviceAndUserRelatedDataLive = usableDeviceAndUserRelatedDataCache.openLiveAtDatabaseThread()

    init {
        database.registerTransactionCommitListener {
            userRelatedDataCache.ownerInterface.updateSync()
            deviceRelatedDataCache.ownerInterface.updateSync()
            deviceAndUserRelatedDataCache.ownerInterface.updateSync()
        }
    }

    fun getUserAndDeviceRelatedDataSync(): DeviceAndUserRelatedData? {
        val result = usableDeviceAndUserRelatedDataCache.openSync(null)

        usableDeviceAndUserRelatedDataCache.close(null)

        return result
    }

    fun getUserAndDeviceRelatedDataLive(): LiveData<DeviceAndUserRelatedData?> = deviceAndUserRelatedDataLive
}