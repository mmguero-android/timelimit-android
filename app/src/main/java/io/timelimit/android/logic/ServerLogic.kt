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

import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.sync.network.api.ServerApi

class ServerLogic(private val appLogic: AppLogic) {
    companion object {
        private fun getServerUrlFromCustomServerUrl(customServerUrl: String) = customServerUrl.let { savedUrl ->
            if (savedUrl.isEmpty()) {
                BuildConfig.serverUrl
            } else {
                savedUrl
            }
        }
    }

    fun getServerFromCustomServerUrl(customServerUrl: String) = appLogic.serverCreator(getServerUrlFromCustomServerUrl(customServerUrl))

    suspend fun getServerConfigCoroutine(): ServerConfig {
        return Threads.database.executeAndWait {
            appLogic.database.runInTransaction {
                val customServerUrl = appLogic.database.config().getCustomServerUrlSync()
                val deviceAuthToken = appLogic.database.config().getDeviceAuthTokenSync()
                val isAppEnabled = appLogic.database.config().getOwnDeviceIdSync() != null

                ServerConfig(
                        customServerUrl = customServerUrl,
                        deviceAuthToken = deviceAuthToken,
                        isAppEnabled = isAppEnabled,
                        serverLogic = this
                )
            }
        }
    }

    data class ServerConfig(
            val customServerUrl: String,
            val deviceAuthToken: String,
            val isAppEnabled: Boolean,
            private val serverLogic: ServerLogic
    ) {
        val hasAuthToken = deviceAuthToken != ""
        val serverUrl = ServerLogic.getServerUrlFromCustomServerUrl(customServerUrl)
        val api: ServerApi by lazy { serverLogic.getServerFromCustomServerUrl(serverUrl) }
    }
}