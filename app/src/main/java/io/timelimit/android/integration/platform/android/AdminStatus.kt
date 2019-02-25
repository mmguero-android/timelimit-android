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
package io.timelimit.android.integration.platform.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import io.timelimit.android.BuildConfig
import io.timelimit.android.integration.platform.ProtectionLevel

object AdminStatus {
    fun getAdminStatus(context: Context, policyManager: DevicePolicyManager): ProtectionLevel {
        val component = ComponentName(context, AdminReceiver::class.java)

        return if (BuildConfig.storeCompilant) {
            if (policyManager.isAdminActive(component)) {
                ProtectionLevel.SimpleDeviceAdmin
            } else {
                ProtectionLevel.None
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (policyManager.isDeviceOwnerApp(context.packageName)) {
                    ProtectionLevel.DeviceOwner
                } else if (policyManager.isAdminActive(component)) {
                    ProtectionLevel.SimpleDeviceAdmin
                } else {
                    ProtectionLevel.None
                }
            } else /* if below Lollipop */ {
                if (policyManager.isAdminActive(component)) {
                    ProtectionLevel.PasswordDeviceAdmin
                } else {
                    ProtectionLevel.None
                }
            }
        }
    }
}
