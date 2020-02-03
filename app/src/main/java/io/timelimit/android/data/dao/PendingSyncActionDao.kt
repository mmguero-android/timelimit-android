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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.PendingSyncAction

@Dao
interface PendingSyncActionDao {
    @Insert
    fun addSyncActionSync(action: PendingSyncAction)

    @Query("DELETE FROM pending_sync_action WHERE sequence_number IN (:sequenceNumbers)")
    fun removeSyncActionsBySequenceNumbersSync(sequenceNumbers: List<Long>)

    @Query("UPDATE pending_sync_action SET `action` = :action WHERE sequence_number = :sequenceNumber")
    fun updateEncodedActionSync(sequenceNumber: Long, action: String)

    @Query("UPDATE pending_sync_action SET scheduled_for_upload = 1 WHERE sequence_number <= :highestSequenceNumberToMark")
    fun markSyncActionsAsScheduledForUpload(highestSequenceNumberToMark: Long)

    @Query("SELECT * FROM pending_sync_action WHERE scheduled_for_upload = 0 ORDER BY sequence_number ASC LIMIT :limit")
    fun getNextUnscheduledActionsSync(limit: Int): List<PendingSyncAction>

    @Query("SELECT * FROM pending_sync_action WHERE scheduled_for_upload = 1 ORDER BY sequence_number ASC LIMIT :limit")
    fun getScheduledActionsSync(limit: Int): List<PendingSyncAction>

    @Query("SELECT COUNT(*) FROM pending_sync_action WHERE scheduled_for_upload = 1 AND sequence_number <= :maxSequenceNumber")
    fun countScheduledActionsSync(maxSequenceNumber: Long): Long

    @Query("SELECT COUNT(*) FROM pending_sync_action WHERE scheduled_for_upload = 0 AND sequence_number <= :maxSequenceNumber")
    fun countUnscheduledActionsSync(maxSequenceNumber: Long): Long

    @Query("SELECT COUNT(*) FROM pending_sync_action")
    fun countAllActionsLive(): LiveData<Long>

    @Query("SELECT * FROM pending_sync_action WHERE scheduled_for_upload = 0 ORDER BY sequence_number DESC")
    fun getLatestUnscheduledActionSync(): PendingSyncAction?

    @Query("SELECT MAX(sequence_number) FROM pending_sync_action")
    fun getMaxSequenceNumber(): Long?

    @Query("SELECT * FROM pending_sync_action LIMIT :pageSize OFFSET :offset")
    fun getPendingSyncActionPageSync(offset: Int, pageSize: Int): List<PendingSyncAction>

    @Query("SELECT * FROM pending_sync_action ORDER BY sequence_number ASC")
    fun getAllPendingSyncActionsPaged(): DataSource.Factory<Int, PendingSyncAction>
}
