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

class ReportUninstallWorker(val context: Context, workerParameters: WorkerParameters): CoroutineWorker(context, workerParameters) {
    companion object {
        private const val DATA_AUTH_TOKEN = "deviceAuthToken"
        private const val DATA_CUSTOM_SERVER_URL = "customServerUrl"
        private const val LOG_TAG = "ReportUninstallWorker"

        fun enqueue(deviceAuthToken: String, customServerUrl: String) {
            if (deviceAuthToken.isEmpty()) {
                return
            }

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "enqueue()")
            }

            WorkManager.getInstance().enqueue(
                    OneTimeWorkRequest.Builder(ReportUninstallWorker::class.java)
                            .setConstraints(
                                    Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .build()
                            )
                            .setInputData(
                                    Data.Builder()
                                            .putString(DATA_AUTH_TOKEN, deviceAuthToken)
                                            .putString(DATA_CUSTOM_SERVER_URL, customServerUrl)
                                            .build()
                            )
                            .build()
            )
        }
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "doWork()")
        }

        return try {
            val logic = DefaultAppLogic.with(context)
            val deviceAuthToken = inputData.getString(DATA_AUTH_TOKEN)!!
            val customServerUrl = inputData.getString(DATA_CUSTOM_SERVER_URL) ?: ""

            logic.serverLogic.getServerFromCustomServerUrl(customServerUrl).reportDeviceRemoved(deviceAuthToken)

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "doWork() succeeded")
            }

            Result.success()
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "doWork() failed", ex)
            }

            Result.retry()
        }
    }
}
