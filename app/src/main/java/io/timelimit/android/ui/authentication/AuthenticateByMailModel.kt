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
package io.timelimit.android.ui.authentication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.api.*
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

    val recoveryUserId = MutableLiveData<String?>().apply { value = null }
    private val forcedMailAddress = recoveryUserId.switchMap { userId ->
        if (userId != null) {
            logic.database.user().getParentUserByIdLive(userId)
        } else {
            liveDataFromValue(null as User?)
        }
    }.map { it?.mail ?: "" }
    val screenToShow = isBusyInternal.switchMap { isBusy ->
        if (isBusy) {
            liveDataFromValue(ScreenToShow.Working)
        } else {
            mailAddressToWhichCodeWasSentInternal.switchMap { receiverMail ->
                if (receiverMail != null) {
                    liveDataFromValue(ScreenToShow.EnterReceivedCode)
                } else {
                    forcedMailAddress.map { forcedMailAddress ->
                        if (forcedMailAddress.isEmpty()) {
                            ScreenToShow.EnterMailAddress
                        } else {
                            ScreenToShow.ConfirmCurrentMail
                        }
                    }
                }
            }
        }
    }
    val mailAuthToken = mailAuthTokenInternal.castDown()
    val errorMessage = MutableLiveData<ErrorMessage?>().apply { value = null }
    val mailAddressToWhichCodeWasSent = mailAddressToWhichCodeWasSentInternal.castDown()

    fun sendAuthMessageToForcedMailAddress() {
        isBusyInternal.value = true

        runAsync {
            val mailAddress = forcedMailAddress.waitForNonNullValue()

            if (mailAddress.isEmpty()) {
                isBusyInternal.value = false
                // not correct, but this would happen if the value would be used
                errorMessage.value = ErrorMessage.ServerRejection
            } else {
                sendAuthMessage(mailAddress)
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
            } catch (ex: TooManyRequestsHttpError) {
                errorMessage.value = ErrorMessage.TooManyRequests
            } catch (ex: HttpError) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "sendAuthMessage()", ex)
                }

                errorMessage.value = ErrorMessage.ServerRejection
            } catch (ex: MailServerBlacklistedException) {
                errorMessage.value = ErrorMessage.BlacklistedMailServer
            } catch (ex: MailAddressNotWhitelistedException) {
                errorMessage.value = ErrorMessage.NotWhitelistedMailAddress
            } catch (ex: Exception) {
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
    NetworkProblem, ServerRejection, WrongCode, BlacklistedMailServer, NotWhitelistedMailAddress, TooManyRequests
}

enum class ScreenToShow {
    EnterMailAddress,
    EnterReceivedCode,
    ConfirmCurrentMail,
    Working
}