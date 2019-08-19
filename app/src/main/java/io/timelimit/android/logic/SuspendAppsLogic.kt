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

import androidx.lifecycle.LiveData
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import java.util.*

class SuspendAppsLogic(private val appLogic: AppLogic) {
    private val blockingAtActivityLevel = appLogic.deviceEntry.map { it?.enableActivityLevelBlocking ?: false }
    private val blockingReasonUtil = CategoriesBlockingReasonUtil(appLogic)

    private val knownInstalledApps = appLogic.deviceId.switchMap { deviceId ->
        if (deviceId.isNullOrEmpty()) {
            liveDataFromValue(emptyList())
        } else {
            appLogic.database.app().getAppsByDeviceIdAsync(deviceId).map { apps ->
                apps.map { it.packageName }
            }
        }
    }.ignoreUnchanged()

    private val categoryData = appLogic.deviceUserEntry.switchMap { deviceUser ->
        if (deviceUser?.type == UserType.Child) {
            appLogic.database.category().getCategoriesByChildId(deviceUser.id).switchMap { categories ->
                appLogic.database.categoryApp().getCategoryApps(categories.map { it.id }).map { categoryApps ->
                    RealCategoryData(
                            categoryForUnassignedApps = deviceUser.categoryForNotAssignedApps,
                            categories = categories,
                            categoryApps = categoryApps
                    ) as CategoryData
                }
            }
        } else {
            liveDataFromValue(NoChildUser as CategoryData)
        }
    }.ignoreUnchanged()

    private val categoryBlockingReasons: LiveData<Map<String, BlockingReason>?> = appLogic.deviceUserEntry.switchMap { deviceUser ->
        if (deviceUser?.type == UserType.Child) {
            appLogic.database.category().getCategoriesByChildId(deviceUser.id).switchMap { categories ->
                blockingReasonUtil.getCategoryBlockingReasons(
                        childDisableLimitsUntil = liveDataFromValue(deviceUser.disableLimitsUntil),
                        timeZone = liveDataFromValue(TimeZone.getTimeZone(deviceUser.timeZone)),
                        categories = categories
                ) as LiveData<Map<String, BlockingReason>?>
            }
        } else {
            liveDataFromValue(null as Map<String, BlockingReason>?)
        }
    }.ignoreUnchanged()

    private val appsToBlock = categoryBlockingReasons.switchMap { blockingReasons ->
        if (blockingReasons == null) {
            liveDataFromValue(emptyList<String>())
        } else {
            categoryData.switchMap { categories ->
                when (categories) {
                    is NoChildUser -> liveDataFromValue(emptyList<String>())
                    is RealCategoryData -> {
                        knownInstalledApps.switchMap { installedApps ->
                            blockingAtActivityLevel.map { blockingAtActivityLevel ->
                                val prepared = getAppsWithCategories(installedApps, categories, blockingAtActivityLevel)
                                val result = mutableListOf<String>()

                                installedApps.forEach { packageName ->
                                    val appCategories = prepared[packageName] ?: emptySet()

                                    if (appCategories.find { categoryId -> (blockingReasons[categoryId] ?: BlockingReason.None) == BlockingReason.None } == null) {
                                        result.add(packageName)
                                    }
                                }

                                result
                            }
                        }
                    }
                } as LiveData<List<String>>
            }
        }
    }

    private val realAppsToBlock = appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.SYSTEM_LEVEL_BLOCKING).switchMap { systemLevelBlocking ->
        if (systemLevelBlocking) {
            appsToBlock
        } else {
            liveDataFromValue(emptyList())
        }
    }

    private fun getAppsWithCategories(packageNames: List<String>, data: RealCategoryData, blockingAtActivityLevel: Boolean): Map<String, Set<String>> {
        val categoryForUnassignedApps = if (data.categories.find { it.id == data.categoryForUnassignedApps } != null) data.categoryForUnassignedApps else null

        if (blockingAtActivityLevel) {
            val categoriesByPackageName = data.categoryApps.groupBy { it.packageNameWithoutActivityName }

            val result = mutableMapOf<String, Set<String>>()

            packageNames.forEach { packageName ->
                val categoriesItems = categoriesByPackageName[packageName]
                val categories = (categoriesItems?.map { it.categoryId }?.toSet() ?: emptySet()).toMutableSet()
                val isMainAppIncluded = categoriesItems?.find { !it.specifiesActivity } != null

                if (!isMainAppIncluded) {
                    if (categoryForUnassignedApps != null) {
                        categories.add(categoryForUnassignedApps)
                    }
                }

                result[packageName] = categories
            }

            return result
        } else {
            val categoryByPackageName = data.categoryApps.associateBy { it.packageName }

            val result = mutableMapOf<String, Set<String>>()

            packageNames.forEach { packageName ->
                val category = categoryByPackageName[packageName]?.categoryId ?: categoryForUnassignedApps

                result[packageName] = if (category != null) setOf(category) else emptySet()
            }

            return result
        }
    }

    init {
        realAppsToBlock.observeForever { appsToBlock ->
            appLogic.platformIntegration.stopSuspendingForAllApps()
            appLogic.platformIntegration.setSuspendedApps(appsToBlock, true)
        }
    }
}

internal sealed class CategoryData
internal object NoChildUser: CategoryData()
internal class RealCategoryData(
        val categoryForUnassignedApps: String,
        val categories: List<Category>,
        val categoryApps: List<CategoryApp>
): CategoryData()