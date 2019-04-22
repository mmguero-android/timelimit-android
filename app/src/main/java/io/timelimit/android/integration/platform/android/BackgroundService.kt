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
package io.timelimit.android.integration.platform.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.integration.platform.AppStatusMessage
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SignOutAtDeviceAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.MainActivity

class BackgroundService: Service() {
    companion object {
        private const val ACTION = "a"
        private const val ACTION_SET_NOTIFICATION = "a"
        private const val ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS = "b"
        private const val ACTION_SWITCH_TO_DEFAULT_USER = "c"
        private const val EXTRA_NOTIFICATION = "b"

        fun setStatusMessage(status: AppStatusMessage?, context: Context) {
            val intent = Intent(context, BackgroundService::class.java)

            if (status != null) {
                ContextCompat.startForegroundService(
                        context,
                        intent
                                .putExtra(ACTION, ACTION_SET_NOTIFICATION)
                                .putExtra(EXTRA_NOTIFICATION, status)
                )
            } else {
                context.stopService(intent)
            }
        }

        fun prepareRevokeTemporarilyAllowed(context: Context) = Intent(context, BackgroundService::class.java)
                .putExtra(ACTION, ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS)

        fun prepareSwitchToDefaultUser(context: Context) = Intent(context, BackgroundService::class.java)
                .putExtra(ACTION, ACTION_SWITCH_TO_DEFAULT_USER)
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var didPostNotification = false

    override fun onCreate() {
        super.onCreate()

        // init the app logic if not yet done
        DefaultAppLogic.with(this)

        // create the channel
        NotificationChannels.createAppStatusChannel(notificationManager, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.getStringExtra(ACTION)

            if (action == ACTION_SET_NOTIFICATION) {
                val appStatusMessage = intent.getParcelableExtra<AppStatusMessage>(EXTRA_NOTIFICATION)

                val openAppIntent = PendingIntent.getActivity(
                        this,
                        PendingIntentIds.OPEN_MAIN_APP,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(this, NotificationChannels.APP_STATUS)
                        .setSmallIcon(R.drawable.ic_stat_timelapse)
                        .setContentTitle(appStatusMessage.title)
                        .setContentText(appStatusMessage.text)
                        .setSubText(appStatusMessage.subtext)
                        .setContentIntent(openAppIntent)
                        .setWhen(0)
                        .setShowWhen(false)
                        .setSound(null)
                        .setOnlyAlertOnce(true)
                        .setLocalOnly(true)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .let { builder ->
                            if (appStatusMessage.showSwitchToDefaultUserOption) {
                                builder.addAction(
                                        NotificationCompat.Action.Builder(
                                                R.drawable.ic_account_circle_black_24dp,
                                                getString(R.string.manage_device_default_user_switch_btn),
                                                PendingIntent.getService(
                                                        this@BackgroundService,
                                                        PendingIntentIds.SWITCH_TO_DEFAULT_USER,
                                                        prepareSwitchToDefaultUser(this@BackgroundService),
                                                        PendingIntent.FLAG_UPDATE_CURRENT
                                                )
                                        ).build()
                                )
                            }

                            builder
                        }
                        .build()

                if (didPostNotification) {
                    notificationManager.notify(NotificationIds.APP_STATUS, notification)
                } else {
                    startForeground(NotificationIds.APP_STATUS, notification)
                    didPostNotification = true
                }
            } else if (action == ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS) {
                runAsync {
                    DefaultAppLogic.with(this@BackgroundService).backgroundTaskLogic.resetTemporarilyAllowedApps()
                }
            } else if (action == ACTION_SWITCH_TO_DEFAULT_USER) {
                runAsync {
                    val logic = DefaultAppLogic.with(this@BackgroundService)

                    if (logic.fullVersion.shouldProvideFullVersionFunctions.waitForNonNullValue()) {
                        ApplyActionUtil.applyAppLogicAction(
                                appLogic = logic,
                                action = SignOutAtDeviceAction,
                                ignoreIfDeviceIsNotConfigured = true
                        )
                    } else {
                        Toast.makeText(this@BackgroundService, R.string.purchase_required_dialog_title, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        stopForeground(true)
        didPostNotification = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        throw NotImplementedError()
    }
}
