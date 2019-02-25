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
package io.timelimit.android.ui.manage.category.usagehistory

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.databinding.FragmentUsageHistoryItemBinding
import io.timelimit.android.util.TimeTextUtil
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import java.util.*

class UsageHistoryAdapter: PagedListAdapter<UsedTimeItem, UsageHistoryViewHolder>(diffCallback) {
    companion object {
        private val diffCallback = object: DiffUtil.ItemCallback<UsedTimeItem>() {
            override fun areContentsTheSame(oldItem: UsedTimeItem, newItem: UsedTimeItem) = oldItem == newItem
            override fun areItemsTheSame(oldItem: UsedTimeItem, newItem: UsedTimeItem) =
                    (oldItem.dayOfEpoch == newItem.dayOfEpoch) && (oldItem.categoryId == newItem.categoryId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = UsageHistoryViewHolder(
            FragmentUsageHistoryItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            )
    )

    override fun onBindViewHolder(holder: UsageHistoryViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        if (item == null) {
            binding.date = ""
            binding.usedTime = ""
        } else {
            val date = LocalDate.ofEpochDay(item.dayOfEpoch.toLong())

            binding.date = DateFormat.getDateFormat(context).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L))

            binding.usedTime = TimeTextUtil.used(item.usedMillis.toInt(), context)
        }
    }
}

class UsageHistoryViewHolder(val binding: FragmentUsageHistoryItemBinding): RecyclerView.ViewHolder(binding.root)
