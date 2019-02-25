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

class SyncInBackgroundWorker(val context: Context, workerParameters: WorkerParameters): CoroutineWorker(context, workerParameters) {
    companion object {
        private const val LOG_TAG = "SyncInBackground"
        private const val UNIQUE_WORK_NAME = "SyncInBackgroundWork"

        fun enqueueDelayed() {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "enqueueDelayed")
            }

            WorkManager.getInstance().beginUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequest.Builder(SyncInBackgroundWorker::class.java)
                            .setInitialDelay(10, TimeUnit.SECONDS)
                            .setConstraints(
                                    Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .setRequiresBatteryNotLow(true)
                                            .build()
                            )
                            .build()
            ).enqueue()
        }

        fun deschedule() {
            WorkManager.getInstance().cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "upload actions now")
        }

        return withContext (Dispatchers.Main) {
            try {
                DefaultAppLogic.with(context).syncUtil.doSync()

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "done uploading actions")
                }

                Result.success()
            } catch (ex: SyncingDisabledException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "upload actions failed because syncing is disabled")
                }

                Result.failure()
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "upload actions failed", ex)
                }

                Result.retry()
            }
        }
    }
}
