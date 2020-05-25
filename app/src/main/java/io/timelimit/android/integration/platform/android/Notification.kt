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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.timelimit.android.R

object NotificationIds {
    const val APP_STATUS = 1
    const val NOTIFICATION_BLOCKED = 2
    const val REVOKE_TEMPORARILY_ALLOWED_APPS = 3
    const val APP_RESET = 4
    const val USER_NOTIFICATION = 5
    const val TIME_WARNING = 6
    const val LOCAL_UPDATE_NOTIFICATION = 7
}

object NotificationChannels {
    const val APP_STATUS = "app status"
    const val BLOCKED_NOTIFICATIONS_NOTIFICATION = "notification blocked notification"
    const val MANIPULATION_WARNING = "manipulation warning"
    const val UPDATE_NOTIFICATION = "update notification"
    const val TIME_WARNING = "time warning"
    const val PREMIUM_EXPIRES_NOTIFICATION = "premium expires"

    private fun createAppStatusChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            APP_STATUS,
                            context.getString(R.string.notification_channel_app_status_title),
                            NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notification_channel_app_status_description)
                        enableLights(false)
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
                    }
            )
        }
    }

    private fun createBlockedNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.BLOCKED_NOTIFICATIONS_NOTIFICATION,
                            context.getString(R.string.notification_channel_blocked_notification_title),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.notification_channel_blocked_notification_text)
                    }
            )
        }
    }

    private fun createManipulationNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.MANIPULATION_WARNING,
                            context.getString(R.string.notification_channel_manipulation_title),
                            NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notification_channel_manipulation_text)
                    }
            )
        }
    }

    private fun createUpdateNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.UPDATE_NOTIFICATION,
                            context.getString(R.string.notification_channel_update_title),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.notification_channel_update_text)
                        enableLights(false)
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        }
    }

    private fun createTimeWarningsNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.TIME_WARNING,
                            context.getString(R.string.notification_channel_time_warning_title),
                            NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notification_channel_time_warning_text)
                    }
            )
        }
    }

    fun createPremiumExpiresChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.PREMIUM_EXPIRES_NOTIFICATION,
                            context.getString(R.string.notification_channel_premium_expires_title),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.notification_channel_premium_expires_text)
                        enableLights(false)
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        }
    }

    fun createNotificationChannels(notificationManager: NotificationManager, context: Context) {
        createAppStatusChannel(notificationManager, context)
        createBlockedNotificationChannel(notificationManager, context)
        createManipulationNotificationChannel(notificationManager, context)
        createUpdateNotificationChannel(notificationManager, context)
        createTimeWarningsNotificationChannel(notificationManager, context)
        createPremiumExpiresChannel(notificationManager, context)
    }
}

object PendingIntentIds {
    const val OPEN_MAIN_APP = 1
    const val REVOKE_TEMPORARILY_ALLOWED = 2
    const val SWITCH_TO_DEFAULT_USER = 3
    const val SYNC_NOTIFICATIONS = 4
    const val UPDATE_STATUS = 5
    const val OPEN_UPDATER = 6
    val DYNAMIC_NOTIFICATION_RANGE = 100..10000
}
