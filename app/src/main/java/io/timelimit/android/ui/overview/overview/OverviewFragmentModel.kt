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
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic

class OverviewFragmentModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

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

    val listEntries = introEntries.switchMap { introEntries ->
        deviceEntries.switchMap { deviceEntries ->
            userEntries.map { userEntries ->
                mutableListOf<OverviewFragmentItem>().apply {
                    addAll(introEntries)

                    add(OverviewFragmentHeaderDevices)
                    addAll(deviceEntries)
                    add(OverviewFragmentActionAddDevice)

                    add(OverviewFragmentHeaderUsers)
                    addAll(userEntries)
                    add(OverviewFragmentActionAddUser)
                }
            }
        }
    }
}
