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
package io.timelimit.android.flavors

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.timelimit.android.BuildConfig

class GoogleSignInUtil(private val activity: Activity) {
    private val client: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(
                activity,
                GoogleSignInOptions.Builder()
                        .requestEmail()
                        .requestIdToken(BuildConfig.googleAuthClientId)
                        .build()
        )
    }

    fun getSignInIntent() = client.signInIntent

    fun processActivityResult(data: Intent?): String? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)

        try {
            return task.getResult(ApiException::class.java)?.idToken
        } catch (ex: ApiException) {
            // login failed

            return null
        }
    }
}
