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
import io.timelimit.android.data.model.derived.CompleteUserLoginRelatedData
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.BlockingReasonUtil
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChildSignInAction
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.sync.actions.SetKeepSignedInAction
import io.timelimit.android.sync.actions.apply.ApplyActionChildAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import io.timelimit.android.ui.manage.parent.key.ScannedKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoginDialogFragmentModel(application: Application): AndroidViewModel(application) {
    val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val logic = DefaultAppLogic.with(application)
    private val blockingReasonUtil = BlockingReasonUtil(logic)
    private val users = logic.database.user().getAllUsersLive()
    private val selectedUser = selectedUserId.switchMap { selectedUserId ->
        if (selectedUserId != null)
            logic.database.derivedDataDao().getUserLoginRelatedDataLive(selectedUserId)
        else
            liveDataFromValue(null as CompleteUserLoginRelatedData?)
    }
    private val isCheckingPassword = MutableLiveData<Boolean>().apply { value = false }
    private val wasPasswordWrong = MutableLiveData<Boolean>().apply { value = false }
    private val isLoginDone = MutableLiveData<Boolean>().apply { value = false }
    private val loginLock = Mutex()

    val status: LiveData<LoginDialogStatus> = isLoginDone.switchMap { isLoginDone ->
        if (isLoginDone) {
            liveDataFromValue(LoginDialogDone as LoginDialogStatus)
        } else {
            selectedUser.switchMap { selectedUserInfo ->
                val selectedUser = selectedUserInfo?.loginRelatedData?.user

                when (selectedUser?.type) {
                    UserType.Parent -> {
                        val isAlreadyCurrentUser = selectedUserInfo.deviceRelatedData.deviceEntry.currentUserId == selectedUser.id
                        val isConnectedMode = !selectedUserInfo.deviceRelatedData.isLocalMode
                        val loginScreen = isCheckingPassword.switchMap { isCheckingPassword ->
                            wasPasswordWrong.map { wasPasswordWrong ->
                                ParentUserLogin(
                                        isConnectedMode = isConnectedMode,
                                        isAlreadyCurrentDeviceUser = isAlreadyCurrentUser,
                                        isCheckingPassword = isCheckingPassword,
                                        wasPasswordWrong = wasPasswordWrong
                                ) as LoginDialogStatus
                            }
                        }

                        AllowUserLoginStatusUtil.calculateLive(logic, selectedUser.id).switchMap { status ->
                            if (status is AllowUserLoginStatus.Allow) {
                                loginScreen
                            } else if (
                                    (status is AllowUserLoginStatus.ForbidByCurrentTime && status.missingNetworkTime) ||
                                    (status is AllowUserLoginStatus.ForbidByCategory && status.blockingReason == BlockingReason.MissingNetworkTime)
                            ) {
                                liveDataFromValue(ParentUserLoginMissingTrustedTime as LoginDialogStatus)
                            } else if (status is AllowUserLoginStatus.ForbidByCurrentTime) {
                                liveDataFromValue(ParentUserLoginBlockedTime as LoginDialogStatus)
                            } else if (status is AllowUserLoginStatus.ForbidByCategory) {
                                liveDataFromValue(
                                        ParentUserLoginBlockedByCategory(
                                                categoryTitle = status.categoryTitle,
                                                reason = status.blockingReason
                                        ) as LoginDialogStatus
                                )
                            } else {
                                loginScreen
                            }
                        }
                    }
                    UserType.Child -> {
                        val hasPremium = selectedUserInfo.deviceRelatedData.isLocalMode || selectedUserInfo.deviceRelatedData.isConnectedAndHasPremium

                        if (hasPremium) {
                            if (selectedUser.password.isEmpty()) {
                                liveDataFromValue(CanNotSignInChildHasNoPassword(childName = selectedUser.name) as LoginDialogStatus)
                            } else {
                                val isAlreadyCurrentUser = selectedUserInfo.deviceRelatedData.deviceEntry.currentUserId == selectedUser.id

                                if (isAlreadyCurrentUser) {
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
                        } else {
                            liveDataFromValue(ChildLoginRequiresPremiumStatus as LoginDialogStatus)
                        }
                    }
                    null -> {
                        logic.fullVersion.isLocalMode.switchMap { isLocalMode ->
                            users.map { users ->
                                UserListLoginDialogStatus(
                                        usersToShow = users,
                                        isLocalMode = isLocalMode
                                ) as LoginDialogStatus
                            }
                        }
                    }
                }
            }
        }
    }

    fun startSignIn(user: User) {
        selectedUserId.value = user.id
    }

    fun tryDefaultLogin(model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                logic.database.user().getParentUsersLive().waitForNonNullValue().singleOrNull()?.let { user ->
                    val emptyPasswordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync("", user.password) }
                    val hasBlockedTimes = !user.blockedTimes.dataNotToModify.isEmpty

                    val shouldSignIn = if (emptyPasswordValid) {
                        Threads.database.executeAndWait {
                            AllowUserLoginStatusUtil.calculateSync(
                                    logic = logic,
                                    userId = user.id
                            ) is AllowUserLoginStatus.Allow
                        }
                    } else {
                        false
                    }

                    if (shouldSignIn) {
                        model.setAuthenticatedUser(AuthenticatedUser(
                                userId = user.id,
                                firstPasswordHash = user.password,
                                secondPasswordHash = Threads.crypto.executeAndWait { PasswordHashing.hashSyncWithSalt("", user.secondPasswordSalt) }
                        ))

                        if (hasBlockedTimes) {
                            Toast.makeText(getApplication(), R.string.manage_parent_blocked_times_toast, Toast.LENGTH_LONG).show()
                        }

                        isLoginDone.value = true
                    }
                }
            }
        }
    }

    fun tryCodeLogin(code: ScannedKey, model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                if (!logic.fullVersion.isLocalMode.waitForNonNullValue()) {
                    Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                    return@runAsync
                }

                val user: User? = Threads.database.executeAndWait {
                    logic.database.runInTransaction {
                        val keyEntry = logic.database.userKey().findUserKeyByPublicKeySync(code.publicKey)

                        if (keyEntry == null) {
                            Threads.mainThreadHandler.post {
                                Toast.makeText(getApplication(), R.string.login_scan_code_err_not_linked, Toast.LENGTH_SHORT).show()
                            }

                            return@runInTransaction null
                        }

                        if (keyEntry.lastUse >= code.timestamp) {
                            Threads.mainThreadHandler.post {
                                Toast.makeText(getApplication(), R.string.login_scan_code_err_expired, Toast.LENGTH_SHORT).show()
                            }

                            return@runInTransaction null
                        }

                        logic.database.userKey().updateKeyTimestamp(code.publicKey, code.timestamp)

                        logic.database.user().getUserByIdSync(keyEntry.userId)
                    }
                }

                if (user != null && user.type == UserType.Parent) {
                    val hasBlockedTimes = !user.blockedTimes.dataNotToModify.isEmpty

                    val shouldSignIn = Threads.database.executeAndWait {
                        AllowUserLoginStatusUtil.calculateSync(
                                logic = logic,
                                userId = user.id
                        ) is AllowUserLoginStatus.Allow
                    }

                    if (shouldSignIn) {
                        // this feature is limited to the local mode
                        model.setAuthenticatedUser(AuthenticatedUser(
                                userId = user.id,
                                firstPasswordHash = user.password,
                                secondPasswordHash = "device"
                        ))

                        if (hasBlockedTimes) {
                            Toast.makeText(getApplication(), R.string.manage_parent_blocked_times_toast, Toast.LENGTH_LONG).show()
                        }

                        isLoginDone.value = true
                    } else {
                        Toast.makeText(getApplication(), R.string.login_blocked_time, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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

                    val userEntryInfo = selectedUser.waitForNullableValue()
                    val userEntry = userEntryInfo?.loginRelatedData?.user

                    if (userEntry?.type != UserType.Parent) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val ownDeviceId = userEntryInfo.deviceRelatedData.deviceEntry.id

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

                    val hasBlockedTimes = !userEntry.blockedTimes.dataNotToModify.isEmpty
                    val shouldSignIn = Threads.database.executeAndWait {
                        AllowUserLoginStatusUtil.calculateSync(
                                logic = logic,
                                userId = userEntry.id
                        ) is AllowUserLoginStatus.Allow
                    }

                    if (!shouldSignIn) {
                        Toast.makeText(getApplication(), R.string.login_blocked_time, Toast.LENGTH_SHORT).show()

                        return@runAsync
                    }

                    model.setAuthenticatedUser(authenticatedUser)

                    if (hasBlockedTimes) {
                        Toast.makeText(getApplication(), R.string.manage_parent_blocked_times_toast, Toast.LENGTH_LONG).show()
                    }

                    if (setAsDeviceUser) {
                        if (userEntryInfo.deviceRelatedData.deviceEntry.currentUserId != userEntry.id) {
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
                                userEntryInfo.deviceRelatedData.deviceEntry.currentUserId == userEntry.id
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
            password: String
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntryInfo = selectedUser.waitForNullableValue()
                    val userEntry = userEntryInfo?.loginRelatedData?.user

                    if (userEntry?.type != UserType.Child) {
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
data class UserListLoginDialogStatus(val usersToShow: List<User>, val isLocalMode: Boolean): LoginDialogStatus()
object ParentUserLoginMissingTrustedTime: LoginDialogStatus()
object ParentUserLoginBlockedTime: LoginDialogStatus()
data class ParentUserLoginBlockedByCategory(val categoryTitle: String, val reason: BlockingReason): LoginDialogStatus()
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