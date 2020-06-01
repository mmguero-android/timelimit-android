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
package io.timelimit.android.sync

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.ServerLogic
import io.timelimit.android.sync.actions.apply.UploadActionsUtil
import io.timelimit.android.sync.network.ClientDataStatus
import io.timelimit.android.sync.network.api.UnauthorizedHttpError
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.work.SyncInBackgroundWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*

class SyncUtil (private val logic: AppLogic) {
    companion object {
        private val random: Random by lazy { Random() }
        private const val LOG_TAG = "SyncUtil"
        private val appVersion = BuildConfig.VERSION_CODE.toString()
    }

    private val upload = UploadActionsUtil(
            database = logic.database,
            syncConflictHandler = {
                Toast.makeText(logic.context, R.string.sync_status_conflict_toast, Toast.LENGTH_SHORT).show()
            }
    )

    private val importantSyncRequested = MutableLiveData<Boolean>().apply { value = false }
    private val unimportantSyncRequested = MutableLiveData<Boolean>().apply { value = false }
    private val veryUnimportantSyncRequested = MutableLiveData<Boolean>().apply { value = false }

    val isNormalSyncRequested = importantSyncRequested.or(unimportantSyncRequested)
    val isVeryUnimportantSyncRequested = veryUnimportantSyncRequested.castDown()

    private val lastSync = MutableLiveData<Long>().apply { value = 0 }
    private val isSyncingLock = Mutex()
    private val lastSyncLongAgo = object: LiveData<Boolean>() {
        private val setToTrueRunnable = Runnable {
            value = true
        }

        init {
            value = true

            lastSync.ignoreUnchanged().observeForever {
                _ ->

                logic.timeApi.cancelScheduledAction(setToTrueRunnable)
                logic.timeApi.runDelayed(setToTrueRunnable, 1000 * 60 * 10) // 10 minutes

                value = false
            }
        }
    }

    private val shouldSyncBase = logic.isConnected.and(logic.enable)
    private val shouldSyncImportant = importantSyncRequested
    private val shouldSyncUnimportant = unimportantSyncRequested.and(IsAppInForeground.isRunning)
    private val shouldSyncVeryUnimportant = veryUnimportantSyncRequested.and(lastSyncLongAgo)

    private val shouldSync = shouldSyncBase.and(
            shouldSyncImportant.or(shouldSyncUnimportant).or(shouldSyncVeryUnimportant)
    )

    private val lastSyncExceptionInternal = MutableLiveData<Exception?>().apply { postValue(null) }
    val lastSyncException = lastSyncExceptionInternal.castDown()
    val isSyncing = liveDataFromFunction (pollInterval = 100) { isSyncingLock.isLocked }

    init {
        runAsync {
            wipeCacheIfUpdated()

            while (true) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "wait until sync should happen")
                }

                shouldSync.waitUntilValueMatches { it == true }
                importantSyncRequested.value = false
                unimportantSyncRequested.value = false
                veryUnimportantSyncRequested.value = false

                try {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "do sync now")
                    }

                    doSync()

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "sync done - force pause")
                    }

                    lastSync.value = logic.timeApi.getCurrentUptimeInMillis()

                    SyncInBackgroundWorker.deschedule()
                    lastSyncExceptionInternal.postValue(null)

                    // wait 2 to 3 seconds before any next sync (debounce)
                    logic.timeApi.sleep((2 * 1000 + random.nextInt(1000)).toLong())
                } catch (ex: Exception) {
                    // wait 10 to 15 seconds before retrying

                    lastSyncExceptionInternal.postValue(ex)

                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "sync failed", ex)
                    }

                    importantSyncRequested.value = true
                    logic.timeApi.sleep(10 * 1000 + random.nextInt(5 * 1000).toLong())
                }
            }
        }
    }

    fun requestImportantSync(enqueueIfOffline: Boolean = false) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "request important sync")
        }

        importantSyncRequested.postValue(true)

        if (enqueueIfOffline) {
            SyncInBackgroundWorker.enqueueDelayed()
        }
    }

    fun requestUnimportantSync() {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "request unimportant sync")
        }

        unimportantSyncRequested.postValue(true)
    }

    suspend fun requestImportantSyncAndWait() {
        withContext (Dispatchers.Main) {
            importantSyncRequested.value = true

            importantSyncRequested.waitUntilValueMatches { it == false }
        }
    }

    fun requestVeryUnimportantSync() {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "request very unimportant sync")
        }

        veryUnimportantSyncRequested.postValue(true)
    }

    suspend fun doSync() {
        isSyncingLock.withLock {
            val server = logic.serverLogic.getServerConfigCoroutine()

            if (!server.hasAuthToken) {
                // no sync enabled

                throw SyncingDisabledException()
            }

            try {
                pushActions(server = server)
                pullStatus(server = server)

                logic.syncNotificationLogic.sync(forceUiSync = false)
            } catch (ex: UnauthorizedHttpError) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "authentication error, check if reset should happen")
                }

                if (server.api.isDeviceRemoved(server.deviceAuthToken)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "reset should happen")
                    }

                    logic.appSetupLogic.dangerousRemoteReset()
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "no reset should happen")
                    }
                }
            }
        }
    }

    private suspend fun pushActions(server: ServerLogic.ServerConfig) {
        upload.uploadActions(server)
    }

    private suspend fun pullStatus(server: ServerLogic.ServerConfig) {
        val currentStatus = ClientDataStatus.getClientDataStatusAsync(logic.database)
        val serverResponse = server.api.pullChanges(server.deviceAuthToken, currentStatus)
        ApplyServerDataStatus.applyServerDataStatusCoroutine(serverResponse, logic.database, logic.platformIntegration)
    }

    private suspend fun wipeCacheIfUpdated() {
        Threads.database.executeAndWait {
            logic.database.runInTransaction {
                if (logic.database.config().getLastAppVersionWhichSynced() != appVersion) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "app update -> wipe cache")
                    }

                    UploadActionsUtil.deleteAllVersionNumbersSync(logic.database)

                    logic.database.config().setLastAppVersionWhichSynced(appVersion)
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "no app update -> keep cache")
                    }
                }
            }
        }
    }
}

class SyncingDisabledException: RuntimeException()
