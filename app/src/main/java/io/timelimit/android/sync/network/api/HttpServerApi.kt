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
package io.timelimit.android.sync.network.api

import android.util.JsonReader
import android.util.JsonWriter
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.waitForResponse
import io.timelimit.android.sync.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import java.io.OutputStreamWriter

class HttpServerApi(private val endpointWithoutSlashAtEnd: String): ServerApi {
    companion object {
        fun createInstance(url: String) = HttpServerApi(url.removeSuffix("/"))

        private const val DEVICE_AUTH_TOKEN = "deviceAuthToken"
        private const val GOOGLE_AUTH_TOKEN = "googleAuthToken"
        private const val MAIL_AUTH_TOKEN = "mailAuthToken"
        private const val REGISTER_TOKEN = "registerToken"
        private const val MILLISECONDS = "ms"
        private const val STATUS = "status"
        private const val PASSWORD = "password"
        private const val PARENT_PASSWORD = "parentPassword"
        private const val PARENT_DEVICE = "parentDevice"
        private const val CHILD_DEVICE = "childDevice"
        private const val DEVICE_NAME = "deviceName"
        private const val TIMEZONE = "timeZone"
        private const val PARENT_NAME = "parentName"
        private const val PARENT_ID = "parentId"
        private const val PARENT_USER_ID = "parentUserId"
        private const val PARENT_PASSWORD_SECOND_HASH = "parentPasswordSecondHash"
        private const val DEVICE_ID = "deviceId"
        private const val MAIL = "mail"
        private const val LOCALE = "locale"
        private const val MAIL_LOGIN_TOKEN = "mailLoginToken"
        private const val RECEIVED_CODE = "receivedCode"

        private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()

        private fun createJsonRequestBody(serialize: (writer: JsonWriter) -> Unit) = object: RequestBody() {
            override fun contentType() = JSON
            override fun writeTo(sink: BufferedSink) {
                val writer = JsonWriter(
                        OutputStreamWriter(
                                GzipSink(sink)
                                        .buffer().outputStream()
                        )
                )

                serialize(writer)

                writer.flush()
                writer.close()
            }
        }
    }

    override suspend fun getTimeInMillis(): Long {
        httpClient.newCall(
                Request.Builder()
                        .get()
                        .url("$endpointWithoutSlashAtEnd/time")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            return Threads.network.executeAndWait {
                val body = it.body!!
                val reader = JsonReader(body.charStream())
                var result: Long? = null

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        MILLISECONDS -> result = reader.nextLong()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                result!!
            }
        }
    }

    override suspend fun sendMailLoginCode(mail: String, locale: String): String {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/auth/send-mail-login-code-v2")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()
                            writer.name(MAIL).value(mail)
                            writer.name(LOCALE).value(locale)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                var response: String? = null

                JsonReader(body.charStream()).use { reader ->
                    reader.beginObject()

                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            MAIL_LOGIN_TOKEN -> response = reader.nextString()
                            "mailServerBlacklisted" -> {
                                if (reader.nextBoolean()) {
                                    throw MailServerBlacklistedException()
                                }
                            }
                            "mailAddressNotWhitelisted" -> {
                                if (reader.nextBoolean()) {
                                    throw MailAddressNotWhitelistedException()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }

                    reader.endObject()
                }

                response!!
            }
        }
    }

    override suspend fun signInByMailCode(mailLoginToken: String, code: String): String {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/auth/sign-in-by-mail-code")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()
                            writer.name(MAIL_LOGIN_TOKEN).value(mailLoginToken)
                            writer.name(RECEIVED_CODE).value(code)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                var response: String? = null

                JsonReader(body.charStream()).use { reader ->
                    reader.beginObject()

                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            MAIL_AUTH_TOKEN -> response = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }

                    reader.endObject()
                }

                response!!
            }
        }
    }

    override suspend fun getStatusByMailToken(mailAuthToken: String): StatusOfMailAddressResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/get-status-by-mail-address")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()
                            writer.name(MAIL_AUTH_TOKEN).value(mailAuthToken)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                StatusOfMailAddressResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun createFamilyByMailToken(mailToken: String, parentPassword: ParentPassword, parentDevice: NewDeviceInfo, timeZone: String, parentName: String, deviceName: String): AddDeviceResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/create-family")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(MAIL_AUTH_TOKEN).value(mailToken)
                            writer.name(TIMEZONE).value(timeZone)
                            writer.name(PARENT_NAME).value(parentName)
                            writer.name(DEVICE_NAME).value(deviceName)

                            writer.name(PARENT_PASSWORD)
                            parentPassword.serialize(writer)

                            writer.name(PARENT_DEVICE)
                            parentDevice.serialize(writer)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                AddDeviceResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun signInToFamilyByMailToken(mailToken: String, parentDevice: NewDeviceInfo, deviceName: String): AddDeviceResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/sign-in-into-family")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(MAIL_AUTH_TOKEN).value(mailToken)
                            writer.name(DEVICE_NAME).value(deviceName)

                            writer.name(PARENT_DEVICE)
                            parentDevice.serialize(writer)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                AddDeviceResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun recoverPasswordByMailToken(mailToken: String, parentPassword: ParentPassword) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/recover-parent-password")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(MAIL_AUTH_TOKEN).value(mailToken)

                            writer.name(PASSWORD)
                            parentPassword.serialize(writer)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()
        }
    }

    override suspend fun registerChildDevice(registerToken: String, childDeviceInfo: NewDeviceInfo, deviceName: String): AddDeviceResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/child/add-device")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(REGISTER_TOKEN).value(registerToken)

                            writer.name(CHILD_DEVICE)
                            childDeviceInfo.serialize(writer)

                            writer.name(DEVICE_NAME).value(deviceName)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                AddDeviceResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun pushChanges(request: ActionUploadRequest): ActionUploadResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/sync/push-actions")
                        .post(createJsonRequestBody{ request.serialize(it) })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                ActionUploadResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun pullChanges(deviceAuthToken: String, status: ClientDataStatus): ServerDataStatus {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/sync/pull-status")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)

                            writer.name(STATUS)
                            status.serialize(writer)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                ServerDataStatus.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun createAddDeviceToken(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String): CreateAddDeviceTokenResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/create-add-device-token")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.name(PARENT_ID).value(parentUserId)
                            writer.name(PARENT_PASSWORD_SECOND_HASH).value(parentPasswordSecondHash)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            it.assertSuccess()

            val body = it.body!!

            return Threads.network.executeAndWait {
                CreateAddDeviceTokenResponse.parse(JsonReader(body.charStream()))
            }
        }
    }

    override suspend fun canDoPurchase(deviceAuthToken: String): CanDoPurchaseStatus {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/purchase/can-do-purchase")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.name("type").value("googleplay")

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()

            return Threads.network.executeAndWait {
                val reader = JsonReader(response.body!!.charStream())

                CanDoPurchaseParser.parse(reader)
            }
        }
    }

    override suspend fun finishPurchaseByGooglePlay(receipt: String, signature: String, deviceAuthToken: String) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/purchase/finish-purchase-by-google-play")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name("receipt").value(receipt)
                            writer.name("signature").value(signature)
                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()
        }
    }

    override suspend fun linkParentMailAddress(mailAuthToken: String, deviceAuthToken: String, parentUserId: String, secondPasswordHash: String) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/link-mail-address")
                        .post(createJsonRequestBody {
                            writer ->

                            writer.beginObject()

                            writer.name(MAIL_AUTH_TOKEN).value(mailAuthToken)
                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.name(PARENT_USER_ID).value(parentUserId)
                            writer.name(PARENT_PASSWORD_SECOND_HASH).value(secondPasswordHash)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()
        }
    }

    override suspend fun updatePrimaryDevice(request: UpdatePrimaryDeviceRequest): UpdatePrimaryDeviceResponse {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/child/update-primary-device")
                        .post(createJsonRequestBody { writer ->
                            request.serialize(writer)
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()

            return Threads.network.executeAndWait {
                val reader = JsonReader(response.body!!.charStream())

                UpdatePrimaryDeviceResponse.parse(reader)
            }
        }
    }

    override suspend fun requestSignOutAtPrimaryDevice(deviceAuthToken: String) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/child/logout-at-primary-device")
                        .post(createJsonRequestBody { writer ->
                            writer.beginObject()

                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)

                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use { response ->
            response.assertSuccess()

            return Threads.network.executeAndWait {
                val reader = JsonReader(response.body!!.charStream())

                reader.skipValue()
            }
        }
    }

    override suspend fun reportDeviceRemoved(deviceAuthToken: String) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/sync/report-removed")
                        .post(createJsonRequestBody { writer ->
                            writer.beginObject()
                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()
        }
    }

    override suspend fun removeDevice(deviceAuthToken: String, parentUserId: String, parentPasswordSecondHash: String, deviceId: String) {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/parent/remove-device")
                        .post(createJsonRequestBody { writer ->
                            writer.beginObject()
                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.name(PARENT_USER_ID).value(parentUserId)
                            writer.name(PARENT_PASSWORD_SECOND_HASH).value(parentPasswordSecondHash)
                            writer.name(DEVICE_ID).value(deviceId)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()
        }
    }

    override suspend fun isDeviceRemoved(deviceAuthToken: String): Boolean {
        httpClient.newCall(
                Request.Builder()
                        .url("$endpointWithoutSlashAtEnd/sync/is-device-removed")
                        .post(createJsonRequestBody { writer ->
                            writer.beginObject()
                            writer.name(DEVICE_AUTH_TOKEN).value(deviceAuthToken)
                            writer.endObject()
                        })
                        .header("Content-Encoding", "gzip")
                        .build()
        ).waitForResponse().use {
            response ->

            response.assertSuccess()

            return Threads.network.executeAndWait {
                val reader = JsonReader(response.body!!.charStream())
                var result: Boolean? = null

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "isDeviceRemoved" -> result = reader.nextBoolean()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                result!!
            }
        }
    }
}
