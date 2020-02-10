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
package io.timelimit.android.integration.platform.android

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.integration.platform.*
import io.timelimit.android.integration.platform.android.foregroundapp.ForegroundAppHelper
import io.timelimit.android.ui.homescreen.HomescreenActivity
import io.timelimit.android.ui.lock.LockActivity
import io.timelimit.android.ui.manipulation.AnnoyActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay


class AndroidIntegration(context: Context): PlatformIntegration(maximumProtectionLevel) {
    companion object {
        private const val LOG_TAG = "AndroidIntegration"

        val maximumProtectionLevel: ProtectionLevel

        init {
            if (BuildConfig.storeCompilant) {
                maximumProtectionLevel = ProtectionLevel.SimpleDeviceAdmin
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    maximumProtectionLevel = ProtectionLevel.DeviceOwner
                } else {
                    maximumProtectionLevel = ProtectionLevel.PasswordDeviceAdmin
                }
            }
        }
    }

    private val context = context.applicationContext
    private val policyManager = this.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val foregroundAppHelper = ForegroundAppHelper.with(this.context)
    private val powerManager = this.context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = this.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val notificationManager = this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val deviceAdmin = ComponentName(context.applicationContext, AdminReceiver::class.java)
    private val overlay = OverlayUtil(context as Application)
    private val battery = BatteryStatusUtil(context)

    init {
        AppsChangeListener.registerBroadcastReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                installedAppsChangeListener?.run()
            }
        })
    }

    override fun getLocalApps(deviceId: String): Collection<App> {
        return AndroidIntegrationApps.getLocalApps(deviceId, context)
    }

    override fun getLocalAppPackageNames(): List<String> = context.packageManager.getInstalledApplications(0).map { it.packageName }

    override fun getLocalAppActivities(deviceId: String): Collection<AppActivity> {
        return AndroidIntegrationApps.getLocalAppActivities(deviceId, context)
    }

    override fun getLocalAppTitle(packageName: String): String? {
        return AndroidIntegrationApps.getAppTitle(packageName, context)
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return AndroidIntegrationApps.getAppIcon(packageName, context)
    }

    override fun getLauncherAppPackageName(): String? {
        return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .resolveActivity(context.packageManager)?.packageName
    }

    override fun getCurrentProtectionLevel(): ProtectionLevel {
        return AdminStatus.getAdminStatus(context, policyManager)
    }

    override suspend fun getForegroundApp(result: ForegroundAppSpec, queryInterval: Long) {
        foregroundAppHelper.getForegroundApp(result, queryInterval)
    }

    override fun getForegroundAppPermissionStatus(): RuntimePermissionStatus {
        return foregroundAppHelper.getPermissionStatus()
    }

    override fun getMusicPlaybackPackage(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getNotificationAccessPermissionStatus() == NewPermissionStatus.Granted) {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val sessions = manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))

                return sessions.find {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                            it.playbackState?.state == PlaybackState.STATE_FAST_FORWARDING ||
                            it.playbackState?.state == PlaybackState.STATE_REWINDING
                }?.packageName
            }
        }

        return null
    }

    override fun showOverlayMessage(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    override fun getDrawOverOtherAppsPermissionStatus(): RuntimePermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) {
                return RuntimePermissionStatus.Granted
            } else {
                return RuntimePermissionStatus.NotGranted
            }
        } else {
            return RuntimePermissionStatus.NotRequired
        }
    }

    override fun getNotificationAccessPermissionStatus(): NewPermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (activityManager.isLowRamDevice) {
                return NewPermissionStatus.NotSupported
            } else if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                return NewPermissionStatus.Granted
            } else {
                return NewPermissionStatus.NotGranted
            }
        } else {
            return NewPermissionStatus.NotSupported
        }
    }

    override fun getOverlayPermissionStatus(): RuntimePermissionStatus = overlay.getOverlayPermissionStatus()

    override fun isAccessibilityServiceEnabled(): Boolean {
        val service = context.packageName + "/" + AccessibilityService::class.java.canonicalName

        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (ex: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val enabledServicesString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

            if (!enabledServicesString.isNullOrEmpty()) {
                if (enabledServicesString.split(":").contains(service)) {
                    return true
                }
            }
        }

        return false
    }

    override fun trySetLockScreenPassword(password: String): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "set password")
        }

        if (!BuildConfig.storeCompilant) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try {
                    if (password.isBlank()) {
                        return policyManager.resetPassword("", 0)
                    } else if (policyManager.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)) {
                        policyManager.lockNow()

                        return true
                    }
                } catch (ex: SecurityException) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "error setting password", ex)
                    }
                }
            }
        }

        return false
    }

    private var lastAppStatusMessage: AppStatusMessage? = null
    private var appStatusMessageChannel = Channel<AppStatusMessage?>(capacity = Channel.CONFLATED)

    override fun setAppStatusMessage(message: AppStatusMessage?) {
        if (lastAppStatusMessage != message) {
            lastAppStatusMessage = message
            appStatusMessageChannel.offer(message)
        }
    }

    init {
        runAsyncExpectForever {
            appStatusMessageChannel.consumeEach { message ->
                BackgroundService.setStatusMessage(message, context)

                delay(200)
            }
        }
    }

    override fun showAppLockScreen(currentPackageName: String, currentActivityName: String?) {
        LockActivity.start(context, currentPackageName, currentActivityName)
    }

    override fun showAnnoyScreen(annoyDuration: Long) {
        AnnoyActivity.start(context, annoyDuration)
    }

    override fun muteAudioIfPossible(packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getNotificationAccessPermissionStatus() == NewPermissionStatus.Granted) {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val sessions = manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))
                val sessionsOfTheApp = sessions.filter { it.packageName == packageName }
                sessionsOfTheApp.forEach { session ->
                    session.dispatchMediaButtonEvent(KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_MEDIA_STOP
                    ))
                    session.dispatchMediaButtonEvent(KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_MEDIA_STOP
                    ))
                }
            }
        }
    }

    override fun setShowBlockingOverlay(show: Boolean) {
        if (show) {
            overlay.show()
        } else {
            overlay.hide()
        }
    }

    override fun isScreenOn(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return powerManager.isInteractive
        } else {
            return powerManager.isScreenOn
        }
    }

    override fun setShowNotificationToRevokeTemporarilyAllowedApps(show: Boolean) {
        if (show) {
            NotificationChannels.createNotificationChannels(notificationManager, context)

            val actionIntent = PendingIntent.getService(
                    context,
                    PendingIntentIds.REVOKE_TEMPORARILY_ALLOWED,
                    BackgroundActionService.prepareRevokeTemporarilyAllowed(context),
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, NotificationChannels.APP_STATUS)
                    .setSmallIcon(R.drawable.ic_stat_check)
                    .setContentTitle(context.getString(R.string.background_logic_temporarily_allowed_title))
                    .setContentText(context.getString(R.string.background_logic_temporarily_allowed_text))
                    .setContentIntent(actionIntent)
                    .setWhen(0)
                    .setShowWhen(false)
                    .setSound(null)
                    .setOnlyAlertOnce(true)
                    .setLocalOnly(true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

            notificationManager.notify(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS, notification)
        } else {
            notificationManager.cancel(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS)
        }
    }

    override fun showRemoteResetNotification() {
        NotificationChannels.createNotificationChannels(notificationManager, context)

        notificationManager.notify(
                NotificationIds.APP_RESET,
                NotificationCompat.Builder(context, NotificationChannels.APP_STATUS)
                        .setSmallIcon(R.drawable.ic_stat_timelapse)
                        .setContentTitle(context.getString(R.string.remote_reset_notification_title))
                        .setContentText(context.getString(R.string.remote_reset_notification_text))
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setSound(null)
                        .setOnlyAlertOnce(true)
                        .setLocalOnly(true)
                        .setAutoCancel(false)
                        .setOngoing(false)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
        )
    }

    override fun showTimeWarningNotification(title: String, text: String) {
        NotificationChannels.createNotificationChannels(notificationManager, context)

        notificationManager.notify(
                NotificationIds.TIME_WARNING,
                NotificationCompat.Builder(context, NotificationChannels.TIME_WARNING)
                        .setSmallIcon(R.drawable.ic_stat_timelapse)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setLocalOnly(true)
                        .setAutoCancel(false)
                        .setOngoing(false)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
        )
    }

    override fun disableDeviceAdmin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                setEnableSystemLockdown(false)
                policyManager.clearDeviceOwnerApp(context.packageName)
            }
        }

        policyManager.removeActiveAdmin(deviceAdmin)
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun setSuspendedApps(packageNames: List<String>, suspend: Boolean): List<String> {
        if (
                (getCurrentProtectionLevel() == ProtectionLevel.DeviceOwner) &&
                (!BuildConfig.storeCompilant) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        ) {
            val failedApps = policyManager.setPackagesSuspended(
                    deviceAdmin,
                    packageNames.toTypedArray(),
                    suspend
            )

            return packageNames.filterNot { failedApps.contains(it) }
        } else {
            return emptyList()
        }
    }

    override fun setEnableSystemLockdown(enableLockdown: Boolean): Boolean {
        return if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                policyManager.isDeviceOwnerApp(context.packageName) &&
                (!BuildConfig.storeCompilant)
        ) {
            if (enableLockdown) {
                // disable problematic features
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)
                }
            } else /* disable lockdown */ {
                // enable problematic features
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)
                }

                enableSystemApps()
                stopSuspendingForAllApps()
            }

            true
        } else {
            false
        }
    }

    private fun enableSystemApps() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        // disabled system apps (all apps - enabled apps)
        val allApps = context.packageManager.getInstalledApplications(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1)
                    PackageManager.GET_UNINSTALLED_PACKAGES
                else
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
        )
        val enabledAppsPackages = context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()

        allApps
                .asSequence()
                .filterNot { enabledAppsPackages.contains(it.packageName) }
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                .map { it.packageName }
                .forEach { policyManager.enableSystemApp(deviceAdmin, it) }
    }

    override fun stopSuspendingForAllApps() {
        setSuspendedApps(getLocalAppPackageNames(), false)
    }

    override fun setLockTaskPackages(packageNames: List<String>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                policyManager.setLockTaskPackages(deviceAdmin, packageNames.toTypedArray())

                true
            } else {
                false
            }
        } else {
            false
        }
    }

    override fun getBatteryStatus(): BatteryStatus = battery.status.value!!
    override fun getBatteryStatusLive(): LiveData<BatteryStatus> = battery.status

    override fun setEnableCustomHomescreen(enable: Boolean) {
        val homescreen = ComponentName(context, HomescreenActivity::class.java)

        context.packageManager.setComponentEnabledSetting(
                homescreen,
                if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        )

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (policyManager.isDeviceOwnerApp(context.packageName)) {
                    policyManager.addPersistentPreferredActivity(
                            deviceAdmin,
                            IntentFilter(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addCategory(Intent.CATEGORY_DEFAULT)
                            },
                            homescreen
                    )
                }
            }
        }
    }
}
