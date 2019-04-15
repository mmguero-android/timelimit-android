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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.integration.platform.ForegroundAppSpec
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LollipopForegroundAppHelper(private val context: Context) : ForegroundAppHelper() {
    companion object {
        private val foregroundAppThread: Executor by lazy { Executors.newSingleThreadExecutor() }
    }

    private val usageStatsManager = context.getSystemService(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) Context.USAGE_STATS_SERVICE else "usagestats") as UsageStatsManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    private var lastQueryTime: Long = 0
    private var lastPackage: String? = null
    private var lastPackageActivity: String? = null
    private var lastPackageTime: Long = 0
    private val event = UsageEvents.Event()

    @Throws(SecurityException::class)
    override suspend fun getForegroundApp(result: ForegroundAppSpec) {
        if (getPermissionStatus() == RuntimePermissionStatus.NotGranted) {
            throw SecurityException()
        }

        foregroundAppThread.executeAndWait {
            val now = System.currentTimeMillis()

            if (lastQueryTime > now) {
                // if the time went backwards, forget everything
                lastQueryTime = 0
                lastPackage = null
                lastPackageActivity = null
                lastPackageTime = 0
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
                lastQueryTime - 1500
            }

            usageStatsManager.queryEvents(queryStartTime, now)?.let { usageEvents ->
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)

                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (event.timeStamp > lastPackageTime) {
                            lastPackageTime = event.timeStamp
                            lastPackage = event.packageName
                            lastPackageActivity = event.className
                        }
                    }
                }
            }

            lastQueryTime = now

            result.packageName = lastPackage
            result.activityName = lastPackageActivity
        }
    }

    override fun getPermissionStatus(): RuntimePermissionStatus {
        if(appOpsManager.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED) {
            return RuntimePermissionStatus.Granted
        } else {
            return RuntimePermissionStatus.NotGranted
        }
    }
}
