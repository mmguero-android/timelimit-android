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

package io.timelimit.android.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.integration.platform.android.NotificationChannels
import io.timelimit.android.integration.platform.android.NotificationIds
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.update.UpdateActivity
import io.timelimit.android.work.CheckUpdateWorker

object UpdateUtil {
    fun setEnableChecks(context: Context, enable: Boolean) {
        if (enable) {
            enableChecks(context)
        } else {
            disableChecks(context)
        }
    }

    fun enableChecks(context: Context) {
        val database = DefaultAppLogic.with(context).database

        Threads.database.execute {
            database.config().setUpdatesEnabledSync(true)
        }

        CheckUpdateWorker.schedule()
    }

    fun disableChecks(context: Context) {
        val database = DefaultAppLogic.with(context).database

        Threads.database.execute {
            database.config().setUpdatesEnabledSync(false)
        }

        CheckUpdateWorker.deschedule()
    }

    suspend fun doUpdateCheck(context: Context, database: Database, enableNotifications: Boolean): Boolean {
        val status = UpdateIntegration.getUpdateStatus(context)

        Threads.database.execute { database.config().setUpdateStatus(status) }

        return if (status.versionCode > BuildConfig.VERSION_CODE) {
            if (enableNotifications) {
                notifyAboutUpdate(context)
            }

            true
        } else {
            UpdateIntegration.deleteUpdateFile(context)

            false
        }
    }

    private fun notifyAboutUpdate(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openUpdaterIntent = PendingIntent.getActivity(
                context,
                PendingIntentIds.OPEN_UPDATER,
                Intent(context, UpdateActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationManager.notify(
                NotificationIds.LOCAL_UPDATE_NOTIFICATION,
                NotificationCompat.Builder(context, NotificationChannels.UPDATE_NOTIFICATION)
                        .setSmallIcon(R.drawable.ic_stat_timelapse)
                        .setContentTitle(context.getString(R.string.update_notification_title))
                        .setContentText(context.getString(R.string.update_notification_text))
                        .setContentIntent(openUpdaterIntent)
                        .setWhen(0)
                        .setShowWhen(false)
                        .setAutoCancel(false)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOnlyAlertOnce(true)
                        .build()
        )
    }
}