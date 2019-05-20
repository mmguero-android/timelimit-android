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
package io.timelimit.android.ui.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.Notification
import io.timelimit.android.data.model.NotificationTypes
import io.timelimit.android.integration.platform.android.BackgroundService
import io.timelimit.android.integration.platform.android.NotificationChannels
import io.timelimit.android.integration.platform.android.NotificationIds
import io.timelimit.android.integration.platform.android.PendingIntentIds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

object NotificationAreaSync {
    private const val LOG_TAG = "NotificationAreaSync"

    private val syncLock = Mutex()

    suspend fun sync(context: Context, database: Database) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "sync()")
        }

        syncLock.withLock {
            val now = System.currentTimeMillis()
            val notifications = Threads.database.executeAndWait {
                database.notification().getVisibleNotifications()
            }.filter { it.firstNotifyTime <= now }

            notifications.forEach { notification -> post(notification, database, context) }

            scheduleNextNotificationPosting(context, database, now)
        }
    }

    fun saveNotificationDismissed(database: Database, type: Int, id: String) {
        Threads.database.execute {
            database.notification().setItemDismissed(type, id)
        }
    }

    fun dismissNotification(type: Int, id: String, context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationTag = type.toString() + id

        notificationManager.cancel(notificationTag, NotificationIds.USER_NOTIFICATION)
    }

    private suspend fun post(notification: Notification, database: Database, context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannels.createNotificationChannels(notificationManager, context)

        val notificationTag = notification.type.toString() + notification.id
        val pendingIntentId = PendingIntentIds.DYNAMIC_NOTIFICATION_RANGE.random(Random(notificationTag.hashCode()))
        val markReadIntent = PendingIntent.getService(
                context,
                pendingIntentId + 1,
                BackgroundService.prepareDismissNotification(context, notification.type, notification.id),
                PendingIntent.FLAG_CANCEL_CURRENT
        )
        val openAppIntent = BackgroundService.getOpenAppIntent(context)

        suspend fun getDeviceName(deviceId: String) = Threads.database.executeAndWait {
            database.device().getDeviceByIdSync(deviceId)?.name ?: "???"
        }

        if (notification.type == NotificationTypes.MANIPULATION) {
            notificationManager.notify(
                    notificationTag,
                    NotificationIds.USER_NOTIFICATION,
                    NotificationCompat.Builder(context, NotificationChannels.MANIPULATION_WARNING)
                            .setSmallIcon(R.drawable.ic_stat_timelapse)
                            .setContentTitle(context.getString(R.string.notification_manipulation_title, getDeviceName(notification.id)))
                            .setContentText(context.getString(R.string.notification_generic_text))
                            .setContentIntent(openAppIntent)
                            .setDeleteIntent(markReadIntent)
                            .setWhen(0)
                            .setShowWhen(false)
                            .setAutoCancel(false)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setOnlyAlertOnce(true)
                            .build()
            )
        } else if (notification.type == NotificationTypes.UPDATE_MISSING) {
            notificationManager.notify(
                    notificationTag,
                    NotificationIds.USER_NOTIFICATION,
                    NotificationCompat.Builder(context, NotificationChannels.UPDATE_NOTIFICATION)
                            .setSmallIcon(R.drawable.ic_stat_timelapse)
                            .setContentTitle(context.getString(R.string.notification_update_title, getDeviceName(notification.id)))
                            .setContentText(context.getString(R.string.notification_generic_text))
                            .setContentIntent(openAppIntent)
                            .setDeleteIntent(markReadIntent)
                            .setWhen(0)
                            .setShowWhen(false)
                            .setAutoCancel(false)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setOnlyAlertOnce(true)
                            .build()
            )
        } else {
            // ignore
        }
    }

    private suspend fun scheduleNextNotificationPosting(context: Context, database: Database, now: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val syncIntent = BackgroundService.getSyncNotificationsPendingIntent(context)
        val nextNotification = Threads.database.executeAndWait { database.notification().getNextVisibleNotifications(now) }

        alarmManager.cancel(syncIntent)

        if (nextNotification != null) {
            alarmManager.set(
                    AlarmManager.RTC,
                    nextNotification.firstNotifyTime,
                    syncIntent
            )
        }
    }
}