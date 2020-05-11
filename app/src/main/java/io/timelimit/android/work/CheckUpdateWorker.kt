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

package io.timelimit.android.work

import android.content.Context
import androidx.work.*
import io.timelimit.android.update.UpdateIntegration
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.update.UpdateUtil
import java.util.concurrent.TimeUnit

class CheckUpdateWorker(private val context: Context, workerParameters: WorkerParameters): CoroutineWorker(context, workerParameters) {
    companion object {
        private const val UNIQUE_WORK_NAME = "CheckUpdateWorker"

        fun schedule() {
            WorkManager.getInstance().enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<CheckUpdateWorker>(1, TimeUnit.DAYS)
                            .setConstraints(
                                    Constraints.Builder()
                                            .setRequiresBatteryNotLow(true)
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .build()
                            )
                            .build()
            )
        }

        fun deschedule() {
            WorkManager.getInstance().cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!UpdateIntegration.doesSupportUpdates(context)) {
            return Result.failure()
        }

        try {
            val database = DefaultAppLogic.with(context).database

            UpdateUtil.doUpdateCheck(context, database, enableNotifications = true)

            return Result.success()
        } catch (ex: Exception) {
            return Result.retry()
        }
    }
}