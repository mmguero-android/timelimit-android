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
package io.timelimit.flavor

import android.util.Log
import io.timelimit.android.BuildConfig
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

object Dns {
    private const val LOG_TAG = "DNS"

    val instance = object: Dns {
        val system = Dns.SYSTEM

        override fun lookup(hostname: String): List<InetAddress> {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "looking up $hostname")
            }

            if (hostname == BuildConfig.serverDomain) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "detected server domain")
                }

                val a = try {
                    system.lookup(BuildConfig.serverDomain)
                } catch (ex: UnknownHostException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "could not get primary dns")
                    }

                    emptyList<InetAddress>()
                }

                val b = try {
                    system.lookup(BuildConfig.backupServerDomain)
                } catch (ex: UnknownHostException) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "could not get secondary dns")
                    }

                    emptyList<InetAddress>()
                }

                val combined = a + b

                if (combined.isEmpty()) {
                    throw UnknownHostException()
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "return $combined")
                }

                return combined.toMutableList()
            } else {
                return system.lookup(hostname)
            }
        }
    }
}