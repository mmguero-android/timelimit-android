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
package io.timelimit.android.sync.websocket

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import io.timelimit.android.sync.network.api.httpClient
import org.json.JSONArray
import org.json.JSONObject

interface WebsocketClientCreator {
    fun createWebsocketClient(serverUrl: String, deviceAuthTokenToConnectFor: String, listener: WebsocketClientListener): WebsocketClient
}

interface WebsocketClientListener {
    fun onSyncRequestedByServer(important: Boolean)
    fun onConnectionEstablished()
    fun onConnectionLost()
    fun onGotConnectedDeviceList(connectedDeviceIds: Set<String>)
    fun onGotSignOutRequest()
}

abstract class WebsocketClient() {
    abstract fun shutdown()
}

class SocketIoWebsocketClient(serverUrl: String, private val deviceAuthTokenToConnectFor: String, private val listener: WebsocketClientListener): WebsocketClient() {
    companion object {
        val creator = object: WebsocketClientCreator {
            override fun createWebsocketClient(serverUrl: String, deviceAuthTokenToConnectFor: String, listener: WebsocketClientListener): WebsocketClient {
                return SocketIoWebsocketClient(serverUrl, deviceAuthTokenToConnectFor, listener)
            }
        }
    }

    private val client = IO.socket(
            serverUrl,
            IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
                webSocketFactory = httpClient
            }
    )

    init {
        client.on(Socket.EVENT_CONNECT) { _ ->
            client.emit("devicelogin", deviceAuthTokenToConnectFor, Ack {
                listener.onConnectionEstablished()
            })
        }

        client.on(Socket.EVENT_DISCONNECT) {
            listener.onConnectionLost()
        }

        client.on("should sync") {
            val params = it[0] as JSONObject

            val isImportant = params.getBoolean("isImportant")

            listener.onSyncRequestedByServer(
                    important = isImportant
            )
        }

        client.on("connected devices") {
            val devices = it[0] as JSONArray
            val result = mutableSetOf<String>()

            for (i in 0..(devices.length() - 1)) {
                result.add(devices[i] as String)
            }

            listener.onGotConnectedDeviceList(result)
        }

        client.on("sign out") {
            listener.onGotSignOutRequest()
        }

        client.connect()
    }

    override fun shutdown() {
        client.disconnect()
    }
}

object DummyWebsocketClient: WebsocketClient() {
    val creator = object: WebsocketClientCreator {
        override fun createWebsocketClient(serverUrl: String, deviceAuthTokenToConnectFor: String, listener: WebsocketClientListener): WebsocketClient {
            return DummyWebsocketClient
        }
    }

    override fun shutdown() {
        // nothing to do
    }
}