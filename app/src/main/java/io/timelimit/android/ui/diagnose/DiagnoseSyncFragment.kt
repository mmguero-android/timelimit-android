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
package io.timelimit.android.ui.diagnose


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.LivePagedListBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.DiagnoseSyncFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.apply.UploadActionsUtil
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class DiagnoseSyncFragment : Fragment(), FragmentWithCustomTitle {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DiagnoseSyncFragmentBinding.inflate(inflater, container, false)
        val logic = DefaultAppLogic.with(context!!)
        val adapter = PendingSyncActionAdapter()
        val sync = logic.syncUtil

        binding.recycler.layoutManager = LinearLayoutManager(context!!)
        binding.recycler.adapter = adapter

        LivePagedListBuilder(logic.database.pendingSyncAction().getAllPendingSyncActionsPaged(), 10)
                .build()
                .observe(this, Observer {
                    adapter.submitList(it)
                    binding.isListEmpty = it.isEmpty()
                })

        binding.clearCacheBtn.setOnClickListener {
            Threads.database.execute {
                UploadActionsUtil.deleteAllVersionNumbersSync(logic.database)
            }

            Toast.makeText(context!!, R.string.diagnose_sync_btn_clear_cache_toast, Toast.LENGTH_SHORT).show()
        }

        binding.requestSyncBtn.setOnClickListener {
            sync.requestImportantSync(true)

            Toast.makeText(context!!, R.string.diagnose_sync_btn_request_sync_toast, Toast.LENGTH_SHORT).show()
        }

        sync.isSyncing.switchMap { a ->
            sync.lastSyncException.map { b -> a to b }
        }.observe(this, Observer { (isSyncing, lastSyncException) ->
            binding.hadSyncException = lastSyncException != null

            if (isSyncing) {
                binding.syncStatusText = getString(R.string.diagnose_sync_status_syncing)
            } else if (lastSyncException != null) {
                binding.syncStatusText = getString(R.string.diagnose_sync_status_had_error)
            } else {
                binding.syncStatusText = getString(R.string.diagnose_sync_status_idle)
            }
        })

        binding.showExceptionBtn.setOnClickListener {
            sync.lastSyncException.value?.let { ex ->
                DiagnoseExceptionDialogFragment.newInstance(ex).show(fragmentManager!!)
            }
        }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromValue("${getString(R.string.diagnose_sync_title)} < ${getString(R.string.about_diagnose_title)} < ${getString(R.string.main_tab_overview)}")
}
