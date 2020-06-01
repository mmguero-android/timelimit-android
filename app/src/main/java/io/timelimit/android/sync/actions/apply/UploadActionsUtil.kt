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

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.logic.ServerLogic
import io.timelimit.android.sync.network.ActionUploadItem
import io.timelimit.android.sync.network.ActionUploadRequest

class UploadActionsUtil(private val database: Database, private val syncConflictHandler: () -> Unit) {
    companion object {
        private const val BATCH_SIZE = 25

        fun deleteAllVersionNumbersSync(database: Database) {
            database.runInTransaction {
                database.config().setUserListVersionSync("")
                database.config().setDeviceListVersionSync("")
                database.device().deleteAllInstalledAppsVersions()
                database.category().deleteAllCategoriesVersionNumbers()
            }
        }
    }

    suspend fun uploadActions(server: ServerLogic.ServerConfig) {
        val highestSequenceNumber = Threads.database.executeAndWait { database.pendingSyncAction().getMaxSequenceNumber() }

        if (highestSequenceNumber != null) {
            while (uploadActionsRound(server, highestSequenceNumber)) {
                // do nothing
            }
        }
    }

    private suspend fun uploadActionsRound(server: ServerLogic.ServerConfig, highestSequenceNumber: Long): Boolean {
        val didUploadSomething = handleUploadIfItemsWhileAvailable(server)
        val didPrepareSomething = markItemsForUploadingIfNeeded(highestSequenceNumber)

        return didUploadSomething or didPrepareSomething
    }

    private suspend fun markItemsForUploadingIfNeeded(highestSequenceNumber: Long): Boolean {
        return Threads.database.executeAndWait {
            database.runInTransaction {
                val preparedItems = database.pendingSyncAction().countScheduledActionsSync(highestSequenceNumber)
                val unpreparedItems = database.pendingSyncAction().countUnscheduledActionsSync(highestSequenceNumber)
                val missingItems = BATCH_SIZE - preparedItems

                if (missingItems > 0 && unpreparedItems > 0) {
                    val items = database.pendingSyncAction().getNextUnscheduledActionsSync(missingItems.toInt())
                    database.pendingSyncAction().markSyncActionsAsScheduledForUpload(items.last().sequenceNumber)

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
            database.runInTransaction {
                if (response.shouldDoFullSync) {
                    deleteAllVersionNumbersSync(database)

                    Threads.mainThreadHandler.post { syncConflictHandler() }
                }

                // delete now processed items
                database.pendingSyncAction().removeSyncActionsBySequenceNumbersSync(pendingItems.map { it.sequenceNumber })
            }
        }

        return true
    }
}