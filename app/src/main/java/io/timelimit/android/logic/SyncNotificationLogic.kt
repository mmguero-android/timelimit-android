package io.timelimit.android.logic

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Notification
import io.timelimit.android.data.model.NotificationTypes
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
        val devices = Threads.database.executeAndWait {
            val ownId = appLogic.database.config().getOwnDeviceIdSync()

            appLogic.database.device().getAllDevicesSync().filter { it.id != ownId }
        }

        val manipulatedDevices = devices.filter { it.hasAnyManipulation || it.didReportUninstall }.map { it.id }
        val outdatedDevices = devices.filter { it.currentAppVersion < BuildConfig.VERSION_CODE }.map { it.id }

        val currentWarnings = mutableSetOf<Pair<Int, String>>() // type + id
        currentWarnings.addAll(manipulatedDevices.map { NotificationTypes.MANIPULATION to it })
        currentWarnings.addAll(outdatedDevices.map { NotificationTypes.UPDATE_MISSING to it })

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

            if (oldEntry != null) {
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
                appLogic.database.beginTransaction()
                try {
                    if (newNotifications.isNotEmpty()) {
                        appLogic.database.notification().addNotificationsSync(newNotifications)
                    }

                    if (notificationsToRemove.isNotEmpty()) {
                        appLogic.database.notification().removeNotificationSync(notificationsToRemove)
                    }

                    appLogic.database.setTransactionSuccessful()
                } finally {
                    appLogic.database.endTransaction()
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