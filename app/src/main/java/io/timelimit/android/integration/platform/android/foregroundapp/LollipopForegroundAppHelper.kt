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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LollipopForegroundAppHelper(private val context: Context) : ForegroundAppHelper() {
    companion object {
        private const val LOG_TAG = "LollipopForegroundApp"

        private val foregroundAppThread: Executor by lazy { Executors.newSingleThreadExecutor() }
        val enableMultiAppDetectionGeneral = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        private val supportsCompleteEvents = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        private fun hash(event: UsageEvents.Event): Int {
            val a = event.eventType.hashCode()
            val b = event.packageName?.hashCode() ?: 522
            val c = event.className?.hashCode() ?: 754
            val d = event.timeStamp.hashCode()

            return (((31 * a) + b) * 31 + c) * 31 + d
        }
    }

    private val usageStatsManager = context.getSystemService(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) Context.USAGE_STATS_SERVICE else "usagestats") as UsageStatsManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val packageManager = context.packageManager

    private var lastQueryTime: Long = 0
    private val currentForegroundApps = mutableMapOf<ForegroundApp, Int>()
    private val expectedStopEvents = mutableSetOf<ForegroundApp>()
    private val seenEvents = SparseIntArray()
    private var currentForegroundAppsSnapshot: Set<ForegroundApp> = emptySet()
    private var lastHandledEventTime: Long = 0
    private val event = UsageEvents.Event()
    private var callsSinceLastActivityExistsCheck = 0
    private var lastEnableMultiAppDetection = false

    @Throws(SecurityException::class)
    override suspend fun getForegroundApps(queryInterval: Long, enableMultiAppDetection: Boolean): Set<ForegroundApp> {
        if (getPermissionStatus() == RuntimePermissionStatus.NotGranted) {
            throw SecurityException()
        }

        val effectiveEnableMultiAppDetection = enableMultiAppDetection && enableMultiAppDetectionGeneral

        foregroundAppThread.executeAndWait {
            val now = System.currentTimeMillis()
            var currentForegroundAppsModified = false

            if (lastQueryTime > now || queryInterval >= 1000 * 60 * 60 * 24 /* 1 day */ || lastEnableMultiAppDetection != effectiveEnableMultiAppDetection) {
                // if the time went backwards, forget everything
                lastQueryTime = 0
                lastHandledEventTime = 0
                currentForegroundApps.clear(); currentForegroundAppsModified = true
                seenEvents.clear()
                expectedStopEvents.clear()
                lastEnableMultiAppDetection = effectiveEnableMultiAppDetection
            }

            val queryStartTime = if (lastQueryTime == 0L) {
                // query data for last 7 days
                now - 1000 * 60 * 60 * 24 * 7
            } else {
                // query data since last query
                // note: when the duration is too small, Android returns no data
                //       due to that, 1 second more than required is queried
                //       which seems to provide all data
                // update: with 1 second, some App switching events were missed
                //         it seems to always work with 1.5 seconds
                lastQueryTime - Math.max(queryInterval, 1500)
            }

            usageStatsManager.queryEvents(queryStartTime, now)?.let { usageEvents ->
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)

                    if (event.timeStamp >= lastHandledEventTime) {
                        if (event.timeStamp > lastHandledEventTime) {
                            seenEvents.clear()
                            lastHandledEventTime = event.timeStamp
                        }

                        val hash = hash(event)

                        if (seenEvents.get(hash, 0) != 0) continue
                        seenEvents.put(hash, 1)

                        if (event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN || event.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "device reboot => reset")
                            }

                            currentForegroundApps.clear(); currentForegroundAppsModified = true
                            expectedStopEvents.clear()
                        } else if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "resume ${event.packageName}:${event.className}")
                            }

                            if (effectiveEnableMultiAppDetection) {
                                val app = ForegroundApp(event.packageName, event.className)

                                if (!doesActivityExist(app)) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... ignore because it can not run")
                                    }

                                    continue
                                }

                                if (supportsCompleteEvents) {
                                    val currentCounter = currentForegroundApps.getOrDefault(app, 0)

                                    currentForegroundApps[app] = currentCounter + 1; currentForegroundAppsModified = true
                                } else {
                                    currentForegroundApps[app] = 1; currentForegroundAppsModified = true
                                }
                            } else {
                                val currentForegroundApp = currentForegroundApps.keys.singleOrNull()
                                val matchingForegroundApp = currentForegroundApp != null && currentForegroundApp.packageName == event.packageName && currentForegroundApp.activityName == event.className

                                if (!matchingForegroundApp) {
                                    currentForegroundApps.clear(); currentForegroundApps.set(ForegroundApp(event.packageName, event.className), 1)

                                    currentForegroundAppsModified = true
                                }
                            }
                        } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            if (effectiveEnableMultiAppDetection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val app = ForegroundApp(event.packageName, event.className)

                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "pause ${event.packageName}:${event.className}")
                                }

                                val currentCounter = currentForegroundApps.getOrDefault(app, 0)

                                if (currentCounter == 0) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... was not running")
                                    }
                                } else if (currentCounter == 1) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... stopped last instance")
                                    }

                                    currentForegroundApps.remove(app); currentForegroundAppsModified = true
                                } else {
                                    currentForegroundApps[app] = currentCounter - 1; currentForegroundAppsModified = true
                                }

                                if (supportsCompleteEvents) {
                                    expectedStopEvents.add(app)
                                }
                            }
                        } else if (supportsCompleteEvents && event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                            val app = ForegroundApp(event.packageName, event.className)

                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "stop ${event.packageName}:${event.className}")
                            }

                            if (expectedStopEvents.remove(app)) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "... expected and ignored")
                                }
                            } else {
                                val currentCounter = currentForegroundApps.getOrDefault(app, 0)

                                if (currentCounter == 0) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... was not running")
                                    }
                                } else if (currentCounter == 1) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... stopped last instance")
                                    }

                                    currentForegroundApps.remove(app); currentForegroundAppsModified = true
                                } else {
                                    currentForegroundApps[app] = currentCounter - 1; currentForegroundAppsModified = true
                                }
                            }
                        }
                    }
                }
            }

            if (callsSinceLastActivityExistsCheck++ > 256) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "do activity exists check")
                }

                callsSinceLastActivityExistsCheck = 0

                val iterator = currentForegroundApps.iterator()

                while (iterator.hasNext()) {
                    val app = iterator.next().key

                    if (!doesActivityExist(app)) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "...remove $app")
                        }

                        iterator.remove()
                        currentForegroundAppsModified = true
                    }
                }
            }

            if (currentForegroundAppsModified) {
                currentForegroundAppsSnapshot = currentForegroundApps.keys.toSet()
            }

            lastQueryTime = now
        }

        return currentForegroundAppsSnapshot
    }

    override fun getPermissionStatus(): RuntimePermissionStatus {
        val appOpsStatus = appOpsManager.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)
        val packageManagerStatus = packageManager.checkPermission("android.permission.PACKAGE_USAGE_STATS", BuildConfig.APPLICATION_ID)

        val allowedUsingSystemSettings = appOpsStatus == AppOpsManager.MODE_ALLOWED
        val allowedUsingAdb = appOpsStatus == AppOpsManager.MODE_DEFAULT && packageManagerStatus == PackageManager.PERMISSION_GRANTED

        if(allowedUsingSystemSettings || allowedUsingAdb) {
            return RuntimePermissionStatus.Granted
        } else {
            return RuntimePermissionStatus.NotGranted
        }
    }

    // Android 9 (and maybe older versions too) do not report pausing Apps if they are disabled while running
    private fun doesActivityExist(app: ForegroundApp) = try {
        packageManager.getActivityInfo(ComponentName(app.packageName, app.activityName), 0).isEnabled
    } catch (ex: PackageManager.NameNotFoundException) {
        false
    }
}
