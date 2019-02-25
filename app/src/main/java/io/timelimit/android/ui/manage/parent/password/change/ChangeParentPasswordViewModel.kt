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
package io.timelimit.android.ui.manage.parent.password.change

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.*
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChangeParentPasswordAction
import io.timelimit.android.sync.actions.apply.ApplyActionParentPasswordAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.sync.network.ParentPassword
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChangeParentPasswordViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ChangeParentPassword"
    }

    private val statusInternal = MutableLiveData<ChangeParentPasswordViewModelStatus>().apply {
        value = ChangeParentPasswordViewModelStatus.Idle
    }

    private val logic = DefaultAppLogic.with(application)

    val status = statusInternal.castDown()

    fun confirmError() {
        val value = statusInternal.value

        if (value == ChangeParentPasswordViewModelStatus.Failed || value == ChangeParentPasswordViewModelStatus.WrongPassword) {
            statusInternal.value = ChangeParentPasswordViewModelStatus.Idle
        }
    }

    fun changePassword(parentUserId: String, oldPassword: String, newPassword: String) {
        runAsync {
            try {
                if (statusInternal.value != ChangeParentPasswordViewModelStatus.Idle) {
                    return@runAsync
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Working

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "start changePassword()")
                }

                val userEntry = logic.database.user().getUserByIdLive(parentUserId).waitForNullableValue()

                if (userEntry == null || userEntry.type != UserType.Parent) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got userEntry")
                }

                val isOldPasswordCorrect = Threads.crypto.executeAndWait {
                    PasswordHashing.validateSync(oldPassword, userEntry.password)
                }

                if (!isOldPasswordCorrect) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.WrongPassword
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "old password is valid")
                }

                val oldPasswordSecondHash = Threads.crypto.executeAndWait {
                    PasswordHashing.hashSyncWithSalt(oldPassword, userEntry.secondPasswordSalt)
                }

                val newPasswordHash = ParentPassword.createCoroutine(newPassword)

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "created hashs")
                }

                val encryptedSecondHash = Threads.crypto.executeAndWait {
                    val salt = HexString.toHex(RandomBytes.randomBytes(16))

                    val keyData = Sha512.hashSync((oldPasswordSecondHash + salt).toByteArray(Charset.forName("UTF-8")))
                    val keySpec = SecretKeySpec(keyData.slice(0..(AES.KEY_LENGTH_BYTES - 1)).toByteArray(), "AES")
                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")

                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(RandomBytes.randomBytes(AES.IV_LENGTH_BYTES)))
                    val iv = cipher.iv  // 16 bytes

                    val encryptedSecondHash = cipher.doFinal(newPasswordHash.parentPasswordSecondHash.toByteArray(Charset.forName("UTF-8")))    // unknown length

                    // 32 chars IV, 32 chars salt, encrypted string as hex
                    val resultString = HexString.toHex(iv) + salt + HexString.toHex(encryptedSecondHash)

                    resultString
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "encrypted data")
                }

                val integrity = Threads.crypto.executeAndWait {
                    Sha512.hashSync(oldPasswordSecondHash + parentUserId + newPasswordHash.parentPasswordHash + newPasswordHash.parentPasswordSecondSalt + encryptedSecondHash)
                }

                val action = ChangeParentPasswordAction(
                        parentUserId = parentUserId,
                        newPasswordFirstHash = newPasswordHash.parentPasswordHash,
                        newPasswordSecondSalt = newPasswordHash.parentPasswordSecondSalt,
                        newPasswordSecondHashEncrypted = encryptedSecondHash,
                        integrity = integrity
                )

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "created action")
                }

                val currentUserEntry = logic.database.user().getUserByIdLive(parentUserId).waitForNullableValue()

                if (currentUserEntry == null || currentUserEntry.password != userEntry.password || currentUserEntry.secondPasswordSalt != userEntry.secondPasswordSalt) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "validated user a second time")
                }

                ApplyActionUtil.applyParentAction(
                        action,
                        logic.database,
                        ApplyActionParentPasswordAuthentication(
                                parentUserId = parentUserId,
                                secondPasswordHash = oldPasswordSecondHash
                        ),
                        logic.syncUtil,
                        logic.platformIntegration
                )

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "applied action")
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "changing password failed", ex)
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
            }
        }
    }
}

enum class ChangeParentPasswordViewModelStatus {
    Idle, Working, Failed, WrongPassword, Done
}
