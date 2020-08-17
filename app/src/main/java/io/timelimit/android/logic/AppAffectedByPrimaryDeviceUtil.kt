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

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.logic.blockingreason.AppBaseHandling

object AppAffectedByPrimaryDeviceUtil {
    suspend fun isCurrentAppAffectedByPrimaryDevice(
            logic: AppLogic
    ): Boolean {
        val deviceAndUserRelatedData = Threads.database.executeAndWait {
            logic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
        }

        if (deviceAndUserRelatedData?.userRelatedData?.user?.type != UserType.Child) {
            return false
        }

        if (deviceAndUserRelatedData.userRelatedData.user.relaxPrimaryDevice) {
            if (deviceAndUserRelatedData.deviceRelatedData.isConnectedAndHasPremium) {
                return false
            }
        }

        val currentApps = try {
            logic.platformIntegration.getForegroundApps(
                    logic.getForegroundAppQueryInterval(),
                    logic.getEnableMultiAppDetection()
            )
        } catch (ex: SecurityException) {
            emptySet<ForegroundApp>()
        }

        return currentApps.find { currentApp ->
            val handling = AppBaseHandling.calculate(
                    foregroundAppPackageName = currentApp.packageName,
                    foregroundAppActivityName = currentApp.activityName,
                    deviceRelatedData = deviceAndUserRelatedData.deviceRelatedData,
                    userRelatedData = deviceAndUserRelatedData.userRelatedData,
                    pauseCounting = false,
                    pauseForegroundAppBackgroundLoop = false
            )

            if (!(handling is AppBaseHandling.UseCategories)) {
                return false
            }

            return handling.categoryIds.find { categoryId ->
                val category = deviceAndUserRelatedData.userRelatedData.categoryById[categoryId]!!

                val hasBlockedTimeAreas = !category.category.blockedMinutesInWeek.dataNotToModify.isEmpty
                val hasRules = category.rules.isNotEmpty()

                hasBlockedTimeAreas || hasRules
            } != null
        } != null
    }
}