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
package io.timelimit.android.ui.manage.category.apps.addactivity

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentAddCategoryActivitiesBinding
import io.timelimit.android.extensions.addOnTextChangedListener
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.getActivityViewModel

class AddAppActivitiesDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddAppActivitiesDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
        private const val PACKAGE_NAME = "packageName"
        private const val SELECTED_ACTIVITIES = "selectedActivities"

        fun newInstance(childId: String, categoryId: String, packageName: String) = AddAppActivitiesDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
                putString(PACKAGE_NAME, packageName)
            }
        }
    }

    val adapter = AddAppActivityAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            adapter.selectedActiviities.clear()
            savedInstanceState.getStringArray(SELECTED_ACTIVITIES)!!.forEach { adapter.selectedActiviities.add(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArray(SELECTED_ACTIVITIES, adapter.selectedActiviities.toTypedArray())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val appPackageName = arguments!!.getString(PACKAGE_NAME)!!
        val categoryId = arguments!!.getString(CATEGORY_ID)!!
        val auth = getActivityViewModel(activity!!)
        val binding = FragmentAddCategoryActivitiesBinding.inflate(LayoutInflater.from(context!!))
        val searchTerm = MutableLiveData<String>().apply { value = binding.search.text.toString() }
        binding.search.addOnTextChangedListener { searchTerm.value = binding.search.text.toString() }

        auth.authenticatedUser.observe(this, Observer {
            if (it?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        val logic = DefaultAppLogic.with(context!!)
        val allActivities = logic.database.appActivity().getAppActivitiesByPackageName(appPackageName).map { activities ->
            activities.distinctBy { it.activityClassName }
        }
        val filteredActivities = allActivities.switchMap { activities ->
            searchTerm.map { term ->
                if (term.isEmpty()) {
                    activities
                } else {
                    activities.filter { it.activityClassName.contains(term, ignoreCase = true) or it.title.contains(term, ignoreCase = true) }
                }
            }
        }

        binding.recycler.layoutManager = LinearLayoutManager(context!!)
        binding.recycler.adapter = adapter

        filteredActivities.observe(this, Observer { list ->
            val selectedActivities = adapter.selectedActiviities
            val visibleActivities = list.map { it.activityClassName }
            val hiddenSelectedActivities = selectedActivities.toMutableSet().apply { removeAll(visibleActivities) }.size

            adapter.data = list
            binding.hiddenEntries = if (hiddenSelectedActivities == 0)
                null
            else
                resources.getQuantityString(R.plurals.category_apps_add_dialog_hidden_entries, hiddenSelectedActivities, hiddenSelectedActivities)
        })

        val emptyViewText = allActivities.switchMap { all ->
            filteredActivities.map { filtered ->
                if (filtered.isNotEmpty())
                    null
                else if (all.isNotEmpty())
                    getString(R.string.category_apps_add_activity_empty_filtered)
                else /* (all.isEmpty()) */
                    getString(R.string.category_apps_add_activity_empty_unfiltered)
            }
        }

        emptyViewText.observe(this, Observer {
            binding.emptyViewText = it
        })

        binding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.addActivitiesButton.setOnClickListener {
            if (adapter.selectedActiviities.isNotEmpty()) {
                auth.tryDispatchParentAction(AddCategoryAppsAction(
                        categoryId = categoryId,
                        packageNames = adapter.selectedActiviities.toList().map { "$appPackageName:$it" }
                ))
            }

            dismissAllowingStateLoss()
        }

        return AlertDialog.Builder(context!!, R.style.AppTheme)
                .setView(binding.root)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}