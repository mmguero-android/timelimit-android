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
package io.timelimit.android.ui.manage.category.usagehistory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.paging.LivePagedListBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.databinding.FragmentUsageHistoryBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs

class UsageHistoryFragment : Fragment() {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs) = UsageHistoryFragment().apply {
            arguments = params.toBundle()
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentUsageHistoryBinding.inflate(inflater, container, false)
        val params = ManageCategoryFragmentArgs.fromBundle(arguments!!)
        val database = DefaultAppLogic.with(context!!).database
        val adapter = UsageHistoryAdapter()

        LivePagedListBuilder(
                database.usedTimes().getUsedTimeListItemsByCategoryId(params.categoryId),
                10
        )
                .build()
                .observe(viewLifecycleOwner, Observer {
                    binding.isEmpty = it.isEmpty()
                    adapter.submitList(it)
                })

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(context)

        return binding.root
    }
}
