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
package io.timelimit.android.ui.manage.parent.password.restore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.transaction
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.ParentPassword

class RestoreParentPasswordViewModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

    private val statusInternal = MutableLiveData<RestoreParentPasswordStatus>().apply {
        value = RestoreParentPasswordStatus.WaitForAuthentication
    }

    private var mailAuthToken: String? = null
    private var parentUserId: String? = null

    val status = statusInternal.castDown()

    fun setParams(mailAuthToken: String, parentUserId: String) {
        if (this.mailAuthToken != null) {
            return
        }

        this.mailAuthToken = mailAuthToken
        this.parentUserId = parentUserId

        statusInternal.value = RestoreParentPasswordStatus.Working

        runAsync {
            try {
                val api = logic.serverLogic.getServerConfigCoroutine().api
                val canRecover = api.canRecoverPassword(mailAuthToken, parentUserId)

                if (canRecover) {
                    statusInternal.value = RestoreParentPasswordStatus.WaitForNewPassword
                } else {
                    statusInternal.value = RestoreParentPasswordStatus.ErrorCanNotRecover
                }
            } catch (ex: Exception) {
                statusInternal.value = RestoreParentPasswordStatus.NetworkError
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (statusInternal.value != RestoreParentPasswordStatus.WaitForNewPassword) {
            return
        }

        statusInternal.value = RestoreParentPasswordStatus.Working

        runAsync {
            try {
                val parentPassword = ParentPassword.createCoroutine(newPassword)
                val api = logic.serverLogic.getServerConfigCoroutine().api

                api.recoverPasswordByMailToken(mailAuthToken!!, parentPassword)

                // update the local database to make the new password work directly
                Threads.database.executeAndWait {
                    logic.database.transaction().use {
                        transaction ->

                        val user = logic.database.user().getUserByIdSync(parentUserId!!)

                        if (user!!.type != UserType.Parent) {
                            throw IllegalStateException()
                        }

                        logic.database.user().updateUserSync(
                                user.copy(
                                        password = parentPassword.parentPasswordHash,
                                        secondPasswordSalt = parentPassword.parentPasswordSecondSalt
                                )
                        )

                        transaction.setSuccess()
                    }
                }

                statusInternal.value = RestoreParentPasswordStatus.Done
            } catch (ex: Exception) {
                statusInternal.value = RestoreParentPasswordStatus.NetworkError
            }
        }
    }
}

enum class RestoreParentPasswordStatus {
    WaitForAuthentication, ErrorCanNotRecover, WaitForNewPassword, Working, NetworkError, Done
}
