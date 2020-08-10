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
package io.timelimit.android.integration.platform.dummy

import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.integration.platform.*

class DummyIntegration(
        maximumProtectionLevel: ProtectionLevel
): PlatformIntegration(maximumProtectionLevel) {
    val localApps = ArrayList<App>(DummyApps.all)
    var protectionLevel = ProtectionLevel.None
    var foregroundAppPermission: RuntimePermissionStatus = RuntimePermissionStatus.NotRequired
    var drawOverOtherApps: RuntimePermissionStatus = RuntimePermissionStatus.NotRequired
    var notificationAccess: NewPermissionStatus = NewPermissionStatus.NotSupported
    var foregroundApp: String? = null
    var screenOn = false
    var lastAppStatusMessage: AppStatusMessage? = null
    var launchLockScreenForPackage: String? = null
    var showRevokeTemporarilyAllowedNotification = false
    val batteryStatus = MutableLiveData<BatteryStatus>().apply { value = BatteryStatus(true, 100) }

    override fun getLocalApps(deviceId: String): Collection<App> {
        return localApps.map{ it.copy(deviceId = deviceId) }
    }

    override fun getLocalAppPackageNames(): List<String> = localApps.map { it.packageName }

    override fun getLocalAppTitle(packageName: String): String? {
        return localApps.find { it.packageName == packageName }?.title
    }

    override fun getLocalAppActivities(deviceId: String): Collection<AppActivity> {
        return emptySet()
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return null
    }

    override fun getLauncherAppPackageName(): String? = null

    override fun getCurrentProtectionLevel(): ProtectionLevel {
        return protectionLevel
    }

    override fun getForegroundAppPermissionStatus(): RuntimePermissionStatus {
        return foregroundAppPermission
    }

    override fun getDrawOverOtherAppsPermissionStatus(): RuntimePermissionStatus {
        return drawOverOtherApps
    }

    override fun getNotificationAccessPermissionStatus(): NewPermissionStatus {
        return notificationAccess
    }

    override fun getOverlayPermissionStatus(): RuntimePermissionStatus {
        return RuntimePermissionStatus.NotRequired
    }

    override fun isAccessibilityServiceEnabled(): Boolean {
        return false
    }

    override fun trySetLockScreenPassword(password: String): Boolean {
        return false    // it failed
    }
    override fun showOverlayMessage(text: String) {
        // do nothing
    }

    override fun showAppLockScreen(currentPackageName: String, currentActivityName: String?) {
        launchLockScreenForPackage = currentPackageName
    }

    override fun showAnnoyScreen(annoyDuration: Long) {
        // ignore
    }

    override fun muteAudioIfPossible(packageName: String) {
        // ignore
    }

    override fun setShowBlockingOverlay(show: Boolean, blockedElement: String?) {
        // ignore
    }

    fun getAndResetShowAppLockScreen(): String? {
        try {
            return launchLockScreenForPackage
        } finally {
            launchLockScreenForPackage = null
        }
    }

    override suspend fun getForegroundApps(queryInterval: Long): Set<ForegroundApp> {
        if (foregroundAppPermission == RuntimePermissionStatus.NotGranted) {
            throw SecurityException()
        }

        return foregroundApp?.let { packageName ->
            setOf(
                    ForegroundApp(packageName, "invalid.activity")
            )
        } ?: emptySet()
    }

    override fun getMusicPlaybackPackage(): String? = null

    override fun setAppStatusMessage(message: AppStatusMessage?) {
        lastAppStatusMessage = message
    }

    fun getAppStatusMessage(): AppStatusMessage? {
        return lastAppStatusMessage
    }

    fun notifyLocalAppsChanged() {
        installedAppsChangeListener?.run()
    }

    override fun isScreenOn(): Boolean {
        return screenOn
    }

    override fun setShowNotificationToRevokeTemporarilyAllowedApps(show: Boolean) {
        showRevokeTemporarilyAllowedNotification = show
    }

    override fun showRemoteResetNotification() {
        // nothing to do
    }

    override fun showTimeWarningNotification(title: String, text: String) {
        // nothing to do
    }

    override fun disableDeviceAdmin() {
        // nothing to do
    }

    override fun setSuspendedApps(packageNames: List<String>, suspend: Boolean) = emptyList<String>()

    override fun stopSuspendingForAllApps() {
        // nothing to do
    }

    override fun setEnableSystemLockdown(enableLockdown: Boolean) = false

    override fun setLockTaskPackages(packageNames: List<String>) = false

    override fun getBatteryStatus(): BatteryStatus = batteryStatus.value!!
    override fun getBatteryStatusLive(): LiveData<BatteryStatus> = batteryStatus

    override fun setEnableCustomHomescreen(enable: Boolean) = Unit
    override fun setForceNetworkTime(enable: Boolean) = Unit

    override fun restartApp() = Unit
}
