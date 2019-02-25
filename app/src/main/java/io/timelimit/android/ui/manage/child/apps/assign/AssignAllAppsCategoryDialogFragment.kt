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
package io.timelimit.android.ui.manage.child.apps.assign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.BottomSheetSelectionListBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class AssignAllAppsCategoryDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val EXTRA_CHILD_ID = "a"
        private const val EXTRA_PACKAGE_NAMES = "b"
        private const val TAG = "aaacdf"

        fun newInstance(childId: String, appPackageNames: List<String>) = AssignAllAppsCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_CHILD_ID, childId)
                putStringArray(EXTRA_PACKAGE_NAMES, appPackageNames.toTypedArray())
            }
        }
    }

    val childId: String by lazy { arguments!!.getString(EXTRA_CHILD_ID) }
    val appPackageNames: Array<String> by lazy { arguments!!.getStringArray(EXTRA_PACKAGE_NAMES) }

    val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    val database: Database by lazy { logic.database }
    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    val childCategoryEntries: LiveData<List<Category>> by lazy {
        database.category().getCategoriesByChildId(childId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = BottomSheetSelectionListBinding.inflate(inflater, container, false)
        val list = binding.list

        binding.title = resources.getQuantityString(R.plurals.generic_plural_app, appPackageNames.size, appPackageNames.size)

        childCategoryEntries.observe(this, Observer { categories ->
            fun buildRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
                    android.R.layout.simple_list_item_single_choice,
                    list,
                    false
            ) as CheckedTextView

            categories.forEach { category ->
                buildRow().let { row ->
                    row.text = category.title
                    row.setOnClickListener {
                            auth.tryDispatchParentAction(
                                    AddCategoryAppsAction(
                                            categoryId = category.id,
                                            packageNames = appPackageNames.toList()
                                    )
                            )

                        dismiss()
                    }

                    list.addView(row)
                }
            }

            buildRow().let { row ->
                row.setText(R.string.child_apps_unassigned)
                row.isChecked = true
                row.setOnClickListener {
                    dismiss()
                }

                list.addView(row)
            }
        })

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
