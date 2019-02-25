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
package io.timelimit.android.logic

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.User
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.integration.time.TimeApi
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.sync.SyncUtil
import io.timelimit.android.sync.network.api.ServerApi
import io.timelimit.android.sync.websocket.NetworkStatus
import io.timelimit.android.sync.websocket.WebsocketClientCreator

class AppLogic(
        val platformIntegration: PlatformIntegration,
        val timeApi: TimeApi,
        val database: Database,
        val serverCreator: (serverUrl: String) -> ServerApi,
        val networkStatus: LiveData<NetworkStatus>,
        websocketClientCreator: WebsocketClientCreator,
        val context: Context,
        val isInitialized: LiveData<Boolean>
) {
    val enable = MutableLiveData<Boolean>().apply { value = true }

    val deviceId = database.config().getOwnDeviceId()

    val deviceEntry = Transformations.switchMap<String?, Device?> (deviceId) {
        if (it == null) {
            liveDataFromValue(null)
        } else {
            database.device().getDeviceById(it)
        }
    }.ignoreUnchanged()

    val deviceEntryIfEnabled = enable.switchMap {
        if (it == null || it == false) {
            liveDataFromValue(null as Device?)
        } else {
            deviceEntry
        }
    }

    val deviceUserId: LiveData<String> = Transformations.map(deviceEntry) { it?.currentUserId ?: "" }

    val deviceUserEntry = deviceUserId.switchMap {
        if (it == "") {
            liveDataFromValue(null as User?)
        } else {
            database.user().getUserByIdLive(it)
        }
    }.ignoreUnchanged()

    val serverLogic = ServerLogic(this)
    val defaultUserLogic = DefaultUserLogic(this)
    val realTimeLogic = RealTimeLogic(this)
    val fullVersion = FullVersionLogic(this)
    val currentDeviceLogic = CurrentDeviceLogic(this)
    val backgroundTaskLogic = BackgroundTaskLogic(this)
    val appSetupLogic = AppSetupLogic(this)

    private val isConnectedInternal = MutableLiveData<Boolean>().apply { value = false }
    val isConnected = isConnectedInternal.castDown()
    val syncUtil = SyncUtil(this)
    val websocket = WebsocketClientLogic(
            appLogic = this,
            isConnectedInternal = isConnectedInternal,
            websocketClientCreator = websocketClientCreator
    )

    init {
        SyncInstalledAppsLogic(this)
    }

    val manipulationLogic = ManipulationLogic(this)

    fun shutdown() {
        enable.value = false
    }
}
