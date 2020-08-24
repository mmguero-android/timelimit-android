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

package io.timelimit.android.logic.blockingreason

import io.timelimit.android.BuildConfig
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.logic.BlockingLevel

sealed class AppBaseHandling {
    object Idle: AppBaseHandling()
    object PauseLogic: AppBaseHandling()
    object Whitelist: AppBaseHandling()
    object TemporarilyAllowed: AppBaseHandling()
    object BlockDueToNoCategory: AppBaseHandling()
    data class UseCategories(
            val categoryIds: Set<String>,
            val shouldCount: Boolean,
            val level: BlockingLevel,
            val needsNetworkId: Boolean
    ): AppBaseHandling() {
        init {
            if (categoryIds.isEmpty()) {
                throw IllegalStateException()
            }
        }
    }

    companion object {
        fun calculate(
                foregroundAppPackageName: String?,
                foregroundAppActivityName: String?,
                pauseForegroundAppBackgroundLoop: Boolean,
                pauseCounting: Boolean,
                userRelatedData: UserRelatedData,
                deviceRelatedData: DeviceRelatedData
        ): AppBaseHandling {
            if (pauseForegroundAppBackgroundLoop) {
                return PauseLogic
            } else if (
                    (foregroundAppPackageName == BuildConfig.APPLICATION_ID) ||
                    (foregroundAppPackageName != null && AndroidIntegrationApps.ignoredApps[foregroundAppPackageName].let {
                        when (it) {
                            null -> false
                            AndroidIntegrationApps.IgnoredAppHandling.Ignore -> true
                            AndroidIntegrationApps.IgnoredAppHandling.IgnoreOnStoreOtherwiseWhitelistAndDontDisable -> BuildConfig.storeCompilant
                        }
                    }) ||
                    (foregroundAppPackageName != null && foregroundAppActivityName != null &&
                            AndroidIntegrationApps.shouldIgnoreActivity(foregroundAppPackageName, foregroundAppActivityName))
            ) {
                return Whitelist
            } else if (foregroundAppPackageName != null && deviceRelatedData.temporarilyAllowedApps.contains(foregroundAppPackageName)) {
                return TemporarilyAllowed
            } else if (foregroundAppPackageName != null) {
                val appCategory = run {
                    val tryActivityLevelBlocking = deviceRelatedData.deviceEntry.enableActivityLevelBlocking && foregroundAppActivityName != null
                    val appLevelCategory = userRelatedData.findCategoryApp(foregroundAppPackageName)

                    (if (tryActivityLevelBlocking) {
                        userRelatedData.findCategoryApp("$foregroundAppPackageName:$foregroundAppActivityName")
                    } else {
                        null
                    }) ?: appLevelCategory
                }

                val startCategory = userRelatedData.categoryById[appCategory?.categoryId]
                        ?: userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]

                if (startCategory == null) {
                    return BlockDueToNoCategory
                } else {
                    val categoryIds = userRelatedData.getCategoryWithParentCategories(startCategoryId = startCategory.category.id)

                    return UseCategories(
                            categoryIds = categoryIds,
                            shouldCount = !pauseCounting,
                            level = when (appCategory?.specifiesActivity) {
                                null -> BlockingLevel.Activity // occurs when using a default category
                                true -> BlockingLevel.Activity
                                false -> BlockingLevel.App
                            },
                            needsNetworkId = categoryIds.find { categoryId ->
                                userRelatedData.categoryById[categoryId]!!.networks.isNotEmpty()
                            } != null
                    )
                }
            } else {
                return Idle
            }
        }

        fun getCategoriesForCounting(items: List<AppBaseHandling>): Set<String> {
            val result = mutableSetOf<String>()

            items.forEach { item ->
                if (item is UseCategories && item.shouldCount) {
                    result.addAll(item.categoryIds)
                }
            }

            return result
        }
    }
}

fun AppBaseHandling.needsNetworkId(): Boolean = if (this is AppBaseHandling.UseCategories) this.needsNetworkId else false