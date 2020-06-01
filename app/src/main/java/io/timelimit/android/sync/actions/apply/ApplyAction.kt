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
package io.timelimit.android.sync.actions.apply

import android.util.JsonWriter
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.Sha512
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.PendingSyncAction
import io.timelimit.android.data.model.PendingSyncActionType
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.ManipulationLogic
import io.timelimit.android.sync.SyncUtil
import io.timelimit.android.sync.actions.*
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseAppLogicActionDispatcher
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseChildActionDispatcher
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseParentActionDispatcher
import org.json.JSONObject
import java.io.StringWriter

object ApplyActionUtil {
    private const val LOG_TAG = "ApplyActionUtil"

    suspend fun applyAppLogicAction(
            action: AppLogicAction,
            appLogic: AppLogic,
            ignoreIfDeviceIsNotConfigured: Boolean
    ) {
        applyAppLogicAction(
                action = action,
                database = appLogic.database,
                syncUtil = appLogic.syncUtil,
                manipulationLogic = appLogic.manipulationLogic,
                ignoreIfDeviceIsNotConfigured = ignoreIfDeviceIsNotConfigured
        )
    }

    private suspend fun applyAppLogicAction(
            action: AppLogicAction,
            database: Database,
            syncUtil: SyncUtil,
            manipulationLogic: ManipulationLogic,
            ignoreIfDeviceIsNotConfigured: Boolean
    ) {
        // uncomment this if you need to know what's dispatching an action
        /*
        if (BuildConfig.DEBUG) {
            try {
                throw Exception()
            } catch (ex: Exception) {
                Log.d(LOG_TAG, "handling action: $action", ex)
            }
        }
        */

        Threads.database.executeAndWait {
            database.runInTransaction {
                val ownDeviceId = database.config().getOwnDeviceIdSync()

                if (ownDeviceId == null && ignoreIfDeviceIsNotConfigured) {
                    return@runInTransaction
                }

                LocalDatabaseAppLogicActionDispatcher.dispatchAppLogicActionSync(action, ownDeviceId!!, database, manipulationLogic)

                if (isSyncEnabled(database)) {
                    if (action is AddUsedTimeAction) {
                        val previousAction = database.pendingSyncAction().getLatestUnscheduledActionSync()

                        if (previousAction != null && previousAction.type == PendingSyncActionType.AppLogic) {
                            val parsed = ActionParser.parseAppLogicAction(JSONObject(previousAction.encodedAction))

                            if (parsed is AddUsedTimeAction && parsed.categoryId == action.categoryId && parsed.dayOfEpoch == action.dayOfEpoch) {
                                // update the previous action
                                database.pendingSyncAction().updateEncodedActionSync(
                                        sequenceNumber = previousAction.sequenceNumber,
                                        action = StringWriter().apply {
                                            JsonWriter(this).apply {
                                                parsed.copy(
                                                        timeToAdd = parsed.timeToAdd + action.timeToAdd,
                                                        extraTimeToSubtract = parsed.extraTimeToSubtract + action.extraTimeToSubtract
                                                ).serialize(this)
                                            }
                                        }.toString()
                                )

                                syncUtil.requestVeryUnimportantSync()

                                return@runInTransaction
                            }
                        }
                    } else if (action is AddUsedTimeActionVersion2) {
                        val previousAction = database.pendingSyncAction().getLatestUnscheduledActionSync()

                        if (previousAction != null && previousAction.type == PendingSyncActionType.AppLogic) {
                            val parsed = ActionParser.parseAppLogicAction(JSONObject(previousAction.encodedAction))

                            if (parsed is AddUsedTimeActionVersion2 && parsed.dayOfEpoch == action.dayOfEpoch) {
                                var updatedAction: AddUsedTimeActionVersion2 = parsed
                                var issues = false

                                if (parsed.trustedTimestamp != 0L && action.trustedTimestamp != 0L) {
                                    issues = action.items.map { it.categoryId } != parsed.items.map { it.categoryId } ||
                                            parsed.trustedTimestamp >= action.trustedTimestamp

                                    updatedAction = updatedAction.copy(trustedTimestamp = action.trustedTimestamp)

                                    // keep timestamp of the old action
                                } else if (parsed.trustedTimestamp != 0L || action.trustedTimestamp != 0L) {
                                    issues = true
                                }

                                action.items.forEach { newItem ->
                                    if (issues) return@forEach

                                    val oldItem = updatedAction.items.find { it.categoryId == newItem.categoryId }

                                    if (oldItem == null) {
                                        updatedAction = updatedAction.copy(
                                                items = updatedAction.items + listOf(newItem)
                                        )
                                    } else {
                                        if (
                                                oldItem.additionalCountingSlots != newItem.additionalCountingSlots ||
                                                oldItem.sessionDurationLimits != newItem.sessionDurationLimits
                                        ) {
                                            issues = true
                                        }

                                        if (parsed.trustedTimestamp != 0L && action.trustedTimestamp != 0L) {
                                            val timeBeforeCurrentItem = action.trustedTimestamp - newItem.timeToAdd
                                            val diff = Math.abs(timeBeforeCurrentItem - parsed.trustedTimestamp)

                                            if (diff > 2 * 1000) {
                                                issues = true
                                            }
                                        }

                                        val mergedItem = AddUsedTimeActionItem(
                                                timeToAdd = oldItem.timeToAdd + newItem.timeToAdd,
                                                extraTimeToSubtract = oldItem.extraTimeToSubtract + newItem.extraTimeToSubtract,
                                                categoryId = newItem.categoryId,
                                                additionalCountingSlots = oldItem.additionalCountingSlots,
                                                sessionDurationLimits = oldItem.sessionDurationLimits
                                        )

                                        updatedAction = updatedAction.copy(
                                                items = updatedAction.items.filter { it.categoryId != mergedItem.categoryId } + listOf(mergedItem)
                                        )
                                    }
                                }

                                if (!issues) {
                                    // update the previous action
                                    database.pendingSyncAction().updateEncodedActionSync(
                                            sequenceNumber = previousAction.sequenceNumber,
                                            action = StringWriter().apply {
                                                JsonWriter(this).apply {
                                                    updatedAction.serialize(this)
                                                }
                                            }.toString()
                                    )

                                    syncUtil.requestVeryUnimportantSync()

                                    return@runInTransaction
                                }
                            }
                        }
                    }

                    val serializedAction = StringWriter().apply {
                        JsonWriter(this).apply {
                            action.serialize(this)
                        }
                    }.toString()

                    database.pendingSyncAction().addSyncActionSync(PendingSyncAction(
                            sequenceNumber = database.config().getNextSyncActionSequenceActionAndIncrementIt(),
                            encodedAction = serializedAction,
                            integrity = "",
                            scheduledForUpload = false,
                            type = PendingSyncActionType.AppLogic,
                            userId = ""
                    ))

                    if (action is AddUsedTimeAction || action is AddUsedTimeActionVersion2) {
                        syncUtil.requestVeryUnimportantSync()
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "request important sync due to dispatched app logic action")
                        }

                        syncUtil.requestImportantSync()
                    }
                }
            }
        }
    }

    suspend fun applyParentAction(action: ParentAction, database: Database, authentication: ApplyActionParentAuthentication, syncUtil: SyncUtil, platformIntegration: PlatformIntegration) {
        Threads.database.executeAndWait {
            database.runInTransaction {
                val deviceUserIdBeforeDispatchingForDeviceAuth = if (authentication is ApplyActionParentDeviceAuthentication) {
                    val deviceId = database.config().getOwnDeviceIdSync()!!
                    val device = database.device().getDeviceByIdSync(deviceId)!!
                    val user = database.user().getUserByIdSync(device.currentUserId)

                    if (user?.type != UserType.Parent) {
                        throw IllegalStateException("no parent assigned to device")
                    }

                    if (!device.isUserKeptSignedIn) {
                        throw IllegalArgumentException("user is not kept signed in")
                    }

                    user.id
                } else null

                LocalDatabaseParentActionDispatcher.dispatchParentActionSync(action, database)

                if (action is SetDeviceUserAction) {
                    val thisDeviceId = database.config().getOwnDeviceIdSync()!!

                    if (action.deviceId == thisDeviceId) {
                        platformIntegration.stopSuspendingForAllApps()
                    }
                }

                if (isSyncEnabled(database)) {
                    val serializedAction = StringWriter().apply {
                        JsonWriter(this).apply {
                            action.serialize(this)
                        }
                    }.toString()

                    val sequenceNumber = database.config().getNextSyncActionSequenceActionAndIncrementIt()

                    val pendingAction = when (authentication) {
                        is ApplyActionParentPasswordAuthentication -> {
                            val integrityData = sequenceNumber.toString() +
                                    database.config().getOwnDeviceIdSync() +
                                    authentication.secondPasswordHash +
                                    serializedAction

                            val hashedIntegrityData = Sha512.hashSync(integrityData)

                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "integrity data: $integrityData")
                                Log.d(LOG_TAG, "integrity hash: $hashedIntegrityData")
                            }

                            PendingSyncAction(
                                    sequenceNumber = sequenceNumber,
                                    encodedAction = serializedAction,
                                    integrity = hashedIntegrityData,
                                    scheduledForUpload = false,
                                    type = PendingSyncActionType.Parent,
                                    userId = authentication.parentUserId
                            )
                        }
                        ApplyActionParentDeviceAuthentication -> {
                            PendingSyncAction(
                                    sequenceNumber = sequenceNumber,
                                    encodedAction = serializedAction,
                                    integrity = "device",
                                    scheduledForUpload = false,
                                    type = PendingSyncActionType.Parent,
                                    userId = deviceUserIdBeforeDispatchingForDeviceAuth!!
                            )
                        }
                    }

                    database.pendingSyncAction().addSyncActionSync(pendingAction)

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "request important sync due to dispatched parent action")
                    }

                    syncUtil.requestImportantSync(enqueueIfOffline = true)
                }
            }
        }
    }

    suspend fun applyChildAction(action: ChildAction, database: Database, authentication: ApplyActionChildAuthentication, syncUtil: SyncUtil) {
        Threads.database.executeAndWait {
            database.runInTransaction {
                LocalDatabaseChildActionDispatcher.dispatchChildActionSync(action, authentication.childUserId, database)

                if (isSyncEnabled(database)) {
                    val serializedAction = StringWriter().apply {
                        JsonWriter(this).apply {
                            action.serialize(this)
                        }
                    }.toString()

                    val sequenceNumber = database.config().getNextSyncActionSequenceActionAndIncrementIt()

                    val integrityData = sequenceNumber.toString() +
                            database.config().getOwnDeviceIdSync() +
                            authentication.secondPasswordHash +
                            serializedAction

                    val hashedIntegrityData = Sha512.hashSync(integrityData)

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "integrity data: $integrityData")
                        Log.d(LOG_TAG, "integrity hash: $hashedIntegrityData")
                    }

                    database.pendingSyncAction().addSyncActionSync(
                            PendingSyncAction(
                                    sequenceNumber = sequenceNumber,
                                    encodedAction = serializedAction,
                                    integrity = hashedIntegrityData,
                                    scheduledForUpload = false,
                                    type = PendingSyncActionType.Child,
                                    userId = authentication.childUserId
                            )
                    )

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "request important sync due to dispatched child action")
                    }

                    syncUtil.requestImportantSync(enqueueIfOffline = true)
                }
            }
        }
    }

    private fun isSyncEnabled(database: Database): Boolean {
        return database.config().getDeviceAuthTokenSync() != ""
    }
}

sealed class ApplyActionParentAuthentication
object ApplyActionParentDeviceAuthentication: ApplyActionParentAuthentication()
data class ApplyActionParentPasswordAuthentication(val parentUserId: String, val secondPasswordHash: String): ApplyActionParentAuthentication()

data class ApplyActionChildAuthentication(val childUserId: String, val secondPasswordHash: String)