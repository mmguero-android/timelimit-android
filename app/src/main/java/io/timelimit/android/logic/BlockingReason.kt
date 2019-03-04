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
import android.util.SparseLongArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.integration.time.TimeApi
import io.timelimit.android.livedata.*
import java.util.*

enum class BlockingReason {
    None,
    NotPartOfAnCategory,
    TemporarilyBlocked,
    BlockedAtThisTime,
    TimeOver,
    TimeOverExtraTimeCanBeUsedLater,
    MissingNetworkTime,
    RequiresCurrentDevice,
    NotificationsAreBlocked
}

class BlockingReasonUtil(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "BlockingReason"
    }

    fun getBlockingReason(packageName: String, forNotification: Boolean): LiveData<BlockingReason> {
        // check precondition that the app is running

        return appLogic.enable.switchMap {
            enabled ->

            if (enabled == null || enabled == false) {
                liveDataFromValue(BlockingReason.None)
            } else {
                appLogic.deviceUserEntry.switchMap {
                    user ->

                    if (user == null || user.type != UserType.Child) {
                        liveDataFromValue(BlockingReason.None)
                    } else {
                        getBlockingReasonStep2(packageName, user, TimeZone.getTimeZone(user.timeZone), forNotification)
                    }
                }
            }
        }
    }

    private fun getBlockingReasonStep2(packageName: String, child: User, timeZone: TimeZone, forNotification: Boolean): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 2")
        }

        // check internal whitelist
        if (packageName == BuildConfig.APPLICATION_ID) {
            return liveDataFromValue(BlockingReason.None)
        } else if (AndroidIntegrationApps.ignoredApps.contains(packageName)) {
            return liveDataFromValue(BlockingReason.None)
        } else {
            return getBlockingReasonStep3(packageName, child, timeZone, forNotification)
        }
    }

    private fun getBlockingReasonStep3(packageName: String, child: User, timeZone: TimeZone, forNotification: Boolean): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 3")
        }

        // check temporarily allowed Apps
        return appLogic.deviceId.switchMap {
            if (it != null) {
                appLogic.database.temporarilyAllowedApp().getTemporarilyAllowedApps(deviceId = it)
            } else {
                liveDataFromValue(Collections.emptyList())
            }
        }.switchMap {
            temporarilyAllowedApps ->

            if (temporarilyAllowedApps.contains(packageName)) {
                liveDataFromValue(BlockingReason.None)
            } else {
                getBlockingReasonStep4(packageName, child, timeZone, forNotification)
            }
        }
    }

    private fun getBlockingReasonStep4(packageName: String, child: User, timeZone: TimeZone, forNotification: Boolean): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 4")
        }

        return appLogic.database.category().getCategoriesByChildId(child.id).switchMap {
            childCategories ->

            Transformations.map(appLogic.database.categoryApp().getCategoryApp(childCategories.map { it.id }, packageName)) {
                categoryApp ->

                if (categoryApp == null) {
                    null
                } else {
                    childCategories.find { it.id == categoryApp.categoryId }
                }
            }
        }.switchMap {
            categoryEntry ->

            if (categoryEntry == null) {
                val defaultCategory = if (child.categoryForNotAssignedApps.isEmpty())
                    liveDataFromValue(null as Category?)
                else
                    appLogic.database.category().getCategoryByChildIdAndId(child.id, child.categoryForNotAssignedApps)

                defaultCategory.switchMap { categoryEntry2 ->
                    if (categoryEntry2 == null) {
                        liveDataFromValue(BlockingReason.NotPartOfAnCategory)
                    } else {
                        getBlockingReasonStep4Point5(categoryEntry2, child, timeZone, false, forNotification)
                    }
                }
            } else {
                getBlockingReasonStep4Point5(categoryEntry, child, timeZone, false, forNotification)
            }
        }
    }

    private fun getBlockingReasonStep4Point5(category: Category, child: User, timeZone: TimeZone, isParentCategory: Boolean, forNotification: Boolean): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 4.5")
        }

        if (forNotification && category.blockAllNotifications) {
            return liveDataFromValue(BlockingReason.NotificationsAreBlocked)
        }

        if (category.temporarilyBlocked) {
            return liveDataFromValue(BlockingReason.TemporarilyBlocked)
        }

        val areLimitsDisabled: LiveData<Boolean>

        if (child.disableLimitsUntil == 0L) {
            areLimitsDisabled = liveDataFromValue(false)
        } else {
            areLimitsDisabled = getTemporarilyTrustedTimeInMillis().map {
                trustedTimeInMillis ->

                trustedTimeInMillis != null && child.disableLimitsUntil > trustedTimeInMillis
            }
        }

        return areLimitsDisabled.switchMap {
            limitsDisabled ->

            if (limitsDisabled) {
                liveDataFromValue(BlockingReason.None)
            } else {
                getBlockingReasonStep5(category, timeZone)
            }
        }.switchMap { result ->
            if (result == BlockingReason.None && (!isParentCategory) && category.parentCategoryId.isNotEmpty()) {
                appLogic.database.category().getCategoryByChildIdAndId(child.id, category.parentCategoryId).switchMap { parentCategory ->
                    if (parentCategory == null) {
                        liveDataFromValue(BlockingReason.None)
                    } else {
                        getBlockingReasonStep4Point5(parentCategory, child, timeZone, true, forNotification)
                    }
                }
            } else {
                liveDataFromValue(result)
            }
        }
    }

    private fun getBlockingReasonStep5(category: Category, timeZone: TimeZone): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 5")
        }

        return Transformations.switchMap(getTrustedMinuteOfWeekLive(appLogic.timeApi, timeZone)) {
            trustedMinuteOfWeek ->

            if (category.blockedMinutesInWeek.dataNotToModify.isEmpty) {
                getBlockingReasonStep6(category, timeZone)
            } else if (trustedMinuteOfWeek == null) {
                liveDataFromValue(BlockingReason.MissingNetworkTime)
            } else if (category.blockedMinutesInWeek.read(trustedMinuteOfWeek)) {
                liveDataFromValue(BlockingReason.BlockedAtThisTime)
            } else {
                getBlockingReasonStep6(category, timeZone)
            }
        }
    }

    private fun getBlockingReasonStep6(category: Category, timeZone: TimeZone): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 6")
        }

        return getTrustedDateLive(appLogic.timeApi, timeZone).switchMap {
            nowTrustedDate ->

            appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(category.id).switchMap {
                rules ->

                if (rules.isEmpty()) {
                    liveDataFromValue(BlockingReason.None)
                } else if (nowTrustedDate == null) {
                    liveDataFromValue(BlockingReason.MissingNetworkTime)
                } else {
                    getBlockingReasonStep6(category, nowTrustedDate, rules)
                }
            }
        }
    }

    private fun getBlockingReasonStep6(category: Category, nowTrustedDate: DateInTimezone, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 6 - 2")
        }

        return appLogic.currentDeviceLogic.isThisDeviceTheCurrentDevice.switchMap { isCurrentDevice ->
            if (isCurrentDevice) {
                getBlockingReasonStep7(category, nowTrustedDate, rules)
            } else {
                liveDataFromValue(BlockingReason.RequiresCurrentDevice)
            }
        }
    }

    private fun getBlockingReasonStep7(category: Category, nowTrustedDate: DateInTimezone, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 7")
        }

        return appLogic.database.usedTimes().getUsedTimesOfWeek(category.id, nowTrustedDate.dayOfEpoch - nowTrustedDate.dayOfWeek).map {
            usedTimes ->
            val usedTimesSparseArray = SparseLongArray()

            for (i in 0..6) {
                val usedTimesItem = usedTimes[i]?.usedMillis
                usedTimesSparseArray.put(i, (if (usedTimesItem != null) usedTimesItem else 0))
            }

            val remaining = RemainingTime.getRemainingTime(nowTrustedDate.dayOfWeek, usedTimesSparseArray, rules, category.extraTimeInMillis)

            if (remaining == null || remaining.includingExtraTime > 0) {
                BlockingReason.None
            } else {
                if (category.extraTimeInMillis > 0) {
                    BlockingReason.TimeOverExtraTimeCanBeUsedLater
                } else {
                    BlockingReason.TimeOver
                }
            }
        }
    }

    private fun getTemporarilyTrustedTimeInMillis(): LiveData<Long?> {
        val realTime = RealTime.newInstance()

        return liveDataFromFunction {
            appLogic.realTimeLogic.getRealTime(realTime)

            if (realTime.shouldTrustTimeTemporarily) {
                realTime.timeInMillis
            } else {
                null
            }
        }
    }

    private fun getTrustedMinuteOfWeekLive(api: TimeApi, timeZone: TimeZone): LiveData<Int?> {
        val realTime = RealTime.newInstance()

        return object: LiveData<Int?>() {
            fun update() {
                appLogic.realTimeLogic.getRealTime(realTime)

                if (realTime.shouldTrustTimeTemporarily) {
                    value = getMinuteOfWeek(realTime.timeInMillis, timeZone)
                } else {
                    value = null
                }
            }

            init {
                update()
            }

            val scheduledUpdateRunnable = Runnable {
                update()
                scheduleUpdate()
            }

            fun scheduleUpdate() {
                api.runDelayed(scheduledUpdateRunnable, 1000L /* every second */)
            }

            fun cancelScheduledUpdate() {
                api.cancelScheduledAction(scheduledUpdateRunnable)
            }

            override fun onActive() {
                super.onActive()

                update()
                scheduleUpdate()
            }

            override fun onInactive() {
                super.onInactive()

                cancelScheduledUpdate()
            }
        }.ignoreUnchanged()
    }

    private fun getTrustedDateLive(api: TimeApi, timeZone: TimeZone): LiveData<DateInTimezone?> {
        val realTime = RealTime.newInstance()

        return object: LiveData<DateInTimezone?>() {
            fun update() {
                appLogic.realTimeLogic.getRealTime(realTime)

                if (realTime.shouldTrustTimeTemporarily) {
                    value = DateInTimezone.newInstance(realTime.timeInMillis, timeZone)
                } else {
                    value = null
                }
            }

            init {
                update()
            }

            val scheduledUpdateRunnable = Runnable {
                update()
                scheduleUpdate()
            }

            fun scheduleUpdate() {
                api.runDelayed(scheduledUpdateRunnable, 1000L /* every second */)
            }

            fun cancelScheduledUpdate() {
                api.cancelScheduledAction(scheduledUpdateRunnable)
            }

            override fun onActive() {
                super.onActive()

                update()
                scheduleUpdate()
            }

            override fun onInactive() {
                super.onInactive()

                cancelScheduledUpdate()
            }
        }.ignoreUnchanged()
    }
}
