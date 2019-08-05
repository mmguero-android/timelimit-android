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

interface ServerApi {
    suspend fun getTimeInMillis(): Long
    suspend fun sendMailLoginCode(mail: String, locale: String): String
    suspend fun signInByMailCode(mailLoginToken: String, code: String): String
    suspend fun getStatusByMailToken(mailAuthToken: String): StatusOfMailAddressResponse
    suspend fun createFamilyByMailToken(
            mailToken: String,
            parentPassword: ParentPassword,
            parentDevice: NewDeviceInfo,
            timeZone: String,
            parentName: String,
            deviceName: String
    ): AddDeviceResponse
    suspend fun signInToFamilyByMailToken(
            mailToken: String,
            parentDevice: NewDeviceInfo,
            deviceName: String
    ): AddDeviceResponse
    suspend fun recoverPasswordByMailToken(
            mailToken: String,
            parentPassword: ParentPassword
    )
    suspend fun canRecoverPassword(
            mailToken: String,
            parentUserId: String
    ): Boolean
    suspend fun registerChildDevice(
            registerToken: String,
            childDeviceInfo: NewDeviceInfo,
            deviceName: String
    ): AddDeviceResponse
    suspend fun pushChanges(request: ActionUploadRequest): ActionUploadResponse
    suspend fun pullChanges(deviceAuthToken: String, status: ClientDataStatus): ServerDataStatus
    suspend fun createAddDeviceToken(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String): CreateAddDeviceTokenResponse
    suspend fun canDoPurchase(deviceAuthToken: String): CanDoPurchaseStatus
    suspend fun finishPurchaseByGooglePlay(receipt: String, signature: String, deviceAuthToken: String)
    suspend fun linkParentMailAddress(mailAuthToken: String, deviceAuthToken: String, parentUserId: String, secondPasswordHash: String)
    suspend fun updatePrimaryDevice(request: UpdatePrimaryDeviceRequest): UpdatePrimaryDeviceResponse
    suspend fun requestSignOutAtPrimaryDevice(deviceAuthToken: String)
    suspend fun reportDeviceRemoved(deviceAuthToken: String)
    suspend fun removeDevice(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String, deviceId: String)
    suspend fun isDeviceRemoved(deviceAuthToken: String): Boolean
}
