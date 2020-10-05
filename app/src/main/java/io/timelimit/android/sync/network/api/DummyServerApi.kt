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
package io.timelimit.android.sync.network.api

import io.timelimit.android.sync.network.*
import java.io.IOException

class DummyServerApi: ServerApi {
    var currentTime = 0L

    override suspend fun getTimeInMillis(): Long {
        if (currentTime == 0L) {
            throw IOException()
        } else {
            return currentTime
        }
    }

    override suspend fun sendMailLoginCode(mail: String, locale: String): String {
        throw IOException()
    }

    override suspend fun signInByMailCode(mailLoginToken: String, code: String): String {
        throw IOException()
    }

    override suspend fun getStatusByMailToken(mailAuthToken: String): StatusOfMailAddressResponse {
        throw IOException()
    }

    override suspend fun createFamilyByMailToken(mailToken: String, parentPassword: ParentPassword, parentDevice: NewDeviceInfo, timeZone: String, parentName: String, deviceName: String): AddDeviceResponse {
        throw IOException()
    }

    override suspend fun signInToFamilyByMailToken(mailToken: String, parentDevice: NewDeviceInfo, deviceName: String): AddDeviceResponse {
        throw IOException()
    }

    override suspend fun recoverPasswordByMailToken(mailToken: String, parentPassword: ParentPassword) {
        throw IOException()
    }

    override suspend fun registerChildDevice(registerToken: String, childDeviceInfo: NewDeviceInfo, deviceName: String): AddDeviceResponse {
        throw IOException()
    }

    override suspend fun pushChanges(request: ActionUploadRequest): ActionUploadResponse {
        throw IOException()
    }

    override suspend fun pullChanges(deviceAuthToken: String, status: ClientDataStatus): ServerDataStatus {
        throw IOException()
    }

    override suspend fun createAddDeviceToken(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String): CreateAddDeviceTokenResponse {
        throw IOException()
    }

    override suspend fun canDoPurchase(deviceAuthToken: String) = CanDoPurchaseStatus.NoForUnknownReason

    override suspend fun finishPurchaseByGooglePlay(receipt: String, signature: String, deviceAuthToken: String) {
        throw IOException()
    }

    override suspend fun linkParentMailAddress(mailAuthToken: String, deviceAuthToken: String, parentUserId: String, secondPasswordHash: String) {
        throw IOException()
    }

    override suspend fun updatePrimaryDevice(request: UpdatePrimaryDeviceRequest): UpdatePrimaryDeviceResponse {
        throw IOException()
    }

    override suspend fun requestSignOutAtPrimaryDevice(deviceAuthToken: String) {
        throw IOException()
    }

    override suspend fun reportDeviceRemoved(deviceAuthToken: String) {
        throw IOException()
    }

    override suspend fun removeDevice(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String, deviceId: String) {
        throw IOException()
    }

    override suspend fun isDeviceRemoved(deviceAuthToken: String): Boolean {
        throw IOException()
    }
}
