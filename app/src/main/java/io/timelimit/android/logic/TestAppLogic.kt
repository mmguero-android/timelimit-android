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

import android.content.Context
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.RoomDatabase
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.dummy.DummyIntegration
import io.timelimit.android.integration.time.DummyTimeApi
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.sync.network.api.DummyServerApi
import io.timelimit.android.sync.websocket.DummyWebsocketClient
import io.timelimit.android.sync.websocket.NetworkStatus
import io.timelimit.android.sync.websocket.NetworkStatusInterface

class TestAppLogic(maximumProtectionLevel: ProtectionLevel, context: Context) {
    val platformIntegration = DummyIntegration(maximumProtectionLevel)
    val timeApi = DummyTimeApi(100)
    val database = RoomDatabase.createInMemoryInstance(context)
    val server = DummyServerApi()
    val networkStatus = MutableLiveData<NetworkStatus>().apply { value = NetworkStatus.Offline }

    val logic = AppLogic(
            platformIntegration = platformIntegration,
            timeApi = timeApi,
            database = database,
            serverCreator = { _ -> server },
            networkStatus = object: NetworkStatusInterface { override val status = networkStatus; override fun forceRefresh() {} },
            websocketClientCreator = DummyWebsocketClient.creator,
            context = context,
            isInitialized = liveDataFromValue(true)
    )
}
