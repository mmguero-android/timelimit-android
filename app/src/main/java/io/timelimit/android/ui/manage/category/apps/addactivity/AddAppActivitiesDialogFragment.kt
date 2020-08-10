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
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
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
        private const val CHILD_ADD_LIMIT_MODE = "childAddLimitMode"

        fun newInstance(childId: String, categoryId: String, packageName: String, childAddLimitMode: Boolean) = AddAppActivitiesDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
                putString(PACKAGE_NAME, packageName)
                putBoolean(CHILD_ADD_LIMIT_MODE, childAddLimitMode)
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
        val childId = arguments!!.getString(CHILD_ID)!!
        val childAddLimitMode = arguments!!.getBoolean(CHILD_ADD_LIMIT_MODE)
        val auth = getActivityViewModel(activity!!)
        val binding = FragmentAddCategoryActivitiesBinding.inflate(LayoutInflater.from(context!!))
        val searchTerm = MutableLiveData<String>().apply { value = binding.search.text.toString() }
        binding.search.addOnTextChangedListener { searchTerm.value = binding.search.text.toString() }

        auth.authenticatedUserOrChild.observe(this, Observer {
            val parentAuthenticated = it?.second?.type == UserType.Parent
            val childAuthenticated = it?.second?.id == childId && childAddLimitMode
            val anyoneAuthenticated = parentAuthenticated || childAuthenticated

            if (!anyoneAuthenticated) {
                dismissAllowingStateLoss()
            }
        })

        val logic = DefaultAppLogic.with(context!!)
        val allActivitiesLive = logic.database.appActivity().getAppActivitiesByPackageName(appPackageName).map { activities ->
            activities.distinctBy { it.activityClassName }
        }

        val userRelatedDataLive = logic.database.derivedDataDao().getUserRelatedDataLive(childId)

        val shownActivities = if (childAddLimitMode) {
            userRelatedDataLive.switchMap { userRelatedData ->
                allActivitiesLive.map { allActivities ->
                    if (userRelatedData == null || !userRelatedData.categoryById.containsKey(categoryId))
                        emptyList()
                    else {
                        val parentCategories = userRelatedData.getCategoryWithParentCategories(categoryId)
                        val defaultCategory = userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]
                        val relatedPackageNameToCategoryId = userRelatedData.categoryApps
                                .filter { it.packageNameWithoutActivityName == appPackageName }
                                .associateBy { it.packageName }
                        val baseAppCategoryOrDefaultCategory = userRelatedData.categoryById[relatedPackageNameToCategoryId[appPackageName]?.categoryId] ?: defaultCategory
                        val baseAppCategoryInParentCategoryOrMatchingUnassigned = parentCategories.contains(baseAppCategoryOrDefaultCategory?.category?.id)

                        allActivities.filter { activity ->
                            val activityCategoryItem = userRelatedData.categoryById[relatedPackageNameToCategoryId[activity.appPackageName + ":" + activity.activityClassName]?.categoryId]
                            val activityItselfInParentCategory = parentCategories.contains(activityCategoryItem?.category?.id)
                            val activityItselfUnassigned = activityCategoryItem == null

                            (baseAppCategoryInParentCategoryOrMatchingUnassigned && activityItselfUnassigned) || activityItselfInParentCategory
                        }
                    }
                }
            }
        } else allActivitiesLive

        val filteredActivities = shownActivities.switchMap { activities ->
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

        val emptyViewText = allActivitiesLive.switchMap { all ->
            shownActivities.switchMap { shown ->
                filteredActivities.map { filtered ->
                    if (filtered.isNotEmpty())
                        null
                    else if (all.isNotEmpty())
                        if (shown.isEmpty())
                            getString(R.string.category_apps_add_activity_empty_shown)
                        else
                            getString(R.string.category_apps_add_activity_empty_filtered)
                    else /* (all.isEmpty()) */
                        getString(R.string.category_apps_add_activity_empty_unfiltered)
                }
            }
        }

        emptyViewText.observe(this, Observer {
            binding.emptyViewText = it
        })

        binding.someOptionsDisabledDueToChildAuthentication = childAddLimitMode

        binding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.addActivitiesButton.setOnClickListener {
            if (adapter.selectedActiviities.isNotEmpty()) {
                auth.tryDispatchParentAction(
                        action = AddCategoryAppsAction(
                                categoryId = categoryId,
                                packageNames = adapter.selectedActiviities.toList().map { "$appPackageName:$it" }
                        ),
                        allowAsChild = childAddLimitMode
                )
            }

            dismissAllowingStateLoss()
        }

        return AlertDialog.Builder(context!!, R.style.AppTheme)
                .setView(binding.root)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}