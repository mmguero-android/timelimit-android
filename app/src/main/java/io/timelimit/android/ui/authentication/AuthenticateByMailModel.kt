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
package io.timelimit.android.ui.authentication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.api.BadRequestHttpError
import io.timelimit.android.sync.network.api.ForbiddenHttpError
import io.timelimit.android.sync.network.api.GoneHttpError
import io.timelimit.android.sync.network.api.HttpError
import java.io.IOException

class AuthenticateByMailModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "AuthenticateByMailModel"
    }

    private val logic = DefaultAppLogic.with(application)
    private val isBusyInternal = MutableLiveData<Boolean>().apply { value = false }
    private val mailAuthTokenInternal = MutableLiveData<String?>().apply { value = null }
    private val mailAddressToWhichCodeWasSentInternal = MutableLiveData<String?>().apply { value = null }
    private var mailLoginToken: String? = null

    val usingDefaultServer = logic.database.config().getCustomServerUrlAsync().map { it.isEmpty() }
    val isBusy = isBusyInternal.castDown()
    val mailAuthToken = mailAuthTokenInternal.castDown()
    val errorMessage = MutableLiveData<ErrorMessage?>().apply { value = null }
    val mailAddressToWhichCodeWasSent = mailAddressToWhichCodeWasSentInternal.castDown()

    fun handleGoogleAuthToken(googleAuthToken: String) {
        isBusyInternal.value = true

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api
                val mailAuthToken = api.getMailAuthTokenByGoogleAccountToken(googleAuthToken)

                mailAuthTokenInternal.value = mailAuthToken
            } catch (ex: BadRequestHttpError) {
                errorMessage.value = ErrorMessage.ServerRejection
            } catch (ex: Exception) {
                errorMessage.value = ErrorMessage.NetworkProblem
            } finally {
                isBusyInternal.value = false
            }
        }
    }

    fun sendAuthMessage(receiver: String) {
        isBusyInternal.value = true

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api

                mailLoginToken = api.sendMailLoginCode(
                        mail = receiver,
                        locale = getApplication<Application>().resources.configuration.locale.language
                )

                mailAddressToWhichCodeWasSentInternal.value = receiver
            } catch (ex: HttpError) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "sendAuthMessage()", ex)
                }

                errorMessage.value = ErrorMessage.ServerRejection
            } catch (ex: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "sendAuthMessage()", ex)
                }

                errorMessage.value = ErrorMessage.NetworkProblem
            } finally {
                isBusyInternal.value = false
            }
        }
    }

    fun confirmCode(code: String) {
        isBusyInternal.value = true

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api
                val mailAuthToken = api.signInByMailCode(mailLoginToken!!, code)

                mailAuthTokenInternal.value = mailAuthToken
            } catch (ex: ForbiddenHttpError) {
                errorMessage.value = ErrorMessage.WrongCode
            } catch (ex: GoneHttpError) {
                errorMessage.value = ErrorMessage.WrongCode

                // go back to first step
                mailAddressToWhichCodeWasSentInternal.value = null
            } catch (ex: HttpError) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "sendAuthMessage()", ex)
                }

                errorMessage.value = ErrorMessage.ServerRejection
            } catch (ex: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "sendAuthMessage()", ex)
                }

                errorMessage.value = ErrorMessage.NetworkProblem
            } finally {
                isBusyInternal.value = false
            }
        }
    }
}

enum class ErrorMessage {
    NetworkProblem, ServerRejection, WrongCode
}
