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

package io.timelimit.android.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.JsonReader
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.waitForResponse
import io.timelimit.android.crypto.HexString
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.api.assertSuccess
import io.timelimit.android.sync.network.api.httpClient
import io.timelimit.android.ui.update.InstallUpdateDialogFragment
import okhttp3.Request
import okio.Okio
import java.io.File
import java.io.IOException
import java.security.MessageDigest


object UpdateIntegration {
    private const val LOG_TAG = "UpdateIntegration"

    private const val CERTIFICATE = BuildConfig.updateServerBuildsCertificate
    const val CONFIG_URL = BuildConfig.updateServer

    fun doesSupportUpdates(context: Context): Boolean {
        if (CONFIG_URL.isEmpty() || BuildConfig.storeCompilant) {
            return false
        }

        if (CERTIFICATE.isEmpty()) {
            return true
        }

        val signatures = getApplicationSignature(context)

        return signatures.find { it.equals(CERTIFICATE, ignoreCase = true) } != null
    }

    private fun getApplicationSignature(context: Context): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // new signature

            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
        } else {
            // old signature
            // this is "unsafe", but it is not used for security features

            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }

        return signatures.map { HexString.toHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
    }

    fun updateSaveFile(context: Context): File = File(context.filesDir, "update.apk")

    suspend fun deleteUpdateFile(context: Context) {
        Threads.update.executeAndWait {
            updateSaveFile(context).delete()
        }
    }

    private suspend fun isDownloadedFileValid(status: UpdateStatus, context: Context): Boolean = Threads.update.executeAndWait {
        isDownloadedFileValidSync(status, context)
    }

    fun isDownloadedFileValidSync(status: UpdateStatus, context: Context): Boolean {
        if (!doesSupportUpdates(context)) {
            return false
        }

        return try {
            val file = updateSaveFile(context)
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)!!

            if (
                    info.packageName != BuildConfig.APPLICATION_ID ||
                    info.versionCode != status.versionCode ||
                    info.versionCode < BuildConfig.VERSION_CODE
            ) {
                // no need to check the signatures here, the package installer will do it

                false
            } else {
                val digest = MessageDigest.getInstance("SHA-512")

                val buffer = ByteArray(1024 * 1024)

                file.inputStream().use { stream ->
                    while (true) {
                        val length = stream.read(buffer)

                        if (length < 0) {
                            break
                        } else {
                            digest.update(buffer, 0, length)
                        }
                    }
                }

                HexString.toHex(digest.digest()).equals(status.sha512, ignoreCase = true)
            }
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "could not verify APK", ex)
            }

            false
        }
    }

    suspend fun getUpdateStatus(context: Context): UpdateStatus {
        if (!doesSupportUpdates(context)) {
            throw IOException()
        }

        httpClient.newCall(
                Request.Builder().url(CONFIG_URL).build()
        ).waitForResponse().use { response ->
            response.assertSuccess()

            return Threads.update.executeAndWait {
                JsonReader(response.body()!!.charStream()).use { reader ->
                    UpdateStatus.parse(reader)
                }
            }
        }
    }

    suspend fun downloadAndVerifyUpdate(status: UpdateStatus, context: Context) {
        if (isDownloadedFileValid(status, context)) {
            return
        }

        httpClient.newCall(
                Request.Builder().url(status.url).build()
        ).waitForResponse().use { response ->
            response.assertSuccess()

            Threads.update.executeAndWait {
                response.body()!!.source().use { source ->
                    Okio.buffer(Okio.sink(updateSaveFile(context))).use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        }

        if (!isDownloadedFileValid(status, context)) {
            deleteUpdateFile(context)

            throw IOException()
        }
    }

    fun hasRequiredPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context.packageManager.canRequestPackageInstalls() ||
                DefaultAppLogic.with(context).platformIntegration.getCurrentProtectionLevel() == ProtectionLevel.DeviceOwner
    else
        true

    fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Toast.makeText(context, R.string.update_toast_needs_permission, Toast.LENGTH_LONG).show()

            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:${BuildConfig.APPLICATION_ID}")));
        }
    }

    fun installUpdate(fragmentActivity: FragmentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // compatibility version for old android versions
            // it needs a file uri
            // someone else could replace the APK before it is installed ...
            val context = fragmentActivity.applicationContext
            val externalFile = File(context.externalCacheDir, "update.apk")

            Threads.update.submit {
                try {
                    Okio.buffer(Okio.sink(externalFile)).use { sink ->
                        Okio.source(updateSaveFile(context)).use { source ->
                            sink.writeAll(source)
                        }
                    }

                    Threads.mainThreadHandler.post {
                        try {
                            context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .setData(Uri.fromFile(externalFile))
                            )
                        } catch (ex: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.w(LOG_TAG, "error during installation", ex)
                            }

                            Threads.mainThreadHandler.post {
                                Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "error during installation", ex)
                    }

                    Threads.mainThreadHandler.post {
                        Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            InstallUpdateDialogFragment().show(fragmentActivity.supportFragmentManager)
        }
    }
}