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
package io.timelimit.android.ui.overview.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic

class OverviewFragmentModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

    private val itemVisibility = MutableLiveData<OverviewItemVisibility>().apply { value = OverviewItemVisibility.default }

    private val categoryEntries = logic.database.category().getAllCategoriesShortInfo()
    private val usersWithTemporarilyDisabledLimits = logic.database.user().getAllUsersLive().switchMap {
        users ->

        liveDataFromFunction { logic.realTimeLogic.getCurrentTimeInMillis() }.map {
            currentTime ->

            users.map {
                user ->

                user to (user.disableLimitsUntil >= currentTime)
            }
        }
    }.ignoreUnchanged()
    private val userEntries = usersWithTemporarilyDisabledLimits.switchMap { users ->
        categoryEntries.switchMap { categories ->
            liveDataFromFunction (5000) { logic.realTimeLogic.getCurrentTimeInMillis() }.map { now ->
                users.map { user ->
                    OverviewFragmentItemUser(
                            user = user.first,
                            limitsTemporarilyDisabled = user.second,
                            temporarilyBlocked = categories.find { category ->
                                category.childId == user.first.id &&
                                        category.temporarilyBlocked && (
                                        category.temporarilyBlockedEndTime == 0L ||
                                                category.temporarilyBlockedEndTime > now
                                        )
                            } != null
                    )
                }
            }
        }.ignoreUnchanged()
    }

    private val ownDeviceId = logic.deviceId
    private val devices = logic.database.device().getAllDevicesLive()
    private val devicesWithUsers = devices.switchMap { devices ->
        usersWithTemporarilyDisabledLimits.map { users ->
            devices.map { device ->
                device to users.find { it.first.id == device.currentUserId }
            }
        }
    }
    private val deviceEntries = ownDeviceId.switchMap { thisDeviceId ->
        devicesWithUsers.switchMap { devices ->
            logic.websocket.connectedDevices.map { connectedDevices ->
                devices.map { (device, user) ->
                    OverviewFragmentItemDevice(
                            device = device,
                            deviceUser = user?.first,
                            isCurrentDevice = device.id == thisDeviceId,
                            isConnected = connectedDevices.contains(device.id)
                    )
                }
            }
        }
    }

    private val isNoUserAssignedLive = logic.deviceUserEntry.map { it == null }.ignoreUnchanged()
    private val hasShownIntroduction = logic.database.config().wereHintsShown(HintsToShow.OVERVIEW_INTRODUCTION)
    private val messageLive = logic.database.config().getServerMessage()
    private val introEntries = isNoUserAssignedLive.switchMap { noUserAssigned ->
        hasShownIntroduction.switchMap { hasShownIntro ->
            messageLive.map { message ->
                val result = mutableListOf<OverviewFragmentItem>()

                if (noUserAssigned) {
                    result.add(OverviewFragmentHeaderFinishSetup)
                }

                if (message != null) {
                    result.add(OverviewFragmentItemMessage(message))
                }

                if (!hasShownIntro) {
                    result.add(OverviewFragmentHeaderIntro)
                }

                result
            }
        }
    }

    private val hiddenTaskIdsLive = MutableLiveData<Set<String>>().apply { value = emptySet() }
    private val tasksWithPendingReviewLive = logic.database.childTasks().getPendingTasks()
    private val pendingTasksToShowLive = hiddenTaskIdsLive.switchMap { hiddenTaskIds ->
        tasksWithPendingReviewLive.map { tasksWithPendingReview ->
            tasksWithPendingReview.filterNot { hiddenTaskIds.contains(it.childTask.taskId) }
        }
    }
    private val hasPremiumLive = logic.fullVersion.shouldProvideFullVersionFunctions
    private val pendingTaskItemLive = hasPremiumLive.switchMap { hasPremium ->
        pendingTasksToShowLive.map { tasks ->
            tasks.firstOrNull()?.let {
                TaskReviewOverviewItem(task = it.childTask, childTitle = it.childName, categoryTitle = it.categoryTitle, hasPremium = hasPremium)
            }
        }
    }

    fun hideTask(taskId: String) {
        hiddenTaskIdsLive.value = (hiddenTaskIdsLive.value ?: emptySet()) + setOf(taskId)
    }

    val listEntries = introEntries.switchMap { introEntries ->
        deviceEntries.switchMap { deviceEntries ->
            userEntries.switchMap { userEntries ->
                pendingTaskItemLive.switchMap { pendingTaskItem ->
                    itemVisibility.map { itemVisibility ->
                        mutableListOf<OverviewFragmentItem>().apply {
                            addAll(introEntries)

                            if (pendingTaskItem != null) add(pendingTaskItem)

                            add(OverviewFragmentHeaderDevices)
                            val shownDevices = when (itemVisibility.devices) {
                                DeviceListItemVisibility.BareMinimum -> deviceEntries.filter { it.isCurrentDevice || it.isImportant }
                                DeviceListItemVisibility.AllChildDevices -> deviceEntries.filter { it.isCurrentDevice || it.isImportant || it.deviceUser?.type == UserType.Child }
                                DeviceListItemVisibility.AllDevices -> deviceEntries
                            }
                            addAll(shownDevices)
                            if (shownDevices.size == deviceEntries.size) {
                                add(OverviewFragmentActionAddDevice)
                            } else {
                                add(ShowMoreOverviewFragmentItem.ShowMoreDevices(when (itemVisibility.devices) {
                                    DeviceListItemVisibility.BareMinimum -> if (deviceEntries.find { it.deviceUser?.type == UserType.Child } != null)
                                        DeviceListItemVisibility.AllChildDevices else DeviceListItemVisibility.AllDevices
                                    DeviceListItemVisibility.AllChildDevices -> DeviceListItemVisibility.AllDevices
                                    DeviceListItemVisibility.AllDevices -> DeviceListItemVisibility.AllDevices
                                }))
                            }

                            add(OverviewFragmentHeaderUsers)
                            if (itemVisibility.showParentUsers) {
                                addAll(userEntries)
                                add(OverviewFragmentActionAddUser)
                            } else {
                                userEntries.forEach { if (it.user.type != UserType.Parent) add(it) }
                                add(ShowMoreOverviewFragmentItem.ShowAllUsers)
                            }
                        }.toList()
                    }
                } as LiveData<List<OverviewFragmentItem>>
            }
        }
    }

    fun showAllUsers() {
        itemVisibility.value = itemVisibility.value!!.copy(showParentUsers = true)
    }

    fun showMoreDevices(level: DeviceListItemVisibility) {
        itemVisibility.value = itemVisibility.value!!.copy(devices = level)
    }
}
