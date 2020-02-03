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
package io.timelimit.android.logic

import android.util.Log
import android.util.SparseArray
import android.util.SparseLongArray
import androidx.lifecycle.MutableLiveData
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
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.UpdateDeviceStatusAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.util.AndroidVersion
import io.timelimit.android.util.TimeTextUtil
import io.timelimit.android.work.PeriodicSyncInBackgroundWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class BackgroundTaskLogic(val appLogic: AppLogic) {
    var pauseForegroundAppBackgroundLoop = false
    val lastLoopException = MutableLiveData<Exception?>().apply { value = null }

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
        runAsyncExpectForever { annoyUserOnManipulationLoop() }
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

    private val cache = BackgroundTaskLogicCache(appLogic)
    private val deviceUserEntryLive = cache.deviceUserEntryLive
    private val childCategories = cache.childCategories
    private val timeLimitRules = cache.timeLimitRules
    private val usedTimesOfCategoryAndWeekByFirstDayOfWeek = cache.usedTimesOfCategoryAndWeekByFirstDayOfWeek
    private val shouldDoAutomaticSignOut = cache.shouldDoAutomaticSignOut
    private val liveDataCaches = cache.liveDataCaches

    private var usedTimeUpdateHelper: UsedTimeUpdateHelper? = null
    private var previousMainLogicExecutionTime = 0
    private var previousMainLoopEndTime = 0L
    private val dayChangeTracker = DayChangeTracker(
            timeApi = appLogic.timeApi,
            longDuration = 1000 * 60 * 10 /* 10 minutes */
    )

    private val appTitleCache = QueryAppTitleCache(appLogic.platformIntegration)

    private suspend fun openLockscreen(blockedAppPackageName: String, blockedAppActivityName: String?) {
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
    val foregroundAppHandling = BackgroundTaskRestrictionLogicResult()
    val audioPlaybackHandling = BackgroundTaskRestrictionLogicResult()

    private suspend fun commitUsedTimeUpdaters() {
        usedTimeUpdateHelper?.forceCommit(appLogic)
    }

    private suspend fun backgroundServiceLoop() {
        val realTime = RealTime.newInstance()

        while (true) {
            // app must be enabled
            if (!appLogic.enable.waitForNonNullValue()) {
                commitUsedTimeUpdaters()
                liveDataCaches.removeAllItems()
                appLogic.platformIntegration.setAppStatusMessage(null)
                appLogic.platformIntegration.setShowBlockingOverlay(false)
                appLogic.enable.waitUntilValueMatches { it == true }

                continue
            }

            // device must be used by a child
            val deviceUserEntry = deviceUserEntryLive.read().waitForNullableValue()

            if (deviceUserEntry == null || deviceUserEntry.type != UserType.Child) {
                commitUsedTimeUpdaters()
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
                val batteryStatus = appLogic.platformIntegration.getBatteryStatus()

                appLogic.defaultUserLogic.reportScreenOn(isScreenOn)

                if (!isScreenOn) {
                    if (temporarilyAllowedApps.isNotEmpty()) {
                        resetTemporarilyAllowedApps()
                    }
                }

                appLogic.platformIntegration.getForegroundApp(foregroundAppSpec, appLogic.getForegroundAppQueryInterval())
                val foregroundAppPackageName = foregroundAppSpec.packageName
                val foregroundAppActivityName = foregroundAppSpec.activityName
                val audioPlaybackPackageName = appLogic.platformIntegration.getMusicPlaybackPackage()
                val activityLevelBlocking = appLogic.deviceEntry.value?.enableActivityLevelBlocking ?: false

                foregroundAppHandling.reset()
                audioPlaybackHandling.reset()

                BackgroundTaskRestrictionLogic.getHandling(
                        foregroundAppPackageName = foregroundAppPackageName,
                        foregroundAppActivityName = foregroundAppActivityName,
                        pauseForegroundAppBackgroundLoop = pauseForegroundAppBackgroundLoop,
                        temporarilyAllowedApps = temporarilyAllowedApps,
                        categories = categories,
                        activityLevelBlocking = activityLevelBlocking,
                        deviceUserEntry = deviceUserEntry,
                        batteryStatus = batteryStatus,
                        shouldTrustTimeTemporarily = realTime.shouldTrustTimeTemporarily,
                        nowTimestamp = nowTimestamp,
                        minuteOfWeek = minuteOfWeek,
                        cache = cache,
                        result = foregroundAppHandling
                )

                BackgroundTaskRestrictionLogic.getHandling(
                        foregroundAppPackageName = audioPlaybackPackageName,
                        foregroundAppActivityName = null,
                        pauseForegroundAppBackgroundLoop = false,
                        temporarilyAllowedApps = temporarilyAllowedApps,
                        categories = categories,
                        activityLevelBlocking = activityLevelBlocking,
                        deviceUserEntry = deviceUserEntry,
                        batteryStatus = batteryStatus,
                        shouldTrustTimeTemporarily = realTime.shouldTrustTimeTemporarily,
                        nowTimestamp = nowTimestamp,
                        minuteOfWeek = minuteOfWeek,
                        cache = cache,
                        result = audioPlaybackHandling
                )

                // update used time helper if date does not match
                if (usedTimeUpdateHelper?.date != nowDate) {
                    usedTimeUpdateHelper?.forceCommit(appLogic)
                    usedTimeUpdateHelper = UsedTimeUpdateHelper(nowDate)
                }

                val usedTimeUpdateHelper = usedTimeUpdateHelper!!

                // check times
                fun buildUsedTimesSparseArray(items: SparseArray<UsedTimeItem>, categoryId: String): SparseLongArray {
                    val result = SparseLongArray()

                    for (i in 0..6) {
                        val usedTimesItem = items[i]?.usedMillis ?: 0
                        val timeToAddButNotCommited = usedTimeUpdateHelper.timeToAdd[categoryId] ?: 0

                        result.put(i, usedTimesItem + timeToAddButNotCommited)
                    }

                    return result
                }

                suspend fun getRemainingTime(categoryId: String?): RemainingTime? {
                    categoryId ?: return null

                    val category = categories.find { it.id == categoryId } ?: return null
                    val rules = timeLimitRules.get(category.id).waitForNonNullValue()

                    if (rules.isEmpty()) {
                        return null
                    }

                    val usedTimes = usedTimesOfCategoryAndWeekByFirstDayOfWeek.get(Pair(category.id, nowDate.dayOfEpoch - nowDate.dayOfWeek)).waitForNonNullValue()

                    return RemainingTime.getRemainingTime(
                            nowDate.dayOfWeek,
                            buildUsedTimesSparseArray(usedTimes, categoryId),
                            rules,
                            Math.max(0, category.extraTimeInMillis - (usedTimeUpdateHelper.extraTimeToSubtract.get(categoryId) ?: 0))
                    )
                }

                val remainingTimeForegroundAppChild = getRemainingTime(foregroundAppHandling.categoryId)
                val remainingTimeForegroundAppParent = getRemainingTime(foregroundAppHandling.parentCategoryId)
                val remainingTimeForegroundApp = RemainingTime.min(remainingTimeForegroundAppChild, remainingTimeForegroundAppParent)

                val remainingTimeBackgroundAppChild = getRemainingTime(audioPlaybackHandling.categoryId)
                val remainingTimeBackgroundAppParent = getRemainingTime(audioPlaybackHandling.parentCategoryId)
                val remainingTimeBackgroundApp = RemainingTime.min(remainingTimeBackgroundAppChild, remainingTimeBackgroundAppParent)

                // eventually block
                if (remainingTimeForegroundApp?.hasRemainingTime == false) {
                    foregroundAppHandling.status = BackgroundTaskLogicAppStatus.ShouldBlock
                }

                if (remainingTimeBackgroundApp?.hasRemainingTime == false) {
                    audioPlaybackHandling.status = BackgroundTaskLogicAppStatus.ShouldBlock
                }

                // update times
                val timeToSubtract = Math.min(previousMainLogicExecutionTime, MAX_USED_TIME_PER_ROUND)

                val shouldCountForegroundApp = remainingTimeForegroundApp != null && isScreenOn && remainingTimeForegroundApp.hasRemainingTime
                val shouldCountBackgroundApp = remainingTimeBackgroundApp != null && remainingTimeBackgroundApp.hasRemainingTime

                val categoriesToCount = mutableSetOf<String>()
                val categoriesToCountExtraTime = mutableSetOf<String>()

                if (shouldCountForegroundApp) {
                    remainingTimeForegroundAppChild?.let { remainingTime ->
                        foregroundAppHandling.categoryId?.let { categoryId ->
                            categoriesToCount.add(categoryId)

                            if (remainingTime.usingExtraTime) {
                                categoriesToCountExtraTime.add(categoryId)
                            }
                        }
                    }

                    remainingTimeForegroundAppParent?.let { remainingTime ->
                        foregroundAppHandling.parentCategoryId?.let {
                            categoriesToCount.add(it)

                            if (remainingTime.usingExtraTime) {
                                categoriesToCountExtraTime.add(it)
                            }
                        }
                    }
                }

                if (shouldCountBackgroundApp) {
                    remainingTimeBackgroundAppChild?.let { remainingTime ->
                        audioPlaybackHandling.categoryId?.let {
                            categoriesToCount.add(it)

                            if (remainingTime.usingExtraTime) {
                                categoriesToCountExtraTime.add(it)
                            }
                        }
                    }

                    remainingTimeBackgroundAppParent?.let { remainingTime ->
                        audioPlaybackHandling.parentCategoryId?.let {
                            categoriesToCount.add(it)

                            if (remainingTime.usingExtraTime) {
                                categoriesToCountExtraTime.add(it)
                            }
                        }
                    }
                }

                if (categoriesToCount.isNotEmpty()) {
                    categoriesToCount.forEach { categoryId ->
                        usedTimeUpdateHelper.add(
                                categoryId = categoryId,
                                time = timeToSubtract,
                                includingExtraTime = categoriesToCountExtraTime.contains(categoryId)
                        )
                    }
                }

                usedTimeUpdateHelper.reportCurrentCategories(categoriesToCount)

                if (usedTimeUpdateHelper.shouldDoAutoCommit) {
                    usedTimeUpdateHelper.forceCommit(appLogic)
                }

                // trigger time warnings
                fun eventuallyTriggerTimeWarning(remaining: RemainingTime, categoryId: String?) {
                    val category = categories.find { it.id == categoryId } ?: return
                    val oldRemainingTime = remaining.includingExtraTime
                    val newRemainingTime = oldRemainingTime - timeToSubtract

                    if (oldRemainingTime / (1000 * 60) != newRemainingTime / (1000 * 60)) {
                        // eventually show remaining time warning
                        val roundedNewTime = ((newRemainingTime / (1000 * 60)) + 1) * (1000 * 60)
                        val flagIndex = CategoryTimeWarnings.durationToBitIndex[roundedNewTime]

                        if (flagIndex != null && category.timeWarnings and (1 shl flagIndex) != 0) {
                            appLogic.platformIntegration.showTimeWarningNotification(
                                    title = appLogic.context.getString(R.string.time_warning_not_title, category.title),
                                    text = TimeTextUtil.remaining(roundedNewTime.toInt(), appLogic.context)
                            )
                        }
                    }
                }

                remainingTimeForegroundAppChild?.let { eventuallyTriggerTimeWarning(it, foregroundAppHandling.categoryId) }
                remainingTimeForegroundAppParent?.let { eventuallyTriggerTimeWarning(it, foregroundAppHandling.parentCategoryId) }
                remainingTimeBackgroundAppChild?.let { eventuallyTriggerTimeWarning(it, audioPlaybackHandling.categoryId) }
                remainingTimeBackgroundAppParent?.let { eventuallyTriggerTimeWarning(it, audioPlaybackHandling.parentCategoryId) }

                // show notification
                fun buildStatusMessageWithCurrentAppTitle(
                        text: String,
                        titlePrefix: String = "",
                        titleSuffix: String = "",
                        appPackageName: String?,
                        appActivityToShow: String?
                ) = AppStatusMessage(
                        titlePrefix + appTitleCache.query(appPackageName ?: "invalid") + titleSuffix,
                        text,
                        if (appActivityToShow != null && appPackageName != null) appActivityToShow.removePrefix(appPackageName) else null
                )

                fun getCategoryTitle(categoryId: String?): String = categories.find { it.id == categoryId }?.title ?: categoryId.toString()

                fun buildStatusMessage(
                        handling: BackgroundTaskRestrictionLogicResult,
                        remainingTime: RemainingTime?,
                        suffix: String,
                        appPackageName: String?,
                        appActivityToShow: String?
                ): AppStatusMessage = when (handling.status) {
                    BackgroundTaskLogicAppStatus.ShouldBlock -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_opening_lockscreen),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    BackgroundTaskLogicAppStatus.BackgroundLogicPaused -> AppStatusMessage(
                            title = appLogic.context.getString(R.string.background_logic_paused_title) + suffix,
                            text = appLogic.context.getString(R.string.background_logic_paused_text)
                    )
                    BackgroundTaskLogicAppStatus.InternalWhitelist -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_whitelisted),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    BackgroundTaskLogicAppStatus.TemporarilyAllowed -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_temporarily_allowed),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    BackgroundTaskLogicAppStatus.LimitsDisabled -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_limits_disabled),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    BackgroundTaskLogicAppStatus.AllowedNoTimelimit -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_no_timelimit),
                            titlePrefix = getCategoryTitle(handling.categoryId) + " - ",
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    BackgroundTaskLogicAppStatus.AllowedCountAndCheckTime -> (
                            if (remainingTime?.usingExtraTime == true) {
                                // using extra time
                                buildStatusMessageWithCurrentAppTitle(
                                        text = appLogic.context.getString(R.string.background_logic_using_extra_time, TimeTextUtil.remaining(remainingTime.includingExtraTime.toInt(), appLogic.context)),
                                        titlePrefix = getCategoryTitle(handling.categoryId) + " - ",
                                        titleSuffix = suffix,
                                        appPackageName = appPackageName,
                                        appActivityToShow = appActivityToShow
                                )
                            } else {
                                // using normal contingent
                                buildStatusMessageWithCurrentAppTitle(
                                        text = TimeTextUtil.remaining(remainingTime?.default?.toInt() ?: 0, appLogic.context),
                                        titlePrefix = getCategoryTitle(handling.categoryId) + " - ",
                                        titleSuffix = suffix,
                                        appPackageName = appPackageName,
                                        appActivityToShow = appActivityToShow
                                )
                            }
                            )
                    BackgroundTaskLogicAppStatus.Idle -> AppStatusMessage(
                            appLogic.context.getString(R.string.background_logic_idle_title) + suffix,
                            appLogic.context.getString(R.string.background_logic_idle_text)
                    )
                }

                val showBackgroundStatus = audioPlaybackHandling.status != BackgroundTaskLogicAppStatus.Idle &&
                        audioPlaybackHandling.status != BackgroundTaskLogicAppStatus.ShouldBlock &&
                        audioPlaybackPackageName != foregroundAppPackageName

                if (showBackgroundStatus && nowTimestamp % 6000 >= 3000) {
                    // show notification for music
                    appLogic.platformIntegration.setAppStatusMessage(
                            buildStatusMessage(
                                    handling = audioPlaybackHandling,
                                    remainingTime = remainingTimeBackgroundApp,
                                    suffix = " (2/2)",
                                    appPackageName = audioPlaybackPackageName,
                                    appActivityToShow = null
                            )
                    )
                } else {
                    // show regular notification
                    appLogic.platformIntegration.setAppStatusMessage(
                            buildStatusMessage(
                                    handling = foregroundAppHandling,
                                    remainingTime = remainingTimeForegroundApp,
                                    suffix = if (showBackgroundStatus) " (1/2)" else "",
                                    appPackageName = foregroundAppPackageName,
                                    appActivityToShow = if (activityLevelBlocking) foregroundAppActivityName else null
                            )
                    )
                }

                // handle blocking
                if (foregroundAppHandling.status == BackgroundTaskLogicAppStatus.ShouldBlock) {
                    openLockscreen(foregroundAppPackageName!!, foregroundAppActivityName)
                } else {
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                }

                if (audioPlaybackHandling.status == BackgroundTaskLogicAppStatus.ShouldBlock && audioPlaybackPackageName != null) {
                    appLogic.platformIntegration.muteAudioIfPossible(audioPlaybackPackageName)
                }
            } catch (ex: SecurityException) {
                // this is handled by an other main loop (with a delay)
                lastLoopException.postValue(ex)

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_permission)
                ))
                appLogic.platformIntegration.setShowBlockingOverlay(false)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "exception during running main loop", ex)
                }

                lastLoopException.postValue(ex)

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

    // first time: annoy for 20 seconds; free for 5 minutes
    // second time: annoy for 30 seconds; free for 2 minutes
    // third time: annoy for 1 minute; free for 1 minute
    // then: annoy for 2 minutes; free for 1 minute
    private suspend fun annoyUserOnManipulationLoop() {
        val isManipulated = appLogic.deviceEntryIfEnabled.map { it?.hasActiveManipulationWarning ?: false }
        val enableAnnoy = appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.MANIPULATION_ANNOY_USER)
        val timeLimitNotActive = IsAppInForeground.isRunning.invert()

        var counter = 0
        var globalCounter = 0

        val shouldAnnoyNow = isManipulated.and(enableAnnoy).and(timeLimitNotActive)

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "delay before enabling annoying")
        }

        delay(1000 * 15)

        while (true) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "wait until should annoy")
            }

            shouldAnnoyNow.waitUntilValueMatches { it == true }

            val annoyDurationInSeconds = when (counter) {
                0 -> 20
                1 -> 30
                2 -> 60
                else -> 120
            }

            val freeDurationInSeconds = when (counter) {
                0 -> 5 * 60
                1 -> 2 * 60
                else -> 60
            }

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "annoy for $annoyDurationInSeconds seconds; free for $freeDurationInSeconds seconds")
            }

            appLogic.platformIntegration.showAnnoyScreen(annoyDurationInSeconds.toLong())

            counter++
            globalCounter++

            // reset counter if there was nothing for one hour
            val globalCounterBackup = globalCounter
            appLogic.timeApi.runDelayed(Runnable {
                if (globalCounter == globalCounterBackup) {
                    counter = 0
                }
            }, 1000 * 60 * 60 /* 1 hour */)

            // wait before annoying next time
            delay((annoyDurationInSeconds + freeDurationInSeconds) * 1000L)
        }
    }
}
