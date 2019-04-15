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
package io.timelimit.android.logic

import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.ForegroundAppSpec
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.livedata.waitForNullableValue

object AppAffectedByPrimaryDeviceUtil {
    suspend fun isCurrentAppAffectedByPrimaryDevice(
            logic: AppLogic
    ): Boolean {
        val user = logic.deviceUserEntry.waitForNullableValue()
                ?: throw NullPointerException("no user is signed in")

        if (user.type != UserType.Child) {
            throw IllegalStateException("no child is signed in")
        }

        if (user.relaxPrimaryDevice) {
            if (logic.fullVersion.shouldProvideFullVersionFunctions.waitForNonNullValue() == true) {
                return false
            }
        }

        val currentApp = ForegroundAppSpec.newInstance()

        try {
            logic.platformIntegration.getForegroundApp(currentApp)
        } catch (ex: SecurityException) {
            // ignore
        }

        if (currentApp.packageName == null) {
            return false
        }

        val categories = logic.database.category().getCategoriesByChildId(user.id).waitForNonNullValue()
        val categoryId = run {
            val categoryIdAtAppLevel = logic.database.categoryApp().getCategoryApp(
                    categoryIds = categories.map { it.id },
                    packageName = currentApp.packageName!!
            ).waitForNullableValue()?.categoryId

            if (logic.deviceEntry.waitForNullableValue()?.enableActivityLevelBlocking == true) {
                val categoryIdAtActivityLevel = logic.database.categoryApp().getCategoryApp(
                        categoryIds = categories.map { it.id },
                        packageName = "${currentApp.packageName}:${currentApp.activityName}"
                ).waitForNullableValue()?.categoryId

                categoryIdAtActivityLevel ?: categoryIdAtAppLevel
            } else {
                categoryIdAtAppLevel
            }
        } ?: user.categoryForNotAssignedApps

        val category = categories.find { it.id == categoryId }
        val parentCategory = categories.find { it.id == category?.parentCategoryId }

        if (category == null) {
            return false
        }

        // check blocked time areas
        if (
                (category.blockedMinutesInWeek.dataNotToModify.isEmpty == false) ||
                (parentCategory?.blockedMinutesInWeek?.dataNotToModify?.isEmpty == false)
        ) {
            return true
        }

        // check time limit rules
        val rules = logic.database.timeLimitRules().getTimeLimitRulesByCategories(
                categoryIds = listOf(categoryId) +
                        (if (parentCategory != null) listOf(parentCategory.id) else emptyList())
        ).waitForNonNullValue()

        if (rules.isNotEmpty()) {
            return true
        }

        return false
    }
}