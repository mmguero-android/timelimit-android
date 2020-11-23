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
package io.timelimit.android.ui.util

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic

class SyncStatusModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val db = logic.database
    private val sync = logic.syncUtil

    private val isConnectedMode = db.config().getDeviceAuthTokenAsync().map { it.isNotEmpty() }
    private val isConnected = logic.isConnected
    private val isNormalSyncPending = sync.isNormalSyncRequested
    private val isUnimportantSyncPending = sync.isVeryUnimportantSyncRequested
    private val hasQueuedItems = db.pendingSyncAction().countAllActionsLive().map { it != 0L }

    val statusText = isConnectedMode.switchMap { connectedMode ->
        if (connectedMode) {
            isConnected.switchMap { connected ->
                if (connected) {
                    val base = application.getString(R.string.sync_status_online)

                    isNormalSyncPending.switchMap { normalSyncPending ->
                        if (normalSyncPending) {
                            liveDataFromValue(application.getString(R.string.sync_status_not_synced, base))
                        } else {
                            isUnimportantSyncPending.map { unimportantSyncPending ->
                                if (unimportantSyncPending) {
                                    application.getString(R.string.sync_status_unimportant_not_synced, base)
                                } else {
                                    base
                                }
                            }
                        }
                    }
                } else {
                    val base = application.getString(R.string.sync_status_offline)

                    hasQueuedItems.map { itemsPending ->
                        if (itemsPending) {
                            application.getString(R.string.sync_status_not_synced, base)
                        } else {
                            base
                        }
                    }
                }
            } as LiveData<String?>
        } else {
            liveDataFromValue(null as String?)
        }
    }

    fun handleStart() { logic.networkStatus.forceRefresh() }
}