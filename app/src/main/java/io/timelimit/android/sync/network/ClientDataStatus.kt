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
package io.timelimit.android.sync.network

import android.util.JsonWriter
import io.timelimit.android.data.Database
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.waitForNonNullValue
import java.util.*
import kotlin.collections.HashMap

data class ClientDataStatus(
        val deviceListVersion: String,
        val installedAppsVersionsByDeviceId: Map<String, String>,
        val categories: Map<String, CategoryDataStatus>,
        val userListVersion: String
) {
    companion object {
        private const val DEVICES = "devices"
        private const val APPS = "apps"
        private const val CATEGORIES = "categories"
        private const val USERS = "users"

        val empty = ClientDataStatus(
                deviceListVersion = "",
                installedAppsVersionsByDeviceId = Collections.emptyMap(),
                categories = Collections.emptyMap(),
                userListVersion = ""
        )

        suspend fun getClientDataStatusAsync(database: Database): ClientDataStatus {
            return ClientDataStatus(
                    deviceListVersion = database.config().getDeviceListVersion().waitForNonNullValue(),
                    installedAppsVersionsByDeviceId = database.device().getInstalledAppsVersions().map {
                        val devicesWithAppVersions = it
                        val result = HashMap<String, String>()

                        devicesWithAppVersions.forEach { result[it.deviceId] = it.installedAppsVersions }

                        Collections.unmodifiableMap(result)
                    }.waitForNonNullValue(),
                    categories = database.category().getCategoriesWithVersionNumbers().map {
                        val categoriesWithVersions = it
                        val result = HashMap<String, CategoryDataStatus>()

                        categoriesWithVersions.forEach {
                            result[it.categoryId] = CategoryDataStatus(
                                baseVersion = it.baseVersion,
                                    assignedAppsVersion = it.assignedAppsVersion,
                                    timeLimitRulesVersion = it.timeLimitRulesVersion,
                                    usedTimeItemsVersion = it.usedTimeItemsVersion
                            )
                        }

                        Collections.unmodifiableMap(result)
                    }.waitForNonNullValue(),
                    userListVersion = database.config().getUserListVersion().waitForNonNullValue()
            )
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DEVICES).value(deviceListVersion)
        writer.name(USERS).value(userListVersion)

        writer.name(APPS)
        writer.beginObject()
        installedAppsVersionsByDeviceId.entries.forEach {
            writer.name(it.key).value(it.value)
        }
        writer.endObject()

        writer.name(CATEGORIES)
        writer.beginObject()
        categories.entries.forEach {
            writer.name(it.key)
            it.value.serialize(writer)
        }
        writer.endObject()

        writer.endObject()
    }
}

data class CategoryDataStatus(
        val baseVersion: String,
        val assignedAppsVersion: String,
        val timeLimitRulesVersion: String,
        val usedTimeItemsVersion: String
) {
    companion object {
        private const val BASE_VERSION = "base"
        private const val ASSIGNED_APPS_VERSION = "apps"
        private const val TIME_LIMIT_RULES_VERSION = "rules"
        private const val USED_TIME_ITEMS_VERSION = "usedTime"
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(BASE_VERSION).value(baseVersion)
        writer.name(ASSIGNED_APPS_VERSION).value(assignedAppsVersion)
        writer.name(TIME_LIMIT_RULES_VERSION).value(timeLimitRulesVersion)
        writer.name(USED_TIME_ITEMS_VERSION).value(usedTimeItemsVersion)

        writer.endObject()
    }
}