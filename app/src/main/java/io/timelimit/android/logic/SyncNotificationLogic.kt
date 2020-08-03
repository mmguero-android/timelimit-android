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
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Notification
import io.timelimit.android.data.model.NotificationTypes
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.ui.notification.NotificationAreaSync

class SyncNotificationLogic (private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "SyncNotificationLogic"
    }

    private suspend fun syncUI() {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "sync ui")
        }

        val backgroundSyncEnabled = appLogic.database.config().getEnableBackgroundSyncAsync().waitForNonNullValue()

        if (!backgroundSyncEnabled) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "cancel because background sync disabled")
            }

            return
        }

        NotificationAreaSync.sync(appLogic.context, appLogic.database)
    }

    suspend fun sync(forceUiSync: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "sync(forceUiSync = $forceUiSync)")
        }

        // get current warnings
        val now = System.currentTimeMillis()
        val ( devices, fullVersionEndTime, isChildDevice ) = Threads.database.executeAndWait {
            val ownId = appLogic.database.config().getOwnDeviceIdSync()

            val otherDevices = appLogic.database.device().getAllDevicesSync().filter { it.id != ownId }
            val fullVersionEndTime = if (ownId != null && appLogic.database.config().getDeviceAuthTokenSync().isNotEmpty())
                appLogic.database.config().getFullVersionUntilSync()
            else
                null

            val deviceEntry = if (ownId != null)
                appLogic.database.device().getDeviceByIdSync(ownId)
            else
                null

            val userEntry = if (deviceEntry != null)
                appLogic.database.user().getUserByIdSync(deviceEntry.currentUserId)
            else
                null

            val isChildDevice = userEntry?.type == UserType.Child

            Triple(otherDevices, fullVersionEndTime, isChildDevice)
        }

        val manipulatedDevices = devices.filter { it.hasAnyManipulation || it.didReportUninstall }.map { it.id }
        val outdatedDevices = devices.filter { it.currentAppVersion < BuildConfig.VERSION_CODE }.map { it.id }

        val currentWarnings = mutableSetOf<Pair<Int, String>>() // type + id
        currentWarnings.addAll(manipulatedDevices.map { NotificationTypes.MANIPULATION to it })
        currentWarnings.addAll(outdatedDevices.map { NotificationTypes.UPDATE_MISSING to it })

        if (fullVersionEndTime != null && fullVersionEndTime != 1L && fullVersionEndTime > now && !isChildDevice) {
            currentWarnings.add(NotificationTypes.PREMIUM_EXPIRES to "")
        }

        val savedWarnings = Threads.database.executeAndWait {
            appLogic.database.notification().getAllNotifications()
        }

        val newNotifications = mutableListOf<Notification>()
        val notificationsToRemove = mutableListOf<Notification>()
        notificationsToRemove.addAll(savedWarnings)

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "current warnings: $currentWarnings")
        }

        currentWarnings.forEach { warning ->
            val oldEntry = notificationsToRemove.find { it.type == warning.first && it.id == warning.second }

            if (warning.first == NotificationTypes.PREMIUM_EXPIRES) {
                val notifyTime = fullVersionEndTime!! - 1000 * 60 * 60 * 24 * 3   // wait until 3 days before its end time

                if (oldEntry != null) {
                    notificationsToRemove.remove(oldEntry)
                }

                if (oldEntry == null || oldEntry.firstNotifyTime != notifyTime) {
                    newNotifications.add(Notification(
                            type = NotificationTypes.PREMIUM_EXPIRES,
                            id = "",
                            isDismissed = false,
                            firstNotifyTime = notifyTime
                    ))
                }
            } else if (oldEntry != null) {
                // nothing to do
                notificationsToRemove.remove(oldEntry)
            } else {
                newNotifications.add(Notification(
                        type = warning.first,
                        id = warning.second,
                        isDismissed = false,
                        firstNotifyTime = when (warning.first) {
                            NotificationTypes.UPDATE_MISSING -> now + 1000 * 60 * 60 * 24   // wait one day
                            else -> now + 1000 * 15                                         // wait 15 seconds
                        }
                ))
            }
        }

        if (newNotifications.isNotEmpty() or notificationsToRemove.isNotEmpty()) {
            Threads.database.executeAndWait {
                appLogic.database.runInTransaction {
                    if (notificationsToRemove.isNotEmpty()) {
                        appLogic.database.notification().removeNotificationSync(notificationsToRemove)
                    }

                    if (newNotifications.isNotEmpty()) {
                        appLogic.database.notification().addNotificationsSync(newNotifications)
                    }
                }
            }
        }

        if (notificationsToRemove.isNotEmpty()) {
            notificationsToRemove.forEach {
                NotificationAreaSync.dismissNotification(it.type, it.id, appLogic.context)
            }
        }

        if (forceUiSync || newNotifications.isNotEmpty()) {
            syncUI()
        }
    }

    init {
        runAsync {
            sync(forceUiSync = true)
        }
    }
}