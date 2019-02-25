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
package io.timelimit.android.ui.manage.parent.link

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.StatusOfMailAddress

class LinkParentMailViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "LinkParentMailViewModel"
    }

    private val logic = DefaultAppLogic.with(application)

    private val statusInternal = MutableLiveData<LinkParentMailViewModelStatus>().apply {
        value = LinkParentMailViewModelStatus.WaitForAuthentication
    }
    private lateinit var mailAuthToken: String
    private val mailAddressInternal = MutableLiveData<String>()

    val status = statusInternal.castDown()
    val mailAddress = mailAddressInternal.castDown()

    fun setMailAuthToken(mailAuthToken: String) {
        if (statusInternal.value != LinkParentMailViewModelStatus.WaitForAuthentication) {
            return
        }

        this.mailAuthToken = mailAuthToken
        statusInternal.value = LinkParentMailViewModelStatus.Working

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api
                val status = api.getStatusByMailToken(mailAuthToken)

                mailAddressInternal.value = status.mail

                statusInternal.value = when (status.status) {
                    StatusOfMailAddress.MailAddressWithFamily -> LinkParentMailViewModelStatus.AlreadyLinked
                    StatusOfMailAddress.MailAddressWithoutFamily-> LinkParentMailViewModelStatus.WaitForConfirmationWithPassword
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "failed to get status of the mail auth token", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                statusInternal.value = LinkParentMailViewModelStatus.ShouldLeaveScreen
            }
        }
    }

    fun doLinking(password: String, parentUserId: String) {
        if (statusInternal.value != LinkParentMailViewModelStatus.WaitForConfirmationWithPassword) {
            return
        }

        statusInternal.value = LinkParentMailViewModelStatus.Working

        runAsync {
            try {
                // check password locally
                val userEntry = logic.database.user().getParentUserByIdLive(parentUserId).waitForNullableValue()!!

                if (userEntry.type != UserType.Parent) {
                    throw IllegalArgumentException()
                }

                val isPasswordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                if (!isPasswordValid) {
                    statusInternal.value = LinkParentMailViewModelStatus.WaitForConfirmationWithPassword
                    Toast.makeText(getApplication(), R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                    return@runAsync
                }

                val passwordSecondHash = Threads.crypto.executeAndWait { PasswordHashing.hashSyncWithSalt(password, userEntry.secondPasswordSalt) }
                val api = logic.serverLogic.getServerConfigCoroutine().api

                api.linkParentMailAddress(
                        mailAuthToken = mailAuthToken,
                        deviceAuthToken = logic.database.config().getDeviceAuthTokenAsync().waitForNonNullValue(),
                        parentUserId = parentUserId,
                        secondPasswordHash = passwordSecondHash
                )

                statusInternal.value = LinkParentMailViewModelStatus.ShouldLeaveScreen
                Toast.makeText(getApplication(), R.string.manage_parent_link_mail_screen_toast_confirm, Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "failed to get status of the mail auth token", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                statusInternal.value = LinkParentMailViewModelStatus.WaitForConfirmationWithPassword
            }
        }
    }
}

enum class LinkParentMailViewModelStatus {
    WaitForAuthentication, WaitForConfirmationWithPassword, Working, ShouldLeaveScreen, AlreadyLinked
}
