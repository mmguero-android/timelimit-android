package io.timelimit.android.ui.backdoor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.HexString
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class BackdoorModel(application: Application): AndroidViewModel(application) {
    companion object {
        private val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(HexString.fromHex(BuildConfig.backdoorPublicKey)))

        private fun verify(input: String, signature: ByteArray): Boolean {
            Signature.getInstance("SHA512withRSA").apply {
                initVerify(publicKey)
                update(input.toByteArray())

                return verify(signature)
            }
        }
    }

    private val logic = DefaultAppLogic.with(application)

    private val environmentInfo = logic.fullVersion.isLocalMode.switchMap { isLocalMode ->
        if (isLocalMode) {
            liveDataFromValue("local-only")
        } else {
            logic.database.user().getParentUsersLive().map { parents ->
                "connected-" + (parents.map { it.mail }.filter { it.isNotEmpty() }.joinToString(separator = "-"))
            }
        }
    }

    private val deviceId = logic.deviceId
    private val statusInternal = MutableLiveData<RecoveryStatus>().apply { value = RecoveryStatus.WaitingForCode }

    val status = statusInternal.castDown()
    val nonce = environmentInfo.switchMap { environment ->
        deviceId.map { deviceId ->
            "$environment-${HexString.toHex((deviceId ?: "").toByteArray())}"
        }
    }

    fun validateSignatureAndReset(signature: ByteArray) {
        runAsync {
            statusInternal.value = RecoveryStatus.Verifying

            val sequence = nonce.waitForNonNullValue()
            val isValid = Threads.crypto.executeAndWait {
                verify(sequence, signature)
            }

            if (isValid) {
                logic.appSetupLogic.resetAppCompletely()

                statusInternal.value = RecoveryStatus.Done
            } else {
                statusInternal.value = RecoveryStatus.Invalid
            }
        }
    }

    fun confirmInvalidCode() {
        statusInternal.value = RecoveryStatus.WaitingForCode
    }
}

enum class RecoveryStatus {
    WaitingForCode,
    Verifying,
    Done,
    Invalid
}