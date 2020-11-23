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
package io.timelimit.android.sync.websocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.ignoreUnchanged

class NetworkStatusUtil (context: Context): NetworkStatusInterface {
    companion object {
        private val handler = Threads.mainThreadHandler
    }

    private val context = context.applicationContext
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val statusInternal = MutableLiveData<NetworkStatus>().apply { value = NetworkStatus.Offline }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            handler.post {
                val networkInfo = connectivityManager.activeNetworkInfo

                if (networkInfo == null) {
                    statusInternal.value = NetworkStatus.Offline
                } else if (networkInfo.detailedState == NetworkInfo.DetailedState.CONNECTED) {
                    statusInternal.value = NetworkStatus.Online
                } else {
                    statusInternal.value = NetworkStatus.Offline
                }
            }
        }
    }
    private val refreshRunnable = Runnable { forceRefresh() }
    private var didRegister = false

    override val status = statusInternal.ignoreUnchanged()

    override fun forceRefresh() {
        handler.removeCallbacks(refreshRunnable)

        if (BuildConfig.hasServer) {
            if (didRegister) context.applicationContext.unregisterReceiver(receiver)
            context.applicationContext.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)); didRegister = true

            handler.postDelayed(refreshRunnable, 15 * 1000 /* 15 seconds */)
        }
    }

    init { forceRefresh() }
}

enum class NetworkStatus {
    Offline, Online
}

interface NetworkStatusInterface {
    fun forceRefresh()

    val status: LiveData<NetworkStatus>
}