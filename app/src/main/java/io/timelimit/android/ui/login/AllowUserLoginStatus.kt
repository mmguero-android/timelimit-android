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

package io.timelimit.android.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.derived.CompleteUserLoginRelatedData
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.RealTime
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import java.util.*
import java.util.concurrent.CountDownLatch

sealed class AllowUserLoginStatus {
    data class Allow(val maxTime: Long): AllowUserLoginStatus()
    data class ForbidByCurrentTime(val missingNetworkTime: Boolean, val maxTime: Long): AllowUserLoginStatus()
    data class ForbidByCategory(val categoryTitle: String, val blockingReason: BlockingReason, val maxTime: Long): AllowUserLoginStatus()
    object ForbidUserNotFound: AllowUserLoginStatus()
}

object AllowUserLoginStatusUtil {
    private fun calculate(data: CompleteUserLoginRelatedData, time: RealTime, cache: CategoryHandlingCache, batteryStatus: BatteryStatus): AllowUserLoginStatus = synchronized(cache) {
        val hasPremium = data.deviceRelatedData.isConnectedAndHasPremium || data.deviceRelatedData.isLocalMode

        if (!hasPremium) {
            return AllowUserLoginStatus.Allow(maxTime = Long.MAX_VALUE)
        }

        if (!data.loginRelatedData.user.blockedTimes.dataNotToModify.isEmpty) {
            if (!time.shouldTrustTimePermanently) {
                return AllowUserLoginStatus.ForbidByCurrentTime(missingNetworkTime = true, maxTime = Long.MAX_VALUE)
            } else {
                val minuteOfWeek = getMinuteOfWeek(time.timeInMillis, TimeZone.getTimeZone(data.loginRelatedData.user.timeZone))

                if (data.loginRelatedData.user.blockedTimes.dataNotToModify[minuteOfWeek]) {
                    val nextAllowedSlot = data.loginRelatedData.user.blockedTimes.dataNotToModify.nextClearBit(minuteOfWeek)
                    val minutesToWait: Long = (nextAllowedSlot - minuteOfWeek).toLong()
                    // not very nice but it works
                    val msToWait = if (minutesToWait <= 1) 5000 else (minutesToWait - 1) * 1000 * 60

                    return AllowUserLoginStatus.ForbidByCurrentTime(missingNetworkTime = false, maxTime = time.timeInMillis + msToWait)
                }
            }
        }

        return if (data.limitLoginCategoryUserRelatedData != null && data.loginRelatedData.limitLoginCategory != null) {
            cache.reportStatus(
                    user = data.limitLoginCategoryUserRelatedData,
                    assumeCurrentDevice = true,
                    timeInMillis = time.timeInMillis,
                    batteryStatus = batteryStatus,
                    shouldTrustTimeTemporarily = time.shouldTrustTimeTemporarily
            )

            val categoryIds = data.limitLoginCategoryUserRelatedData.getCategoryWithParentCategories(data.loginRelatedData.limitLoginCategory.categoryId)
            val handlings = categoryIds.map { cache.get(it) }

            val blockingHandling = handlings.find { it.shouldBlockAtSystemLevel }

            if (blockingHandling != null) {
                AllowUserLoginStatus.ForbidByCategory(
                        categoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title,
                        blockingReason = blockingHandling.systemLevelBlockingReason,
                        maxTime = blockingHandling.dependsOnMaxTime.coerceAtMost(
                                if (data.loginRelatedData.user.blockedTimes.dataNotToModify.isEmpty)
                                    Long.MAX_VALUE
                                else
                                    time.timeInMillis + 1000 * 5
                        )
                )
            } else {
                val maxTimeByCategories = handlings.minBy { it.dependsOnMaxTime }?.dependsOnMaxTime ?: Long.MAX_VALUE

                AllowUserLoginStatus.Allow(
                        maxTime = maxTimeByCategories.coerceAtMost(
                                if (data.loginRelatedData.user.blockedTimes.dataNotToModify.isEmpty)
                                    Long.MAX_VALUE
                                else
                                    time.timeInMillis + 1000 * 5
                        )
                )
            }
        } else {
            AllowUserLoginStatus.Allow(
                    maxTime = if (data.loginRelatedData.user.blockedTimes.dataNotToModify.isEmpty)
                        Long.MAX_VALUE
                    else
                        time.timeInMillis + 1000 * 5
            )
        }
    }

    fun calculateSync(logic: AppLogic, userId: String): AllowUserLoginStatus {
        val userRelatedData = logic.database.derivedDataDao().getUserLoginRelatedDataSync(userId) ?: return AllowUserLoginStatus.ForbidUserNotFound
        val realTime = RealTime.newInstance()
        val batteryStatus = logic.platformIntegration.getBatteryStatus()
        val latch = CountDownLatch(1)

        Threads.mainThreadHandler.post {
            logic.realTimeLogic.getRealTime(realTime)
            latch.countDown()
        }

        latch.await()

        return calculate(
                data = userRelatedData,
                batteryStatus = batteryStatus,
                time = realTime,
                cache = CategoryHandlingCache()
        )
    }

    fun calculateLive(logic: AppLogic, userId: String): LiveData<AllowUserLoginStatus> = object : MediatorLiveData<AllowUserLoginStatus>() {
        val cache = CategoryHandlingCache()
        val time = RealTime.newInstance()
        var batteryStatus: BatteryStatus? = null
        var hasUserLoginRelatedData = false
        var userLoginRelatedData: CompleteUserLoginRelatedData? = null

        init {
            addSource(logic.platformIntegration.getBatteryStatusLive(), androidx.lifecycle.Observer {
                batteryStatus = it; update()
            })

            addSource(logic.database.derivedDataDao().getUserLoginRelatedDataLive(userId), androidx.lifecycle.Observer {
                userLoginRelatedData = it; hasUserLoginRelatedData = true; update()
            })
        }

        val updateLambda: () -> Unit = { update() }
        val updateRunnable = Runnable { update() }

        fun update() {
            val batteryStatus = batteryStatus
            val userLoginRelatedData = userLoginRelatedData

            if (batteryStatus == null || !hasUserLoginRelatedData) return

            if (userLoginRelatedData == null) {
                if (value !== AllowUserLoginStatus.ForbidUserNotFound) {
                    value = AllowUserLoginStatus.ForbidUserNotFound
                }

                return
            }

            logic.realTimeLogic.getRealTime(time)

            val result = calculate(
                    data = userLoginRelatedData,
                    batteryStatus = batteryStatus,
                    cache = cache,
                    time = time
            )

            if (result != value) {
                value = result
            }

            val scheduledTime: Long = when (result) {
                AllowUserLoginStatus.ForbidUserNotFound -> Long.MAX_VALUE
                is AllowUserLoginStatus.ForbidByCurrentTime -> result.maxTime
                is AllowUserLoginStatus.Allow -> result.maxTime
                is AllowUserLoginStatus.ForbidByCategory -> result.maxTime
            }

            if (scheduledTime != Long.MAX_VALUE) {
                logic.timeApi.cancelScheduledAction(updateRunnable)
                logic.timeApi.runDelayedByUptime(updateRunnable, scheduledTime - time.timeInMillis)
            }
        }

        override fun onActive() {
            super.onActive()

            logic.realTimeLogic.registerTimeModificationListener(updateLambda)

            update()
        }

        override fun onInactive() {
            super.onInactive()

            logic.realTimeLogic.unregisterTimeModificationListener(updateLambda)
            logic.timeApi.cancelScheduledAction(updateRunnable)
        }
    }
}