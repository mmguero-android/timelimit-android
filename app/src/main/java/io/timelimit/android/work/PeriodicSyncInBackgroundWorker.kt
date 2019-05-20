package io.timelimit.android.work

import android.content.Context
import android.util.Log
import androidx.work.*
import io.timelimit.android.BuildConfig
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.SyncingDisabledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PeriodicSyncInBackgroundWorker(private val context: Context, workerParameters: WorkerParameters): CoroutineWorker(context, workerParameters) {
    companion object {
        private const val LOG_TAG = "PeriodicBackgroundSync"
        private const val UNIQUE_WORK_NAME = "PeriodicSyncInBackgroundWorker"

        fun enable() {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "enable()")
            }

            WorkManager.getInstance().enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    PeriodicWorkRequestBuilder<PeriodicSyncInBackgroundWorker>(1, TimeUnit.HOURS)
                            .setConstraints(
                                    Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .setRequiresBatteryNotLow(true)
                                            .build()
                            )
                            .build()
            )
        }

        fun disable() {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "disable()")
            }

            WorkManager.getInstance().cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "do sync now")
        }

        return withContext (Dispatchers.Main) {
            try {
                DefaultAppLogic.with(context).syncUtil.doSync()

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "sync done")
                }

                Result.success()
            } catch (ex: SyncingDisabledException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "failed because syncing is disabled")
                }

                Result.failure()
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "background sync failed", ex)
                }

                Result.retry()
            }
        }
    }
}