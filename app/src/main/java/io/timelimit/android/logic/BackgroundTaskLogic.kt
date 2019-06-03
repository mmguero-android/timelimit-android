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
import android.util.SparseArray
import android.util.SparseLongArray
import android.widget.Toast
import androidx.lifecycle.LiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.data.model.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.integration.platform.AppStatusMessage
import io.timelimit.android.integration.platform.ForegroundAppSpec
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.android.AccessibilityService
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.UpdateDeviceStatusAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.util.AndroidVersion
import io.timelimit.android.util.TimeTextUtil
import io.timelimit.android.work.PeriodicSyncInBackgroundWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class BackgroundTaskLogic(val appLogic: AppLogic) {
    companion object {
        private const val CHECK_PERMISSION_INTERVAL = 10 * 1000L    // all 10 seconds
        private const val BACKGROUND_SERVICE_INTERVAL = 100L    // all 100 ms
        private const val MAX_USED_TIME_PER_ROUND = 1000        // 1 second
        private const val LOG_TAG = "BackgroundTaskLogic"
    }

    private val temporarilyAllowedApps = appLogic.deviceId.switchMap {
        if (it != null) {
            appLogic.database.temporarilyAllowedApp().getTemporarilyAllowedApps(it)
        } else {
            liveDataFromValue(Collections.emptyList<String>())
        }
    }

    init {
        runAsyncExpectForever { backgroundServiceLoop() }
        runAsyncExpectForever { syncDeviceStatusLoop() }
        runAsyncExpectForever { backupDatabaseLoop() }
        runAsync {
            // this is effective after an reboot

            if (appLogic.deviceEntryIfEnabled.waitForNullableValue() != null) {
                appLogic.platformIntegration.setEnableSystemLockdown(true)
            }
        }

        appLogic.deviceEntryIfEnabled
                .map { it?.id }
                .ignoreUnchanged()
                .observeForever {
                    _ ->

                    runAsync {
                        syncInstalledAppVersion()
                    }
                }

        temporarilyAllowedApps.map { it.isNotEmpty() }.ignoreUnchanged().observeForever {
            appLogic.platformIntegration.setShowNotificationToRevokeTemporarilyAllowedApps(it!!)
        }

        appLogic.database.config().getEnableBackgroundSyncAsync().ignoreUnchanged().observeForever {
            if (it) {
                PeriodicSyncInBackgroundWorker.enable()
            } else {
                PeriodicSyncInBackgroundWorker.disable()
            }
        }
    }

    private val deviceUserEntryLive = SingleItemLiveDataCache(appLogic.deviceUserEntry.ignoreUnchanged())
    private val isThisDeviceTheCurrentDeviceLive = SingleItemLiveDataCache(appLogic.currentDeviceLogic.isThisDeviceTheCurrentDevice)
    private val childCategories = object: MultiKeyLiveDataCache<List<Category>, String?>() {
        // key = child id
        override fun createValue(key: String?): LiveData<List<Category>> {
            if (key == null) {
                // this should rarely happen
                return liveDataFromValue(Collections.emptyList())
            } else {
                return appLogic.database.category().getCategoriesByChildId(key).ignoreUnchanged()
            }
        }
    }
    private val appCategories = object: MultiKeyLiveDataCache<CategoryApp?, Pair<String, List<String>>>() {
        // key = package name, category ids
        override fun createValue(key: Pair<String, List<String>>): LiveData<CategoryApp?> {
            return appLogic.database.categoryApp().getCategoryApp(key.second, key.first)
        }
    }
    private val timeLimitRules = object: MultiKeyLiveDataCache<List<TimeLimitRule>, String>() {
        override fun createValue(key: String): LiveData<List<TimeLimitRule>> {
            return appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(key)
        }
    }
    private val usedTimesOfCategoryAndWeekByFirstDayOfWeek = object: MultiKeyLiveDataCache<SparseArray<UsedTimeItem>, Pair<String, Int>>() {
        override fun createValue(key: Pair<String, Int>): LiveData<SparseArray<UsedTimeItem>> {
            return appLogic.database.usedTimes().getUsedTimesOfWeek(key.first, key.second)
        }
    }
    private val shouldDoAutomaticSignOut = SingleItemLiveDataCacheWithRequery { -> appLogic.defaultUserLogic.hasAutomaticSignOut()}

    private val liveDataCaches = LiveDataCaches(arrayOf(
            deviceUserEntryLive,
            childCategories,
            appCategories,
            timeLimitRules,
            usedTimesOfCategoryAndWeekByFirstDayOfWeek,
            shouldDoAutomaticSignOut
    ))

    private var usedTimeUpdateHelper: UsedTimeItemBatchUpdateHelper? = null
    private var previousMainLogicExecutionTime = 0
    private var previousMainLoopEndTime = 0L
    private val dayChangeTracker = DayChangeTracker(
            timeApi = appLogic.timeApi,
            longDuration = 1000 * 60 * 10 /* 10 minutes */
    )

    private val appTitleCache = QueryAppTitleCache(appLogic.platformIntegration)

    private suspend fun openLockscreen(blockedAppPackageName: String, blockedAppActivityName: String?) {
        appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                title = appTitleCache.query(blockedAppPackageName),
                text = appLogic.context.getString(R.string.background_logic_opening_lockscreen)
        ))

        appLogic.platformIntegration.setShowBlockingOverlay(true)

        if (appLogic.platformIntegration.isAccessibilityServiceEnabled()) {
            if (blockedAppPackageName != appLogic.platformIntegration.getLauncherAppPackageName()) {
                AccessibilityService.instance?.showHomescreen()
                delay(100)
                AccessibilityService.instance?.showHomescreen()
                delay(100)
            }
        }

        appLogic.platformIntegration.showAppLockScreen(blockedAppPackageName, blockedAppActivityName)
    }

    private val foregroundAppSpec = ForegroundAppSpec.newInstance()

    private suspend fun backgroundServiceLoop() {
        val realTime = RealTime.newInstance()

        while (true) {
            // app must be enabled
            if (!appLogic.enable.waitForNonNullValue()) {
                usedTimeUpdateHelper?.commit(appLogic)
                liveDataCaches.removeAllItems()
                appLogic.platformIntegration.setAppStatusMessage(null)
                appLogic.platformIntegration.setShowBlockingOverlay(false)
                appLogic.enable.waitUntilValueMatches { it == true }

                continue
            }

            // device must be used by a child
            val deviceUserEntry = deviceUserEntryLive.read().waitForNullableValue()

            if (deviceUserEntry == null || deviceUserEntry.type != UserType.Child) {
                usedTimeUpdateHelper?.commit(appLogic)
                val shouldDoAutomaticSignOut = shouldDoAutomaticSignOut.read()

                if (shouldDoAutomaticSignOut.waitForNonNullValue()) {
                    appLogic.defaultUserLogic.reportScreenOn(appLogic.platformIntegration.isScreenOn())

                    appLogic.platformIntegration.setAppStatusMessage(
                            AppStatusMessage(
                                    title = appLogic.context.getString(R.string.background_logic_timeout_title),
                                    text = appLogic.context.getString(R.string.background_logic_timeout_text),
                                    showSwitchToDefaultUserOption = true
                            )
                    )
                    appLogic.platformIntegration.setShowBlockingOverlay(false)

                    liveDataCaches.reportLoopDone()
                    appLogic.timeApi.sleep(BACKGROUND_SERVICE_INTERVAL)
                } else {
                    liveDataCaches.removeAllItems()
                    appLogic.platformIntegration.setAppStatusMessage(null)
                    appLogic.platformIntegration.setShowBlockingOverlay(false)

                    val isChildSignedIn = deviceUserEntryLive.read().map { it != null && it.type == UserType.Child }

                    isChildSignedIn.or(shouldDoAutomaticSignOut).waitUntilValueMatches { it == true }
                }

                continue
            }

            // loop logic
            try {
                // get the current time
                appLogic.realTimeLogic.getRealTime(realTime)

                val nowTimestamp = realTime.timeInMillis
                val nowTimezone = TimeZone.getTimeZone(deviceUserEntry.timeZone)

                val nowDate = DateInTimezone.newInstance(nowTimestamp, nowTimezone)
                val minuteOfWeek = getMinuteOfWeek(nowTimestamp, nowTimezone)

                // eventually remove old used time data
                if (realTime.shouldTrustTimePermanently) {
                    val dayChange = dayChangeTracker.reportDayChange(nowDate.dayOfEpoch)

                    fun deleteOldUsedTimes() = UsedTimeDeleter.deleteOldUsedTimeItems(
                            database = appLogic.database,
                            date = nowDate
                    )

                    if (realTime.isNetworkTime) {
                        if (dayChange == DayChangeTracker.DayChange.Now) {
                            deleteOldUsedTimes()
                        }
                    } else {
                        if (dayChange == DayChangeTracker.DayChange.NowSinceLongerTime) {
                            deleteOldUsedTimes()
                        }
                    }
                }

                // get the categories
                val categories = childCategories.get(deviceUserEntry.id).waitForNonNullValue()
                val temporarilyAllowedApps = temporarilyAllowedApps.waitForNonNullValue()

                // get the current status
                val isScreenOn = appLogic.platformIntegration.isScreenOn()

                appLogic.defaultUserLogic.reportScreenOn(isScreenOn)

                if (!isScreenOn) {
                    if (temporarilyAllowedApps.isNotEmpty()) {
                        resetTemporarilyAllowedApps()
                    }
                }

                appLogic.platformIntegration.getForegroundApp(foregroundAppSpec, appLogic.getForegroundAppQueryInterval())
                val foregroundAppPackageName = foregroundAppSpec.packageName
                val foregroundAppActivityName = foregroundAppSpec.activityName
                val activityLevelBlocking = appLogic.deviceEntry.value?.enableActivityLevelBlocking ?: false

                fun showStatusMessageWithCurrentAppTitle(text: String, titlePrefix: String? = "") {
                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                            titlePrefix + appTitleCache.query(foregroundAppPackageName ?: "invalid"),
                            text,
                            if (activityLevelBlocking) foregroundAppActivityName?.removePrefix(foregroundAppPackageName ?: "invalid") else null
                    ))
                }

                // the following is not executed if the permission is missing

                if (
                        (foregroundAppPackageName == BuildConfig.APPLICATION_ID) ||
                        (foregroundAppPackageName != null && AndroidIntegrationApps.ignoredApps[foregroundAppPackageName].let {
                            when (it) {
                                null -> false
                                AndroidIntegrationApps.IgnoredAppHandling.Ignore -> true
                                AndroidIntegrationApps.IgnoredAppHandling.IgnoreOnStoreOtherwiseWhitelistAndDontDisable -> BuildConfig.storeCompilant
                            }
                        })
                ) {
                    usedTimeUpdateHelper?.commit(appLogic)
                    showStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_whitelisted)
                    )
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                } else if (foregroundAppPackageName != null && temporarilyAllowedApps.contains(foregroundAppPackageName)) {
                    usedTimeUpdateHelper?.commit(appLogic)
                    showStatusMessageWithCurrentAppTitle(appLogic.context.getString(R.string.background_logic_temporarily_allowed))
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                } else if (foregroundAppPackageName != null) {
                    val categoryIds = categories.map { it.id }

                    val appCategory = run {
                        val appLevelCategoryLive = appCategories.get(foregroundAppPackageName to categoryIds)

                        if (activityLevelBlocking) {
                            val appActivityCategoryLive = appCategories.get("$foregroundAppPackageName:$foregroundAppActivityName" to categoryIds)

                            appActivityCategoryLive.waitForNullableValue() ?: appLevelCategoryLive.waitForNullableValue()
                        } else {
                            appLevelCategoryLive.waitForNullableValue()
                        }
                    }

                    val category = categories.find { it.id == appCategory?.categoryId }
                            ?: categories.find { it.id == deviceUserEntry.categoryForNotAssignedApps }
                    val parentCategory = categories.find { it.id == category?.parentCategoryId }

                    if (category == null) {
                        usedTimeUpdateHelper?.commit(appLogic)

                        if (AndroidIntegrationApps.ignoredApps[foregroundAppPackageName] == null) {
                            // don't suspend system apps which are whitelisted in any version
                            appLogic.platformIntegration.setSuspendedApps(listOf(foregroundAppPackageName), true)
                        }

                        openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                    } else if (category.temporarilyBlocked or (parentCategory?.temporarilyBlocked == true)) {
                        usedTimeUpdateHelper?.commit(appLogic)

                        openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                    } else {
                        // disable time limits temporarily feature
                        if (realTime.shouldTrustTimeTemporarily && nowTimestamp < deviceUserEntry.disableLimitsUntil) {
                            showStatusMessageWithCurrentAppTitle(appLogic.context.getString(R.string.background_logic_limits_disabled))
                            appLogic.platformIntegration.setShowBlockingOverlay(false)
                        } else if (
                        // check blocked time areas
                        // directly blocked
                                (category.blockedMinutesInWeek.read(minuteOfWeek)) or
                                (parentCategory?.blockedMinutesInWeek?.read(minuteOfWeek) == true) or
                                // or no safe time
                                (
                                        (
                                                (category.blockedMinutesInWeek.dataNotToModify.isEmpty == false) or
                                                        (parentCategory?.blockedMinutesInWeek?.dataNotToModify?.isEmpty == false)
                                                ) &&
                                                (!realTime.shouldTrustTimeTemporarily)
                                        )
                        ) {
                            usedTimeUpdateHelper?.commit(appLogic)

                            openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                        } else {
                            // check time limits
                            val rules = timeLimitRules.get(category.id).waitForNonNullValue()
                            val parentRules = parentCategory?.let {
                                timeLimitRules.get(it.id).waitForNonNullValue()
                            } ?: emptyList()

                            if (rules.isEmpty() and parentRules.isEmpty()) {
                                // unlimited
                                usedTimeUpdateHelper?.commit(appLogic)

                                showStatusMessageWithCurrentAppTitle(
                                        text = appLogic.context.getString(R.string.background_logic_no_timelimit),
                                        titlePrefix = category.title + " - "
                                )
                                appLogic.platformIntegration.setShowBlockingOverlay(false)
                            } else {
                                val isCurrentDevice = isThisDeviceTheCurrentDeviceLive.read().waitForNonNullValue()

                                if (!isCurrentDevice) {
                                    usedTimeUpdateHelper?.commit(appLogic)

                                    openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                                } else if (realTime.shouldTrustTimeTemporarily) {
                                    val usedTimes = usedTimesOfCategoryAndWeekByFirstDayOfWeek.get(Pair(category.id, nowDate.dayOfEpoch - nowDate.dayOfWeek)).waitForNonNullValue()
                                    val parentUsedTimes = parentCategory?.let {
                                        usedTimesOfCategoryAndWeekByFirstDayOfWeek.get(Pair(it.id, nowDate.dayOfEpoch - nowDate.dayOfWeek)).waitForNonNullValue()
                                    } ?: SparseArray()

                                    val newUsedTimeItemBatchUpdateHelper = UsedTimeItemBatchUpdateHelper.eventuallyUpdateInstance(
                                            date = nowDate,
                                            childCategoryId = category.id,
                                            parentCategoryId = parentCategory?.id,
                                            oldInstance = usedTimeUpdateHelper,
                                            usedTimeItemForDayChild = usedTimes.get(nowDate.dayOfWeek),
                                            usedTimeItemForDayParent = parentUsedTimes.get(nowDate.dayOfWeek),
                                            logic = appLogic
                                    )
                                    usedTimeUpdateHelper = newUsedTimeItemBatchUpdateHelper

                                    fun buildUsedTimesSparseArray(items: SparseArray<UsedTimeItem>, isParentCategory: Boolean): SparseLongArray {
                                        val result = SparseLongArray()

                                        for (i in 0..6) {
                                            val usedTimesItem = items[i]?.usedMillis

                                            if (newUsedTimeItemBatchUpdateHelper.date.dayOfWeek == i) {
                                                result.put(
                                                        i,
                                                        if (isParentCategory)
                                                            newUsedTimeItemBatchUpdateHelper.getTotalUsedTimeParent()
                                                        else
                                                            newUsedTimeItemBatchUpdateHelper.getTotalUsedTimeChild()
                                                )
                                            } else {
                                                result.put(i, usedTimesItem ?: 0)
                                            }
                                        }

                                        return result
                                    }

                                    val remainingChild = RemainingTime.getRemainingTime(
                                            nowDate.dayOfWeek,
                                            buildUsedTimesSparseArray(usedTimes, isParentCategory = false),
                                            rules,
                                            Math.max(0, category.extraTimeInMillis - newUsedTimeItemBatchUpdateHelper.getCachedExtraTimeToSubtract())
                                    )

                                    val remainingParent = parentCategory?.let {
                                        RemainingTime.getRemainingTime(
                                                nowDate.dayOfWeek,
                                                buildUsedTimesSparseArray(parentUsedTimes, isParentCategory = true),
                                                parentRules,
                                                Math.max(0, parentCategory.extraTimeInMillis - newUsedTimeItemBatchUpdateHelper.getCachedExtraTimeToSubtract())
                                        )
                                    }

                                    val remaining = RemainingTime.min(remainingChild, remainingParent)

                                    if (remaining == null) {
                                        // unlimited

                                        usedTimeUpdateHelper?.commit(appLogic)

                                        showStatusMessageWithCurrentAppTitle(
                                                text = appLogic.context.getString(R.string.background_logic_no_timelimit),
                                                titlePrefix = category.title + " - "
                                        )
                                        appLogic.platformIntegration.setShowBlockingOverlay(false)
                                    } else {
                                        // time limited
                                        if (remaining.includingExtraTime > 0) {
                                            var subtractExtraTime: Boolean

                                            if (remaining.default == 0L) {
                                                // using extra time
                                                showStatusMessageWithCurrentAppTitle(
                                                        text = appLogic.context.getString(R.string.background_logic_using_extra_time, TimeTextUtil.remaining(remaining.includingExtraTime.toInt(), appLogic.context)),
                                                        titlePrefix = category.title + " - "
                                                )
                                                subtractExtraTime = true
                                            } else {
                                                // using normal contingent
                                                showStatusMessageWithCurrentAppTitle(
                                                        text = TimeTextUtil.remaining(remaining.default.toInt(), appLogic.context),
                                                        titlePrefix = category.title + " - "
                                                )
                                                subtractExtraTime = false
                                            }

                                            appLogic.platformIntegration.setShowBlockingOverlay(false)
                                            if (isScreenOn) {
                                                // never save more than a second of used time
                                                val timeToSubtract = Math.min(previousMainLogicExecutionTime, MAX_USED_TIME_PER_ROUND)

                                                newUsedTimeItemBatchUpdateHelper.addUsedTime(
                                                        timeToSubtract,
                                                        subtractExtraTime,
                                                        appLogic
                                                )

                                                val oldRemainingTime = remaining.includingExtraTime
                                                val newRemainingTime = oldRemainingTime - timeToSubtract

                                                if (oldRemainingTime / (1000 * 60) != newRemainingTime / (1000 * 60)) {
                                                    // eventually show remaining time warning
                                                    val roundedNewTime = (newRemainingTime / (1000 * 60)) * (1000 * 60)
                                                    val flagIndex = CategoryTimeWarnings.durationToBitIndex[roundedNewTime]

                                                    if (flagIndex != null && category.timeWarnings and (1 shl flagIndex) != 0) {
                                                        appLogic.platformIntegration.showTimeWarningNotification(
                                                                title = appLogic.context.getString(R.string.time_warning_not_title, category.title),
                                                                text = TimeTextUtil.remaining(roundedNewTime.toInt(), appLogic.context)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // there is not time anymore

                                            newUsedTimeItemBatchUpdateHelper.commit(appLogic)

                                            openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                                        }
                                    }
                                } else {
                                    // if should not trust the time temporarily

                                    usedTimeUpdateHelper?.commit(appLogic)

                                    openLockscreen(foregroundAppPackageName, foregroundAppActivityName)
                                }
                            }
                        }
                    }
                } else {
                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                            appLogic.context.getString(R.string.background_logic_idle_title),
                            appLogic.context.getString(R.string.background_logic_idle_text)
                    ))
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                }
            } catch (ex: SecurityException) {
                // this is handled by an other main loop (with a delay)

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_permission)
                ))
                appLogic.platformIntegration.setShowBlockingOverlay(false)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "exception during running main loop", ex)
                }

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_internal)
                ))
                appLogic.platformIntegration.setShowBlockingOverlay(false)
            }

            liveDataCaches.reportLoopDone()

            // delay before running next time
            val endTime = appLogic.timeApi.getCurrentUptimeInMillis()
            previousMainLogicExecutionTime = (endTime - previousMainLoopEndTime).toInt()
            previousMainLoopEndTime = endTime

            val timeToWait = Math.max(10, BACKGROUND_SERVICE_INTERVAL - previousMainLogicExecutionTime)
            appLogic.timeApi.sleep(timeToWait)
        }
    }

    private suspend fun syncInstalledAppVersion() {
        val currentAppVersion = BuildConfig.VERSION_CODE
        val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

        if (deviceEntry != null) {
            if (deviceEntry.currentAppVersion != currentAppVersion) {
                ApplyActionUtil.applyAppLogicAction(
                        action = UpdateDeviceStatusAction.empty.copy(
                                newAppVersion = currentAppVersion
                        ),
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    fun syncDeviceStatusAsync() {
        runAsync {
            syncDeviceStatus()
        }
    }

    private suspend fun syncDeviceStatusLoop() {
        while (true) {
            appLogic.deviceEntryIfEnabled.waitUntilValueMatches { it != null }

            syncDeviceStatus()

            appLogic.timeApi.sleep(CHECK_PERMISSION_INTERVAL)
        }
    }

    private val syncDeviceStatusLock = Mutex()

    fun reportDeviceReboot() {
        runAsync {
            val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

            if (deviceEntry?.considerRebootManipulation == true) {
                ApplyActionUtil.applyAppLogicAction(
                        action = UpdateDeviceStatusAction.empty.copy(
                                didReboot = true
                        ),
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    private suspend fun syncDeviceStatus() {
        syncDeviceStatusLock.withLock {
            val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

            if (deviceEntry != null) {
                val protectionLevel = appLogic.platformIntegration.getCurrentProtectionLevel()
                val usageStatsPermission = appLogic.platformIntegration.getForegroundAppPermissionStatus()
                val notificationAccess = appLogic.platformIntegration.getNotificationAccessPermissionStatus()
                val overlayPermission = appLogic.platformIntegration.getOverlayPermissionStatus()
                val accessibilityService = appLogic.platformIntegration.isAccessibilityServiceEnabled()
                val qOrLater = AndroidVersion.qOrLater

                var changes = UpdateDeviceStatusAction.empty

                if (protectionLevel != deviceEntry.currentProtectionLevel) {
                    changes = changes.copy(
                            newProtectionLevel = protectionLevel
                    )

                    if (protectionLevel == ProtectionLevel.DeviceOwner) {
                        appLogic.platformIntegration.setEnableSystemLockdown(true)
                    }
                }

                if (usageStatsPermission != deviceEntry.currentUsageStatsPermission) {
                    changes = changes.copy(
                            newUsageStatsPermissionStatus = usageStatsPermission
                    )
                }

                if (notificationAccess != deviceEntry.currentNotificationAccessPermission) {
                    changes = changes.copy(
                            newNotificationAccessPermission = notificationAccess
                    )
                }

                if (overlayPermission != deviceEntry.currentOverlayPermission) {
                    changes = changes.copy(
                            newOverlayPermission = overlayPermission
                    )
                }

                if (accessibilityService != deviceEntry.accessibilityServiceEnabled) {
                    changes = changes.copy(
                            newAccessibilityServiceEnabled = accessibilityService
                    )
                }

                if (qOrLater && !deviceEntry.qOrLater) {
                    changes = changes.copy(isQOrLaterNow = true)
                }

                if (changes != UpdateDeviceStatusAction.empty) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = changes,
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }
        }
    }

    suspend fun resetTemporarilyAllowedApps() {
        val deviceId = appLogic.deviceId.waitForNullableValue()

        if (deviceId != null) {
            Threads.database.executeAndWait(Runnable {
                appLogic.database.temporarilyAllowedApp().removeAllTemporarilyAllowedAppsSync(deviceId)
            })
        }
    }

    private suspend fun backupDatabaseLoop() {
        appLogic.timeApi.sleep(1000 * 60 * 5 /* 5 minutes */)

        while (true) {
            DatabaseBackup.with(appLogic.context).tryCreateDatabaseBackupAsync()

            appLogic.timeApi.sleep(1000 * 60 * 60 * 3 /* 3 hours */)
        }
    }
}
