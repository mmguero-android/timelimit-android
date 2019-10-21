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
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SignOutAtDeviceAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.notification.NotificationAreaSync

class BackgroundActionService: Service() {
    companion object {
        private const val ACTION = "a"
        private const val ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS = "b"
        private const val ACTION_SWITCH_TO_DEFAULT_USER = "c"
        private const val ACTION_DISMISS_NOTIFICATION = "d"
        private const val ACTION_UPDATE_NOTIFICATION = "e"
        private const val EXTRA_NOTIFICATION_TYPE = "c"
        private const val EXTRA_NOTIFICATION_ID = "d"

        fun prepareRevokeTemporarilyAllowed(context: Context) = Intent(context, BackgroundActionService::class.java)
                .putExtra(ACTION, ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS)

        fun prepareSwitchToDefaultUser(context: Context) = Intent(context, BackgroundActionService::class.java)
                .putExtra(ACTION, ACTION_SWITCH_TO_DEFAULT_USER)

        fun prepareDismissNotification(context: Context, type: Int, id: String) = Intent(context, BackgroundActionService::class.java)
                .putExtra(ACTION, ACTION_DISMISS_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_TYPE, type)
                .putExtra(EXTRA_NOTIFICATION_ID, id)

        fun getOpenAppIntent(context: Context) = PendingIntent.getActivity(
                context,
                PendingIntentIds.OPEN_MAIN_APP,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun getSyncNotificationsPendingIntent(context: Context) = PendingIntent.getService(
                context,
                PendingIntentIds.SYNC_NOTIFICATIONS,
                Intent(context, BackgroundActionService::class.java)
                        .putExtra(ACTION, ACTION_UPDATE_NOTIFICATION),
                PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()

        // init the app logic if not yet done
        DefaultAppLogic.with(this)

        // create the channel
        NotificationChannels.createNotificationChannels(notificationManager, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.getStringExtra(ACTION)

            if (action == ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS) {
                runAsync {
                    DefaultAppLogic.with(this@BackgroundActionService).backgroundTaskLogic.resetTemporarilyAllowedApps()
                }
            } else if (action == ACTION_SWITCH_TO_DEFAULT_USER) {
                runAsync {
                    val logic = DefaultAppLogic.with(this@BackgroundActionService)

                    if (logic.fullVersion.shouldProvideFullVersionFunctions.waitForNonNullValue()) {
                        ApplyActionUtil.applyAppLogicAction(
                                appLogic = logic,
                                action = SignOutAtDeviceAction,
                                ignoreIfDeviceIsNotConfigured = true
                        )
                    } else {
                        Toast.makeText(this@BackgroundActionService, R.string.purchase_required_dialog_title, Toast.LENGTH_LONG).show()
                    }
                }
            } else if (action == ACTION_DISMISS_NOTIFICATION) {
                runAsync {
                    NotificationAreaSync.saveNotificationDismissed(
                            DefaultAppLogic.with(this@BackgroundActionService).database,
                            intent.getIntExtra(EXTRA_NOTIFICATION_TYPE, 0),
                            intent.getStringExtra(EXTRA_NOTIFICATION_ID)!!
                    )
                }
            } else if (action == ACTION_UPDATE_NOTIFICATION) {
                runAsync {
                    NotificationAreaSync.sync(
                            this@BackgroundActionService,
                            DefaultAppLogic.with(this@BackgroundActionService).database
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        throw NotImplementedError()
    }
}
