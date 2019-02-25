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
package io.timelimit.android.sync.actions.apply

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.data.transaction
import io.timelimit.android.logic.ServerLogic
import io.timelimit.android.sync.network.ActionUploadItem
import io.timelimit.android.sync.network.ActionUploadRequest

class UploadActionsUtil(private val database: Database) {
    companion object {
        private const val BATCH_SIZE = 25

        fun deleteAllVersionNumbersSync(database: Database) {
            database.transaction().use {
                transaction ->

                database.config().setUserListVersionSync("")
                database.config().setDeviceListVersionSync("")
                database.device().deleteAllInstalledAppsVersions()
                database.category().deleteAllCategoriesVersionNumbers()

                transaction.setSuccess()
            }
        }
    }

    suspend fun uploadActions(server: ServerLogic.ServerConfig) {
        while (uploadActionsRound(server)) {
            // do nothing
        }
    }

    private suspend fun uploadActionsRound(server: ServerLogic.ServerConfig): Boolean {
        val didUploadSomething = handleUploadIfItemsWhileAvailable(server)
        val didPrepareSomething = markItemsForUploadingIfNeeded()

        return didUploadSomething or didPrepareSomething
    }

    private suspend fun markItemsForUploadingIfNeeded(): Boolean {
        return Threads.database.executeAndWait {
            database.transaction().use {
                transaction ->

                val preparedItems = database.pendingSyncAction().countScheduledActionsSync()
                val unpreparedItems = database.pendingSyncAction().countUnscheduledActionsSync()
                val missingItems = BATCH_SIZE - preparedItems

                if (missingItems > 0 && unpreparedItems > 0) {
                    val items = database.pendingSyncAction().getNextUnscheduledActionsSync(missingItems.toInt())
                    database.pendingSyncAction().markSyncActionsAsScheduledForUpload(items.last().sequenceNumber)

                    transaction.setSuccess()
                    true
                } else {
                    false
                }
            }
        }
    }

    private suspend fun handleUploadIfItemsWhileAvailable(server: ServerLogic.ServerConfig): Boolean {
        var hadOneIteration = false

        while (handleUploadIfItemsAreAvailable(server)) {
            hadOneIteration = true
        }

        return hadOneIteration
    }

    private suspend fun handleUploadIfItemsAreAvailable(server: ServerLogic.ServerConfig): Boolean {
        val pendingItems = Threads.database.executeAndWait {
            database.pendingSyncAction().getScheduledActionsSync(BATCH_SIZE)
        }

        if (pendingItems.isEmpty()) {
            return false
        }

        val response = server.api.pushChanges(
                ActionUploadRequest(
                        deviceAuthToken = server.deviceAuthToken,
                        actions = pendingItems.map {
                            ActionUploadItem(
                                    encodedAction = it.encodedAction,
                                    integrity = it.integrity,
                                    sequenceNumber = it.sequenceNumber,
                                    type = it.type,
                                    userId = it.userId
                            )
                        }
                )
        )

        Threads.database.executeAndWait {
            database.transaction().use {
                transaction ->

                if (response.shouldDoFullSync) {
                    deleteAllVersionNumbersSync(database)
                }

                // delete now processed items
                database.pendingSyncAction().removeSyncActionsBySequenceNumbersSync(pendingItems.map { it.sequenceNumber })

                transaction.setSuccess()
            }
        }

        return true
    }
}