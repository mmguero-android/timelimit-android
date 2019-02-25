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
package io.timelimit.android.sync.network.api

import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.pinning.CertificatePinning
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

val httpClient: OkHttpClient by lazy {
    val builder = OkHttpClient.Builder()
            .certificatePinner(CertificatePinning.configuration)

    if (BuildConfig.DEBUG) {
        builder.addInterceptor (HttpLoggingInterceptor {
            Log.d("HttpClient", it)
        })
    }

    builder.build()
}
