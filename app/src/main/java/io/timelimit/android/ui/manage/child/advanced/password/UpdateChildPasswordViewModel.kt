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
package io.timelimit.android.ui.manage.child.advanced.password

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChildChangePasswordAction
import io.timelimit.android.sync.actions.apply.ApplyActionChildAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.ParentPassword

class UpdateChildPasswordViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ChangeParentPassword"
    }

    private val statusInternal = MutableLiveData<ChangeChildPasswordViewModelStatus>().apply {
        value = ChangeChildPasswordViewModelStatus.Idle
    }

    private val logic = DefaultAppLogic.with(application)

    val status = statusInternal.castDown()

    fun confirmError() {
        val value = statusInternal.value

        if (value == ChangeChildPasswordViewModelStatus.Failed || value == ChangeChildPasswordViewModelStatus.WrongPassword) {
            statusInternal.value = ChangeChildPasswordViewModelStatus.Idle
        }
    }

    fun changePassword(childUserId: String, oldPassword: String, newPassword: String) {
        runAsync {
            try {
                if (statusInternal.value != ChangeChildPasswordViewModelStatus.Idle) {
                    return@runAsync
                }

                statusInternal.value = ChangeChildPasswordViewModelStatus.Working

                val userEntry = logic.database.user().getUserByIdLive(childUserId).waitForNullableValue()

                if (userEntry == null || userEntry.type != UserType.Child) {
                    statusInternal.value = ChangeChildPasswordViewModelStatus.Failed
                    return@runAsync
                }

                val isOldPasswordCorrect = Threads.crypto.executeAndWait {
                    PasswordHashing.validateSync(oldPassword, userEntry.password)
                }

                if (!isOldPasswordCorrect) {
                    statusInternal.value = ChangeChildPasswordViewModelStatus.WrongPassword
                    return@runAsync
                }

                val oldPasswordSecondHash = Threads.crypto.executeAndWait {
                    PasswordHashing.hashSyncWithSalt(oldPassword, userEntry.secondPasswordSalt)
                }

                val action = ChildChangePasswordAction(
                        password = ParentPassword.createCoroutine(newPassword)
                )

                ApplyActionUtil.applyChildAction(
                        action,
                        logic.database,
                        ApplyActionChildAuthentication(
                                childUserId = childUserId,
                                secondPasswordHash = oldPasswordSecondHash
                        ),
                        logic.syncUtil
                )

                statusInternal.value = ChangeChildPasswordViewModelStatus.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "changing password failed", ex)
                }

                statusInternal.value = ChangeChildPasswordViewModelStatus.Failed
            }
        }
    }
}

enum class ChangeChildPasswordViewModelStatus {
    Idle, Working, Failed, WrongPassword, Done
}
