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

import io.timelimit.android.data.model.Device
import io.timelimit.android.livedata.*

class CurrentDeviceLogic(private val appLogic: AppLogic) {
    private val disabledPrimaryDeviceCheck = appLogic.deviceUserEntry.switchMap { userEntry ->
        if (userEntry?.relaxPrimaryDevice == true) {
            appLogic.fullVersion.shouldProvideFullVersionFunctions
        } else {
            liveDataFromValue(false)
        }
    }

    private val userDeviceEntries = appLogic.deviceUserId.switchMap { deviceUserId ->
        if (deviceUserId == "") {
            liveDataFromValue(emptyList())
        } else {
            appLogic.database.device().getDevicesByUserId(deviceUserId)
        }
    }

    private val otherUserDeviceEntries = appLogic.deviceEntry.switchMap { ownDeviceEntry ->
        userDeviceEntries.map { devices ->
            devices.filterNot { device -> device.id == ownDeviceEntry?.id }
        }
    }

    private val isThisDeviceMarkedAsCurrentDevice = appLogic.deviceEntry
            .map { it?.id }
            .switchMap { ownDeviceId ->
                appLogic.deviceUserEntry.map { userEntry ->
                    userEntry?.currentDevice == ownDeviceId
                }
            }

    val isThisDeviceTheCurrentDevice = appLogic.fullVersion.isLocalMode
            .or(isThisDeviceMarkedAsCurrentDevice)
            .or(disabledPrimaryDeviceCheck)
            .ignoreUnchanged()

    val otherAssignedDevice = appLogic.deviceUserEntry.switchMap { userEntry ->
        if (userEntry?.currentDevice == null) {
            liveDataFromValue(null as Device?)
        } else {
            otherUserDeviceEntries.map { otherDeviceEntries ->
                otherDeviceEntries.find { it.id == userEntry.currentDevice }
            }
        }
    }
}
