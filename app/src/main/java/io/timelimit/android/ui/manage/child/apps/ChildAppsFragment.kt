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
package io.timelimit.android.ui.manage.child.apps


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.databinding.ChildAppsFragmentBinding
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.apps.assign.AssignAllAppsCategoryDialogFragment
import io.timelimit.android.ui.manage.child.apps.assign.AssignAppCategoryDialogFragment
import io.timelimit.android.ui.view.AppFilterView

class ChildAppsFragment : Fragment() {
    companion object {
        fun newInstance(args: ManageChildFragmentArgs) = ChildAppsFragment().apply {
            arguments = args.toBundle()
        }
    }

    val args: ManageChildFragmentArgs by lazy {
        ManageChildFragmentArgs.fromBundle(arguments!!)
    }

    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ChildAppsFragmentBinding.inflate(inflater, container, false)
        val model = ViewModelProviders.of(this).get(ChildAppsModel::class.java)
        val adapter = ChildAppsAdapter()

        fun getMode() = when (binding.sortSetting.checkedRadioButtonId) {
            R.id.sort_by_category -> ChildAppsMode.SortByCategory
            R.id.sort_by_title -> ChildAppsMode.SortByTitle
            else -> throw IllegalArgumentException()
        }

        model.childIdLive.value = args.childId
        AppFilterView.getFilterLive(binding.appFilter).observe(this, Observer { model.appFilterLive.value = it })
        model.modeLive.value = getMode()
        binding.sortSetting.setOnCheckedChangeListener { _, _ -> model.modeLive.value = getMode() }

        model.showAppsFromOtherDevicesChecked.value = binding.showAppsFromUnassignedDevices.isChecked
        binding.showAppsFromUnassignedDevices.setOnCheckedChangeListener { _, isChecked ->
            model.showAppsFromOtherDevicesChecked.value = isChecked
        }

        model.listContentLive.observe(this, Observer {
            adapter.data = it
        })

        model.isLocalMode.observe(this, Observer {
            binding.showAppsFromUnassignedDevices.visibility = if (it) View.GONE else View.VISIBLE
        })

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        adapter.handlers = object: Handlers {
            override fun onAppClicked(app: App) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    AssignAppCategoryDialogFragment.newInstance(
                            childId = args.childId,
                            appPackageName = app.packageName
                    ).show(fragmentManager!!)
                }
            }

            override fun onAssignAppsClicked(packageNames: List<String>) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    AssignAllAppsCategoryDialogFragment.newInstance(
                            childId = args.childId,
                            appPackageNames = packageNames
                    ).show(fragmentManager!!)
                }
            }
        }

        return binding.root
    }
}
