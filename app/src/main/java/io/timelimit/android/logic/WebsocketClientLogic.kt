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
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.websocket.NetworkStatus
import io.timelimit.android.sync.websocket.WebsocketClient
import io.timelimit.android.sync.websocket.WebsocketClientCreator
import io.timelimit.android.sync.websocket.WebsocketClientListener
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.ui.manage.child.primarydevice.UpdatePrimaryDeviceModel

class WebsocketClientLogic(
        private val appLogic: AppLogic,
        private val isConnectedInternal: MutableLiveData<Boolean>,
        websocketClientCreator: WebsocketClientCreator
) {
    companion object {
        private const val LOG_TAG = "WebsocketClientLogic"
    }

    // it's not checked if the device is configured for the online mode here because this is caught below
    private val shouldConnectToWebsocket = appLogic.enable.switchMap { enabled ->

        // app must be enabled
        if (enabled == true) {
            val okForCurrentUser = appLogic.deviceUserEntry.switchMap {
                if (it?.type == UserType.Child) {
                    liveDataFromValue(true)
                } else {
                    IsAppInForeground.isRunning
                }
            }

            val okFromNetworkStatus = appLogic.networkStatus.map { networkStatus ->
                networkStatus == NetworkStatus.Online
            }.or(appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.IGNORE_SYSTEM_CONNECTION_STATUS))

            val okFromScreenStatus = appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.DISCONNECT_WHEN_SCREEN_OFF).invert()
                    .or(liveDataFromFunction { appLogic.platformIntegration.isScreenOn() })

            okForCurrentUser.and(okFromNetworkStatus).and(okFromScreenStatus)
        } else {
            liveDataFromValue(false)
        }
    }

    private val deviceAuthTokenToConnectFor = shouldConnectToWebsocket.switchMap { shouldConnect ->

        if (shouldConnect) {
            appLogic.database.config().getDeviceAuthTokenAsync()
        } else {
            liveDataFromValue("")
        }
    }

    private val connectedDevicesInternal = MutableLiveData<Set<String>>().apply { value = emptySet() }
    val connectedDevices = connectedDevicesInternal.ignoreUnchanged()

    init {
        runAsyncExpectForever {
            var previousDeviceAuthToken: String? = null
            var currentWebsocketClient: WebsocketClient? = null

            while (true) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "wait for new device auth token")
                }

                val deviceAuthToken = deviceAuthTokenToConnectFor.waitUntilValueMatches { it != previousDeviceAuthToken }!!
                previousDeviceAuthToken = deviceAuthToken

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got new device auth token: $deviceAuthToken")
                }

                // shutdown any current connection
                currentWebsocketClient?.shutdown()
                currentWebsocketClient = null
                isConnectedInternal.postValue(false)

                if (deviceAuthToken.isNotEmpty()) {
                    val serverConfig = appLogic.serverLogic.getServerConfigCoroutine()

                    if (serverConfig.deviceAuthToken != deviceAuthToken) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "device auth token changed in the time between - don't connect")
                        }

                        continue
                    }

                    val serverUrl = serverConfig.serverUrl

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "create new websocket client for $serverUrl")
                    }

                    lateinit var newWebsocketClient: WebsocketClient

                    newWebsocketClient = websocketClientCreator.createWebsocketClient(
                            deviceAuthTokenToConnectFor = deviceAuthToken,
                            serverUrl = serverUrl,
                            listener = object : WebsocketClientListener {
                                override fun onConnectionEstablished() {
                                    if (currentWebsocketClient !== newWebsocketClient) {
                                        // we are not the current instance anymore
                                        return
                                    }

                                    isConnectedInternal.postValue(true)
                                    connectedDevicesInternal.postValue(emptySet())

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "request important sync because websocket connection was established")
                                    }

                                    appLogic.syncUtil.requestImportantSync()
                                }

                                override fun onSyncRequestedByServer(important: Boolean) {
                                    if (currentWebsocketClient !== newWebsocketClient) {
                                        // we are not the current instance anymore
                                        return
                                    }

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "request sync because the server did it")
                                    }

                                    if (important) {
                                        appLogic.syncUtil.requestImportantSync()
                                    } else {
                                        appLogic.syncUtil.requestUnimportantSync()
                                    }
                                }

                                override fun onConnectionLost() {
                                    if (currentWebsocketClient !== newWebsocketClient) {
                                        // we are not the current instance anymore
                                        return
                                    }

                                    isConnectedInternal.postValue(false)
                                    connectedDevicesInternal.postValue(emptySet())
                                }

                                override fun onGotConnectedDeviceList(connectedDeviceIds: Set<String>) {
                                    if (currentWebsocketClient !== newWebsocketClient) {
                                        // we are not the current instance anymore
                                        return
                                    }

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "got connected device list: ${connectedDeviceIds.joinToString(", ")}")
                                    }

                                    connectedDevicesInternal.postValue(connectedDeviceIds)
                                }

                                override fun onGotSignOutRequest() {
                                    if (currentWebsocketClient !== newWebsocketClient) {
                                        // we are not the current instance anymore
                                        return
                                    }

                                    runAsync {
                                        try {
                                            if (AppAffectedByPrimaryDeviceUtil.isCurrentAppAffectedByPrimaryDevice(appLogic)) {
                                                throw IllegalStateException("current device would be affected by primary device")
                                            }

                                            UpdatePrimaryDeviceModel.unsetPrimaryDeviceInBackground(appLogic)
                                        } catch (ex: Exception) {
                                            if (BuildConfig.DEBUG) {
                                                Log.w(LOG_TAG, "could not unset this device as current device", ex)
                                            }
                                        }
                                    }
                                }
                            }
                    )

                    currentWebsocketClient = newWebsocketClient
                }
            }
        }
    }
}