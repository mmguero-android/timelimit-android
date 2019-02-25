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
package io.timelimit.android.ui.setup.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.*
import io.timelimit.android.sync.network.ParentPassword
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.user.create.DefaultCategories

class SetupDeviceModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val statusInternal = MutableLiveData<SetupDeviceModelStatus>().apply { value = SetupDeviceModelStatus.Ready }

    val status = statusInternal.castDown()

    fun doSetup(
            userId: String,
            username: String,
            password: String,
            allowedAppsCategory: String,
            appsToNotWhitelist: Set<String>,
            model: ActivityViewModel,
            networkTime: NetworkTime
    ) {
        if (statusInternal.value != SetupDeviceModelStatus.Ready) {
            return
        }

        statusInternal.value = SetupDeviceModelStatus.Working

        runAsync {
            val actions = mutableListOf<ParentAction>()
            var realUserId = userId
            var realAllowedAppsCategory = allowedAppsCategory
            val defaultCategories = DefaultCategories.with(getApplication())

            val isUserAnChild = when (userId) {
                SetupDeviceFragment.NEW_PARENT -> {
                    // generate user id
                    realUserId = IdGenerator.generateId()

                    // create parent
                    actions.add(AddUserAction(
                            userId = realUserId,
                            name = username,
                            timeZone = logic.timeApi.getSystemTimeZone().id,
                            userType = UserType.Parent,
                            password = ParentPassword.createCoroutine(password)
                    ))

                    false
                }
                SetupDeviceFragment.NEW_CHILD -> {
                    // generate user id
                    realUserId = IdGenerator.generateId()

                    // create child
                    actions.add(AddUserAction(
                            userId = realUserId,
                            name = username,
                            timeZone = logic.timeApi.getSystemTimeZone().id,
                            userType = UserType.Child,
                            password = if (password.isEmpty()) null else ParentPassword.createCoroutine(password)
                    ))

                    // create default categories
                    realAllowedAppsCategory = IdGenerator.generateId()
                    val allowedGamesCategory = IdGenerator.generateId()

                    actions.add(CreateCategoryAction(
                            childId = realUserId,
                            categoryId = realAllowedAppsCategory,
                            title = defaultCategories.allowedAppsTitle
                    ))

                    actions.add(CreateCategoryAction(
                            childId = realUserId,
                            categoryId = allowedGamesCategory,
                            title = defaultCategories.allowedGamesTitle
                    ))

                    actions.add(UpdateCategoryBlockedTimesAction(
                            categoryId = allowedGamesCategory,
                            blockedTimes = defaultCategories.allowedGamesBlockedTimes
                    ))

                    defaultCategories.generateGamesTimeLimitRules(allowedGamesCategory).forEach { rule ->
                        actions.add(CreateTimeLimitRuleAction(rule))
                    }

                    true
                }
                else -> {
                    logic.database.user().getUserByIdLive(userId).waitForNullableValue()!!.type == UserType.Child
                }
            }

            if (isUserAnChild) {
                if (realAllowedAppsCategory == "") {
                    // create allowed apps category if none was specified and overwrite its id
                    realAllowedAppsCategory = IdGenerator.generateId()

                    actions.add(CreateCategoryAction(
                            childId = realUserId,
                            categoryId = realAllowedAppsCategory,
                            title = defaultCategories.allowedAppsTitle
                    ))
                }

                // add allowed apps
                val allowedAppsPackages = logic.platformIntegration.getLocalApps(IdGenerator.generateId())
                        .filter { app -> app.recommendation == AppRecommendation.Whitelist }
                        .map { app -> app.packageName }
                        .toMutableSet().apply {
                            removeAll(appsToNotWhitelist)
                        }.toList()

                if (allowedAppsPackages.isNotEmpty()) {
                    actions.add(AddCategoryAppsAction(
                            categoryId = realAllowedAppsCategory,
                            packageNames = allowedAppsPackages
                    ))
                }
            }

            // apply the network time mode
            val deviceId = logic.deviceId.waitForNullableValue()!!

            actions.add(UpdateNetworkTimeVerificationAction(
                    deviceId = deviceId,
                    mode = networkTime
            ))

            // assign user to this device
            actions.add(SetDeviceUserAction(
                    deviceId = deviceId,
                    userId = realUserId
            ))

            if (model.tryDispatchParentActions(actions)) {
                statusInternal.value = SetupDeviceModelStatus.Done
            } else {
                statusInternal.value = SetupDeviceModelStatus.Ready
            }
        }
    }
}

enum class SetupDeviceModelStatus {
    Ready,
    Working,
    Done
}
