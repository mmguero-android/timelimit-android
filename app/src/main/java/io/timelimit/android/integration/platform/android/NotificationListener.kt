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
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.*

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class NotificationListener: NotificationListenerService() {
    companion object {
        private const val LOG_TAG = "NotificationListenerLog"
        private val SUPPORTS_HIDING_ONGOING_NOTIFICATIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(this) }
    private val blockingReasonUtil: BlockingReasonUtil by lazy { BlockingReasonUtil(appLogic) }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val queryAppTitleCache: QueryAppTitleCache by lazy { QueryAppTitleCache(appLogic.platformIntegration) }
    private val lastOngoingNotificationHidden = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        NotificationChannels.createNotificationChannels(notificationManager, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, sbn.packageName)
        }

        runAsync {
            val reason = shouldRemoveNotification(sbn)

            if (reason == BlockingReason.None) {
                if (sbn.isOngoing) {
                    lastOngoingNotificationHidden.remove(sbn.packageName)
                }
            } else {
                appLogic.platformIntegration.muteAudioIfPossible(sbn.packageName)

                val success = try {
                    if (sbn.isOngoing && SUPPORTS_HIDING_ONGOING_NOTIFICATIONS) {
                        // only snooze for 5 seconds to show it again soon
                        snoozeNotification(sbn.key, 5000)

                        if (!lastOngoingNotificationHidden.add(sbn.packageName)) {
                            // skip showing again a notification that it was blocked
                            return@runAsync
                        }
                    } else {
                        cancelNotification(sbn.key)
                    }

                    true
                } catch (ex: SecurityException) {
                    // this occurs when the notification access is revoked
                    // while this function is running

                    false
                }

                notificationManager.notify(
                        sbn.packageName,
                        NotificationIds.NOTIFICATION_BLOCKED,
                        NotificationCompat.Builder(this@NotificationListener, NotificationChannels.BLOCKED_NOTIFICATIONS_NOTIFICATION)
                                .setDefaults(NotificationCompat.DEFAULT_ALL)
                                .setSmallIcon(R.drawable.ic_stat_block)
                                .setContentTitle(
                                        if (success)
                                            getString(R.string.notification_filter_not_blocked_title)
                                        else
                                            getString(R.string.notification_filter_blocking_failed_title)
                                )
                                .setContentText(
                                        queryAppTitleCache.query(sbn.packageName) +
                                                " - " +
                                                when (reason) {
                                                    BlockingReason.NotPartOfAnCategory -> getString(R.string.lock_reason_short_no_category)
                                                    BlockingReason.TemporarilyBlocked -> getString(R.string.lock_reason_short_temporarily_blocked)
                                                    BlockingReason.TimeOver -> getString(R.string.lock_reason_short_time_over)
                                                    BlockingReason.TimeOverExtraTimeCanBeUsedLater -> getString(R.string.lock_reason_short_time_over)
                                                    BlockingReason.BlockedAtThisTime -> getString(R.string.lock_reason_short_blocked_time_area)
                                                    BlockingReason.MissingNetworkTime -> getString(R.string.lock_reason_short_missing_network_time)
                                                    BlockingReason.RequiresCurrentDevice -> getString(R.string.lock_reason_short_requires_current_device)
                                                    BlockingReason.NotificationsAreBlocked -> getString(R.string.lock_reason_short_notification_blocking)
                                                    BlockingReason.BatteryLimit -> getString(R.string.lock_reason_short_battery_limit)
                                                    BlockingReason.SessionDurationLimit -> getString(R.string.lock_reason_short_session_duration)
                                                    BlockingReason.None -> throw IllegalStateException()
                                                }
                                )
                                .setLocalOnly(true)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .build()
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)

        // not interesting but required for old android versions
    }

    private suspend fun shouldRemoveNotification(sbn: StatusBarNotification): BlockingReason {
        if (sbn.packageName == packageName) {
            return BlockingReason.None
        }

        if (sbn.isOngoing && (!SUPPORTS_HIDING_ONGOING_NOTIFICATIONS)) {
            return BlockingReason.None
        }

        val blockingReason = blockingReasonUtil.getBlockingReason(
                packageName = sbn.packageName,
                activityName = null
        ).waitForNonNullValue()

        if (blockingReason.areNotificationsBlocked) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "blocking notification of ${sbn.packageName} because notifications are blocked")
            }

            return BlockingReason.NotificationsAreBlocked
        }

        return when (blockingReason) {
            is NoBlockingReason -> BlockingReason.None
            is BlockedReasonDetails -> {
                if (isSystemApp(sbn.packageName) && blockingReason.reason == BlockingReason.NotPartOfAnCategory) {
                    return BlockingReason.None
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "blocking notification of ${sbn.packageName} because ${blockingReason.reason}")
                }

                return blockingReason.reason
            }
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            return appInfo.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM
        } catch (ex: PackageManager.NameNotFoundException) {
            return false
        }
    }
}