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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.AppRecommendation

object AndroidIntegrationApps {
    private val mainIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

    private val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_HOME)

    val ignoredApps = HashSet<String>()
    init {
        ignoredApps.add("com.android.systemui")
        ignoredApps.add("android")
        ignoredApps.add("com.android.packageinstaller")
        ignoredApps.add("com.google.android.packageinstaller")
        ignoredApps.add("com.android.bluetooth")
        ignoredApps.add("com.android.nfc")
    }

    fun getLocalApps(deviceId: String, context: Context): Collection<App> {
        val packageManager = context.packageManager

        val result = HashMap<String, App>()

        // WHITELIST
        // add launcher
        add(map = result, resolveInfoList = packageManager.queryIntentActivities(launcherIntent, 0), deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        // add settings
        add(map = result, packageName = Intent(Settings.ACTION_SETTINGS).resolveActivity(packageManager)?.packageName, deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        // add dialer
        add(map = result, packageName = Intent(Intent.ACTION_DIAL).resolveActivity(packageManager)?.packageName, deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        // add SMS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val smsApp: String? = Telephony.Sms.getDefaultSmsPackage(context)

            if (smsApp != null) {
                add(map = result, packageName = smsApp, deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
            }
        } else {
            add(map = result, packageName = Intent(android.content.Intent.ACTION_VIEW).setType("vnd.android-dir/mms-sms").resolveActivity(packageManager)?.packageName, deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        }
        // add contacts
        add(map = result, packageName = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI).resolveActivity(packageManager)?.packageName, deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        // add google play
        if (BuildConfig.storeCompilant) {
            add(map = result, packageName = "com.android.vending", deviceId = deviceId, recommendation = AppRecommendation.Whitelist, context = context)
        }
        // add all apps with launcher icon
        add(map = result, resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0), deviceId = deviceId, recommendation = AppRecommendation.None, context = context)

        val installedPackages = packageManager.getInstalledApplications(0)

        for (applicationInfo in installedPackages) {
            val packageName = applicationInfo.packageName

            if (!result.containsKey(packageName) && !ignoredApps.contains(packageName)) {
                result[packageName] = App(
                        deviceId = deviceId,
                        packageName = packageName,
                        title = applicationInfo.loadLabel(packageManager).toString(),
                        isLaunchable = false,
                        recommendation = AppRecommendation.None
                )
            }
        }

        return result.values
    }

    fun getLocalAppActivities(deviceId: String, context: Context): Collection<AppActivity> {
        return context.packageManager.getInstalledApplications(0).asSequence().map { applicationInfo ->
            (context.packageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_ACTIVITIES)?.activities ?: emptyArray())
                    .filter { it.exported }
                    .map {
                        AppActivity(
                                deviceId = deviceId,
                                appPackageName = applicationInfo.packageName,
                                activityClassName = it.name,
                                title = it.loadLabel(context.packageManager).toString()
                        )
                    }
        }.flatten().toSet()
    }

    private fun add(map: MutableMap<String, App>, resolveInfoList: List<ResolveInfo>, deviceId: String, recommendation: AppRecommendation, context: Context) {
        val packageManager = context.packageManager

        for (info in resolveInfoList) {
            val packageName = info.activityInfo.applicationInfo.packageName
            if (ignoredApps.contains(packageName)) {
                continue
            }

            if (!map.containsKey(packageName)) {
                map[packageName] = App(
                        deviceId = deviceId,
                        packageName = packageName,
                        title = info.activityInfo.applicationInfo.loadLabel(packageManager).toString(),
                        isLaunchable = true,
                        recommendation = recommendation
                )
            }
        }
    }

    private fun add(map: MutableMap<String, App>, packageName: String?, deviceId: String, recommendation: AppRecommendation, context: Context) {
        val packageManager = context.packageManager

        if (packageName == null) {
            return
        }

        if (ignoredApps.contains(packageName)) {
            return
        }

        if (map.containsKey(packageName)) {
            return
        }

        try {
            val packageInfo = context.packageManager.getApplicationInfo(packageName, 0)

            map[packageName] = App(
                    deviceId = deviceId,
                    packageName = packageName,
                    title = packageInfo.loadLabel(packageManager).toString(),
                    isLaunchable = true,
                    recommendation = recommendation
            )
        } catch (ex: PackageManager.NameNotFoundException) {
            // ignore
        }
    }

    fun getAppTitle(packageName: String, context: Context): String? {
        try {
            return context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
        } catch (ex: PackageManager.NameNotFoundException) {
            return null
        }
    }

    fun getAppIcon(packageName: String, context: Context): Drawable? {
        try {
            return context.packageManager.getApplicationIcon(packageName)
        } catch (ex: PackageManager.NameNotFoundException) {
            return null
        }
    }
}
