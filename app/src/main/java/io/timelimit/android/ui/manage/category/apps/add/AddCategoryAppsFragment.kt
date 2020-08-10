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
package io.timelimit.android.ui.manage.category.apps.add


import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentAddCategoryAppsBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.apps.addactivity.AddAppActivitiesDialogFragment
import io.timelimit.android.ui.view.AppFilterView

class AddCategoryAppsFragment : DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "x"
        private const val STATUS_PACKAGE_NAMES = "d"
        private const val STATUS_EDUCATED = "e"
        private const val PARAM_CHILD_ID = "childId"
        private const val PARAM_CATEGORY_ID = "categoryId"
        private const val PARAM_CHILD_ADD_LIMIT_MODE = "addLimitMode"

        fun newInstance(childId: String, categoryId: String, childAddLimitMode: Boolean) = AddCategoryAppsFragment().apply {
            arguments = Bundle().apply {
                putString(PARAM_CHILD_ID, childId)
                putString(PARAM_CATEGORY_ID, categoryId)
                putBoolean(PARAM_CHILD_ADD_LIMIT_MODE, childAddLimitMode)
            }
        }
    }

    private val database: Database by lazy { DefaultAppLogic.with(context!!).database }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val adapter = AddAppAdapter()
    private var didEducateAboutAddingAssignedApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            adapter.selectedApps = savedInstanceState.getStringArrayList(STATUS_PACKAGE_NAMES)!!.toSet()
            didEducateAboutAddingAssignedApps = savedInstanceState.getBoolean(STATUS_EDUCATED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(STATUS_PACKAGE_NAMES, ArrayList(adapter.selectedApps))
        outState.putBoolean(STATUS_EDUCATED, didEducateAboutAddingAssignedApps)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentAddCategoryAppsBinding.inflate(LayoutInflater.from(context))
        val childId = arguments!!.getString(PARAM_CHILD_ID)!!
        val categoryId = arguments!!.getString(PARAM_CATEGORY_ID)!!
        val childAddLimitMode = arguments!!.getBoolean(PARAM_CHILD_ADD_LIMIT_MODE)

        auth.authenticatedUserOrChild.observe(this, Observer {
            val parentAuthValid = it?.second?.type == UserType.Parent
            val childAuthValid = it?.second?.id == childId && childAddLimitMode
            val authValid = parentAuthValid || childAuthValid

            if (!authValid) {
                dismissAllowingStateLoss()
            }
        })

        val filter = AppFilterView.getFilterLive(binding.filter)
        val isLocalMode = database.config().getDeviceAuthTokenAsync().map { it.isEmpty() }
        val showAppsFromOtherDevicesChecked = MutableLiveData<Boolean>().apply {
            value = binding.showAppsFromUnassignedDevices.isChecked
        }
        val realShowAppsFromAllDevices = isLocalMode.switchMap { localMode ->
            if (localMode) {
                liveDataFromValue(true)
            } else {
                showAppsFromOtherDevicesChecked
            }
        }

        binding.showAppsFromUnassignedDevices.setOnCheckedChangeListener { _, isChecked ->
            showAppsFromOtherDevicesChecked.value = isChecked
        }

        isLocalMode.observe(this, Observer {
            binding.showAppsFromUnassignedDevices.visibility = if (it) View.GONE else View.VISIBLE
        })

        val showAppsFromOtherCategories = MutableLiveData<Boolean>().apply { value = binding.showOtherCategoriesApps.isChecked }
        binding.showOtherCategoriesApps.setOnCheckedChangeListener { _, isChecked -> showAppsFromOtherCategories.value = isChecked }

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        val childDeviceIds = database.device().getDevicesByUserId(childId)
                .map { devices -> devices.map { it.id } }
                .ignoreUnchanged()

        val globalChildDeviceCounter = database.device().countDevicesWithChildUser()

        val hasChildDeviceIds = childDeviceIds.map { it.isNotEmpty() }

        val appsAtAssignedDevices = childDeviceIds
                .switchMap { database.app().getAppsByDeviceIds(it) }

        val appsAtAllDevices = database.app().getAllApps()

        val installedApps = realShowAppsFromAllDevices.switchMap { appsFromAllDevices ->
            if (appsFromAllDevices) appsAtAllDevices else appsAtAssignedDevices
        }.map { apps -> apps.distinctBy { app -> app.packageName } }

        val userRelatedDataLive = database.derivedDataDao().getUserRelatedDataLive(childId)

        val categoryTitleByPackageName = userRelatedDataLive.map { userRelatedData ->
            val result = mutableMapOf<String, String>()

            userRelatedData?.categoryApps?.forEach { app ->
                result[app.packageName] = userRelatedData.categoryById[app.categoryId]!!.category.title
            }

            result
        }

        val packageNamesAssignedToOtherCategories = userRelatedDataLive
                .map { it?.categoryApps?.map { app -> app.packageName }?.toSet() ?: emptySet() }

        val shownApps = if (childAddLimitMode) {
            userRelatedDataLive.switchMap { userRelatedData ->
                installedApps.map { installedApps ->
                    if (userRelatedData == null || !userRelatedData.categoryById.containsKey(categoryId))
                        emptyList()
                    else {
                        val parentCategories = userRelatedData.getCategoryWithParentCategories(categoryId)
                        val defaultCategory = userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]
                        val allowAppsWithoutCategory = defaultCategory != null && parentCategories.contains(defaultCategory.category.id)
                        val packageNameToCategoryId = userRelatedData.categoryApps.associateBy { it.packageName }

                        installedApps.filter { app ->
                            val appCategoryId = packageNameToCategoryId[app.packageName]?.categoryId
                            val categoryNotFound = !userRelatedData.categoryById.containsKey(appCategoryId)

                            parentCategories.contains(appCategoryId) || (categoryNotFound && allowAppsWithoutCategory)
                        }
                    }
                }
            }
        } else installedApps

        val listItems = filter.switchMap { filter ->
            shownApps.map { filter to it }
        }.map { (search, apps) ->
            apps.filter { search.matches(it) }
        }.switchMap { apps ->
            showAppsFromOtherCategories.switchMap { showOtherCategeories ->
                if (showOtherCategeories) {
                    liveDataFromValue(apps)
                } else {
                    packageNamesAssignedToOtherCategories.map { packagesFromOtherCategories ->
                        apps.filterNot { packagesFromOtherCategories.contains(it.packageName) }
                    }
                }
            }
        }.map { apps ->
            apps.sortedBy { app -> app.title.toLowerCase() }
        }

        val emptyViewText: LiveData<String?> = listItems.switchMap { items ->
            if (items.isNotEmpty()) {
                // list is not empty ...
                liveDataFromValue(null as String?)
            } else /* items.isEmpty() */ {
                shownApps.switchMap { shownApps ->
                    if (shownApps.isNotEmpty()) {
                        liveDataFromValue(getString(R.string.category_apps_add_empty_due_to_filter) as String?)
                    } else /* if (shownApps.isEmpty()) */ {
                        installedApps.switchMap { installedApps ->
                            if (installedApps.isNotEmpty()) {
                                liveDataFromValue(getString(R.string.category_apps_add_empty_due_to_child_mode) as String?)
                            } else /* if (installedApps.isEmpty()) */ {
                                isLocalMode.switchMap { isLocalMode ->
                                    if (isLocalMode) {
                                        liveDataFromValue(getString(R.string.category_apps_add_empty_local_mode) as String?)
                                    } else {
                                        showAppsFromOtherDevicesChecked.switchMap { showAppsFromOtherDevicesChecked ->
                                            if (showAppsFromOtherDevicesChecked) {
                                                globalChildDeviceCounter.map { childDeviceCounter ->
                                                    if (childDeviceCounter == 0L) {
                                                        getString(R.string.category_apps_add_empty_all_devices_no_apps_no_childs) as String?
                                                    } else {
                                                        getString(R.string.category_apps_add_empty_all_devices_no_apps_but_child_devices) as String?
                                                    }
                                                }
                                            } else {
                                                hasChildDeviceIds.map { hasChildDeviceIds ->
                                                    if (hasChildDeviceIds) {
                                                        getString(R.string.category_apps_add_empty_child_devices_no_apps) as String?
                                                    } else {
                                                        getString(R.string.category_apps_add_empty_no_child_devices) as String?
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } as LiveData<String?>
            }
        }

        listItems.observe(this, Observer {
            val selectedPackageNames = adapter.selectedApps
            val visiblePackageNames = it.map { it.packageName }.toSet()
            val hiddenSelectedPackageNames = selectedPackageNames.toMutableSet().apply { removeAll(visiblePackageNames) }.size

            adapter.data = it
            binding.hiddenEntries = if (hiddenSelectedPackageNames == 0)
                null
            else
                resources.getQuantityString(R.plurals.category_apps_add_dialog_hidden_entries, hiddenSelectedPackageNames, hiddenSelectedPackageNames)
        })

        emptyViewText.observe(this, Observer { binding.emptyText = it })

        categoryTitleByPackageName.observe(this, Observer {
            adapter.categoryTitleByPackageName = it
        })

        binding.someOptionsDisabledDueToChildAuthentication = childAddLimitMode

        binding.addAppsButton.setOnClickListener {
            val packageNames = adapter.selectedApps.toList()

            if (packageNames.isNotEmpty()) {
                auth.tryDispatchParentAction(
                        action = AddCategoryAppsAction(
                                categoryId = categoryId,
                                packageNames = packageNames
                        ),
                        allowAsChild = childAddLimitMode
                )
            }

            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.selectAllButton.setOnClickListener {
            adapter.selectedApps = adapter.selectedApps + (adapter.data?.map { it.packageName }?.toSet() ?: emptySet())
        }

        adapter.listener = object: AddAppAdapterListener {
            override fun onAppClicked(app: App) {
                if (adapter.selectedApps.contains(app.packageName)) {
                    adapter.selectedApps = adapter.selectedApps - setOf(app.packageName)
                } else {
                    if (!didEducateAboutAddingAssignedApps) {
                        if (adapter.categoryTitleByPackageName[app.packageName] != null) {
                            didEducateAboutAddingAssignedApps = true

                            AddAlreadyAssignedAppsInfoDialog().show(fragmentManager!!)
                        }
                    }

                    adapter.selectedApps = adapter.selectedApps + setOf(app.packageName)
                }
            }

            override fun onAppLongClicked(app: App): Boolean {
                return if (adapter.selectedApps.isEmpty()) {
                    AddAppActivitiesDialogFragment.newInstance(
                            childId = childId,
                            categoryId = categoryId,
                            packageName = app.packageName,
                            childAddLimitMode = childAddLimitMode
                    ).show(parentFragmentManager)

                    dismissAllowingStateLoss()

                    true
                } else {
                    Toast.makeText(context, R.string.category_apps_add_dialog_cannot_add_activities_already_sth_selected, Toast.LENGTH_LONG).show()

                    false
                }
            }
        }

        // uses the idea from https://stackoverflow.com/a/57854900
        binding.emptyView.layoutParams = CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            behavior = object: CoordinatorLayout.Behavior<TextView>() {
                override fun layoutDependsOn(parent: CoordinatorLayout, child: TextView, dependency: View) = dependency is AppBarLayout

                override fun onDependentViewChanged(parent: CoordinatorLayout, child: TextView, dependency: View): Boolean {
                    dependency as AppBarLayout

                    (child.layoutParams as CoordinatorLayout.LayoutParams).topMargin = (dependency.height + dependency.y).toInt()
                    child.requestLayout()

                    return true
                }
            }
        }

        return AlertDialog.Builder(context!!, R.style.AppTheme)
                .setView(binding.root)
                .create()
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}
