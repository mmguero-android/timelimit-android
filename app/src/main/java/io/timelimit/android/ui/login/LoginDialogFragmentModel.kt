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
package io.timelimit.android.ui.login

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChildSignInAction
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.sync.actions.SetKeepSignedInAction
import io.timelimit.android.sync.actions.apply.ApplyActionChildAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoginDialogFragmentModel(application: Application): AndroidViewModel(application) {
    val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val logic = DefaultAppLogic.with(application)
    private val users = logic.database.user().getAllUsersLive()
    private val isConnectedMode = logic.fullVersion.isLocalMode.invert()
    private val selectedUser = users.switchMap { users ->
        selectedUserId.map { userId ->
            users.find { it.id == userId }
        }
    }
    private val currentDeviceUser = logic.deviceUserId
    private val isCheckingPassword = MutableLiveData<Boolean>().apply { value = false }
    private val wasPasswordWrong = MutableLiveData<Boolean>().apply { value = false }
    private val isLoginDone = MutableLiveData<Boolean>().apply { value = false }
    private val loginLock = Mutex()

    val status: LiveData<LoginDialogStatus> = isLoginDone.switchMap { isLoginDone ->
        if (isLoginDone) {
            liveDataFromValue(LoginDialogDone as LoginDialogStatus)
        } else {
            selectedUser.switchMap { selectedUser ->
                when (selectedUser?.type) {
                    UserType.Parent -> {
                        val isAlreadyCurrentUser = currentDeviceUser.map { it == selectedUser.id }.ignoreUnchanged()

                        isConnectedMode.switchMap { isConnectedMode ->
                            isAlreadyCurrentUser.switchMap { isAlreadyCurrentUser ->
                                isCheckingPassword.switchMap { isCheckingPassword ->
                                    wasPasswordWrong.map { wasPasswordWrong ->
                                        ParentUserLogin(
                                                isConnectedMode = isConnectedMode,
                                                isAlreadyCurrentDeviceUser = isAlreadyCurrentUser,
                                                isCheckingPassword = isCheckingPassword,
                                                wasPasswordWrong = wasPasswordWrong
                                        ) as LoginDialogStatus
                                    }
                                }
                            }
                        }
                    }
                    UserType.Child -> {
                        logic.fullVersion.shouldProvideFullVersionFunctions.switchMap { fullversion ->
                            if (fullversion) {
                                if (selectedUser.password.isEmpty()) {
                                    liveDataFromValue(CanNotSignInChildHasNoPassword(childName = selectedUser.name) as LoginDialogStatus)
                                } else {
                                    val isAlreadyCurrentUser = currentDeviceUser.map { it == selectedUser.id }.ignoreUnchanged()

                                    isAlreadyCurrentUser.switchMap { isSignedIn ->
                                        if (isSignedIn) {
                                            liveDataFromValue(ChildAlreadyDeviceUser as LoginDialogStatus)
                                        } else {
                                            isCheckingPassword.switchMap { isCheckingPassword ->
                                                wasPasswordWrong.map { wasPasswordWrong ->
                                                    ChildUserLogin(
                                                            isCheckingPassword = isCheckingPassword,
                                                            wasPasswordWrong = wasPasswordWrong
                                                    ) as LoginDialogStatus
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                liveDataFromValue(ChildLoginRequiresPremiumStatus as LoginDialogStatus)
                            }
                        }
                    }
                    null -> {
                        users.map { users ->
                            UserListLoginDialogStatus(users) as LoginDialogStatus
                        }
                    }
                }
            }
        }
    }

    fun startSignIn(user: User) {
        selectedUserId.value = user.id
    }

    fun tryParentLogin(
            password: String,
            keepSignedIn: Boolean,
            setAsDeviceUser: Boolean,
            model: ActivityViewModel
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntry = selectedUser.waitForNullableValue()
                    val ownDeviceId = logic.deviceId.waitForNullableValue()

                    if (userEntry?.type != UserType.Parent || ownDeviceId == null) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    val secondPasswordHash = Threads.crypto.executeAndWait { PasswordHashing.hashSyncWithSalt(password, userEntry.secondPasswordSalt) }

                    val authenticatedUser = AuthenticatedUser(
                            userId = userEntry.id,
                            firstPasswordHash = userEntry.password,
                            secondPasswordHash = secondPasswordHash
                    )

                    model.setAuthenticatedUser(authenticatedUser)

                    if (setAsDeviceUser) {
                        val deviceEntry = logic.deviceEntry.waitForNonNullValue()!!

                        if (deviceEntry.currentUserId != userEntry.id) {
                            ActivityViewModel.dispatchWithoutCheckOrCatching(
                                    SetDeviceUserAction(
                                            deviceId = ownDeviceId,
                                            userId = userEntry.id
                                    ),
                                    authenticatedUser = authenticatedUser,
                                    logic = logic
                            )
                        }
                    }

                    if (keepSignedIn) {
                        if (
                                setAsDeviceUser ||
                                (currentDeviceUser.waitForNullableValue() == userEntry.id)
                        ) {
                            ActivityViewModel.dispatchWithoutCheckOrCatching(
                                    SetKeepSignedInAction(
                                            deviceId = ownDeviceId,
                                            keepSignedIn = true
                                    ),
                                    authenticatedUser = authenticatedUser,
                                    logic = logic
                            )
                        }
                    }

                    isLoginDone.value = true
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun tryChildLogin(
            password: String,
            model: ActivityViewModel
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntry = selectedUser.waitForNullableValue()
                    val ownDeviceId = logic.deviceId.waitForNullableValue()

                    if (userEntry?.type != UserType.Child || ownDeviceId == null) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    val secondPasswordHash = Threads.crypto.executeAndWait { PasswordHashing.hashSyncWithSalt(password, userEntry.secondPasswordSalt) }

                    ApplyActionUtil.applyChildAction(
                            action = ChildSignInAction,
                            database = logic.database,
                            authentication = ApplyActionChildAuthentication(
                                    childUserId = userEntry.id,
                                    secondPasswordHash = secondPasswordHash
                            ),
                            syncUtil = logic.syncUtil
                    )

                    Toast.makeText(getApplication(), R.string.login_child_done_toast, Toast.LENGTH_SHORT).show()

                    isLoginDone.value = true
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun resetPasswordWrong() {
        if (wasPasswordWrong.value == true) {
            wasPasswordWrong.value = false
        }
    }

    fun goBack(): Boolean {
        return if (status.value is UserListLoginDialogStatus) {
            false
        } else {
            selectedUserId.value = null

            true
        }
    }
}

sealed class LoginDialogStatus
data class UserListLoginDialogStatus(val usersToShow: List<User>): LoginDialogStatus()
data class ParentUserLogin(
        val isConnectedMode: Boolean,
        val isAlreadyCurrentDeviceUser: Boolean,
        val isCheckingPassword: Boolean,
        val wasPasswordWrong: Boolean
): LoginDialogStatus()
object LoginDialogDone: LoginDialogStatus()
data class CanNotSignInChildHasNoPassword(val childName: String): LoginDialogStatus()
object ChildAlreadyDeviceUser: LoginDialogStatus()
data class ChildUserLogin(
        val isCheckingPassword: Boolean,
        val wasPasswordWrong: Boolean
): LoginDialogStatus()
object ChildLoginRequiresPremiumStatus: LoginDialogStatus()