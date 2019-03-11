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

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

class RealTimeLogic(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "RealTimeLogic"
    }

    private val deviceEntry = appLogic.deviceEntryIfEnabled
    val shouldQueryTime = deviceEntry.map {
        it != null &&
                (it.networkTime == NetworkTime.Enabled || it.networkTime == NetworkTime.IfPossible)
    }.ignoreUnchanged()

    init {
        deviceEntry.ignoreUnchanged().observeForever {
            // this keeps the value fresh
        }

        shouldQueryTime.observeForever {
            if (it != null && it) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "shouldQueryTime = true")
                }

                requireRemoteTimeUptime = appLogic.timeApi.getCurrentUptimeInMillis()
                tryQueryTime()
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "shouldQueryTime = false")
                }

                appLogic.timeApi.cancelScheduledAction(tryQueryTime)
            }
        }
    }

    private var lastSuccessfullyTimeRequestUptime: Long? = null
    private var uptimeRealTimeOffset: Long? = null
    private var requireRemoteTimeUptime: Long = 0
    private var confirmedUptimeSystemTimeOffset: Long? = null

    private val queryTimeLock = Mutex()
    private val tryQueryTime = Runnable { tryQueryTime() }

    fun tryQueryTime() {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "tryQueryTime")
        }

        runAsync {
            val owner = Object()

            if (queryTimeLock.tryLock(owner)) {
                appLogic.timeApi.cancelScheduledAction(tryQueryTime)

                try {
                    val server = appLogic.serverLogic.getServerConfigCoroutine()

                    if (!server.isAppEnabled) {
                        throw IOException("app during setup - time queries disabled")
                    }

                    val uptimeBefore = appLogic.timeApi.getCurrentUptimeInMillis()
                    val serverTime = server.api.getTimeInMillis()
                    val uptime = appLogic.timeApi.getCurrentUptimeInMillis()

                    val uptimeOffset = uptime - uptimeBefore

                    if (uptimeOffset > 30 * 1000 /* 30 seconds */) {
                        throw IOException("time request took too long")
                    }

                    uptimeRealTimeOffset = serverTime - uptime
                    lastSuccessfullyTimeRequestUptime = uptime

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "tryQueryTime was successfully in $uptimeOffset ms")
                    }

                    // schedule refresh in 2 hours
                    appLogic.timeApi.runDelayed(tryQueryTime, 1000 * 60 * 60 * 2)
                } catch (ex: Exception) {
                    if (uptimeRealTimeOffset == null) {
                        // schedule next attempt in 10 seconds
                        appLogic.timeApi.runDelayed(tryQueryTime, 1000 * 10)
                    } else {
                        // schedule next attempt in 10 minutes
                        appLogic.timeApi.runDelayed(tryQueryTime, 1000 * 60 * 10)
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "tryQueryTime failed")
                    }
                } finally {
                    queryTimeLock.unlock(owner)
                }
            }
        }
    }

    fun confirmLocalTime() {
        val uptime = appLogic.timeApi.getCurrentUptimeInMillis()
        val systemTime = appLogic.timeApi.getCurrentTimeInMillis()

        confirmedUptimeSystemTimeOffset = systemTime - uptime
    }

    fun getRealTime(time: RealTime) {
        val systemUptime = appLogic.timeApi.getCurrentUptimeInMillis()
        val systemTime = appLogic.timeApi.getCurrentTimeInMillis()

        val uptimeRealTimeOffset = uptimeRealTimeOffset
        val confirmedUptimeSystemTimeOffset = confirmedUptimeSystemTimeOffset

        val deviceConfig = deviceEntry.value

        if (deviceConfig == null) {
            time.timeInMillis = systemTime
            time.shouldTrustTimeTemporarily = true
            time.shouldTrustTimePermanently = false
            time.isNetworkTime = false
        } else if (deviceConfig.networkTime == NetworkTime.Disabled) {
            time.timeInMillis = systemTime
            time.shouldTrustTimeTemporarily = true
            time.shouldTrustTimePermanently = true
            time.isNetworkTime = false
        } else if (deviceConfig.networkTime == NetworkTime.IfPossible) {
            if (uptimeRealTimeOffset != null) {
                time.timeInMillis = systemUptime + uptimeRealTimeOffset
                time.shouldTrustTimeTemporarily = true
                time.shouldTrustTimePermanently = true
                time.isNetworkTime = true
            } else {
                time.timeInMillis = systemTime
                time.shouldTrustTimeTemporarily = true
                time.shouldTrustTimePermanently = true
                time.isNetworkTime = false
            }
        } else if (deviceConfig.networkTime == NetworkTime.Enabled) {
            if (uptimeRealTimeOffset != null) {
                time.timeInMillis = systemUptime + uptimeRealTimeOffset
                time.shouldTrustTimeTemporarily = true
                time.shouldTrustTimePermanently = true
                time.isNetworkTime = true
            } else if (confirmedUptimeSystemTimeOffset != null) {
                time.timeInMillis = systemUptime + confirmedUptimeSystemTimeOffset
                time.shouldTrustTimeTemporarily = true
                time.shouldTrustTimePermanently = false
                time.isNetworkTime = false
            } else {
                time.timeInMillis = systemTime
                // 5 seconds grace period
                time.shouldTrustTimeTemporarily = requireRemoteTimeUptime + 5000 > systemUptime
                time.shouldTrustTimePermanently = false
                time.isNetworkTime = false
            }
        } else {
            throw IllegalStateException()
        }
    }

    private val temp = RealTime.newInstance()

    fun getCurrentTimeInMillis(): Long {
        getRealTime(temp)

        return temp.timeInMillis
    }

    val durationSinceLastSuccessfullyTimeSync: Long?
        get() {
            val now = appLogic.timeApi.getCurrentUptimeInMillis()
            val last = lastSuccessfullyTimeRequestUptime

            return if (last == null)
                null
            else
                now - last
        }
}

data class RealTime(
        var timeInMillis: Long,
        var shouldTrustTimeTemporarily: Boolean,
        var shouldTrustTimePermanently: Boolean,
        var isNetworkTime: Boolean
) {
    companion object {
        fun newInstance() = RealTime(0, false, false, false)
    }
}
