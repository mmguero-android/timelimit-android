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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log
import androidx.core.net.ConnectivityManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads

object NetworkStatusUtil {
    fun getSystemNetworkStatusLive(context: Context): LiveData<NetworkStatus> {
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val status = MutableLiveData<NetworkStatus>()

        status.value = NetworkStatus.Offline

        if (BuildConfig.hasServer) {
            context.applicationContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    Threads.mainThreadHandler.post {
                        val networkInfo = connectivityManager.activeNetworkInfo

                        if (networkInfo == null) {
                            status.value = NetworkStatus.Offline
                        } else if (networkInfo.detailedState == NetworkInfo.DetailedState.CONNECTED) {
                            status.value = NetworkStatus.Online
                        } else {
                            status.value = NetworkStatus.Offline
                        }
                    }
                }
            }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

        return status
    }
}

enum class NetworkStatus {
    Offline, Online
}
