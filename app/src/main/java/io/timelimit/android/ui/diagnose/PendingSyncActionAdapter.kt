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
package io.timelimit.android.ui.diagnose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.PendingSyncAction
import io.timelimit.android.databinding.DiagnoseSyncCardBinding
import org.json.JSONObject

class PendingSyncActionAdapter: PagedListAdapter<PendingSyncAction, PendingSyncActionViewHolder>(diffCallback) {
    companion object {
        private val diffCallback = object: DiffUtil.ItemCallback<PendingSyncAction>() {
            override fun areContentsTheSame(oldItem: PendingSyncAction, newItem: PendingSyncAction) = oldItem == newItem
            override fun areItemsTheSame(oldItem: PendingSyncAction, newItem: PendingSyncAction) = oldItem.sequenceNumber == newItem.sequenceNumber
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PendingSyncActionViewHolder(
            DiagnoseSyncCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            ).apply {
                copyButton.setOnClickListener {
                    val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                    clipboard!!.primaryClip = ClipData.newPlainText("TimeLimit", text)

                    Toast.makeText(it.context, R.string.diagnose_sync_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            }
    )

    override fun onBindViewHolder(holder: PendingSyncActionViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.text = if (item == null) {
            "null"
        } else {
            val builder = StringBuilder("sequence number: ${item.sequenceNumber}\n")

            if (item.userId.isNotEmpty()) {
                builder.append("user: ${item.userId}\n")
            }

            if (item.integrity.isNotEmpty()) {
                builder.append("integrity: ${item.integrity}\n")
            }

            builder.append("action: ").append(
                    try {
                        JSONObject(item.encodedAction).toString(2)
                    } catch (ex: Exception) {
                        "error"
                    }
            )

            builder.toString()
        }
    }
}

class PendingSyncActionViewHolder(val binding: DiagnoseSyncCardBinding): RecyclerView.ViewHolder(binding.root)
