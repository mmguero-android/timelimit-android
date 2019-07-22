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
package io.timelimit.android.ui.manage.category.settings

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
import io.timelimit.android.sync.actions.SetParentCategory
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class SelectParentCategoryDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "SelectParentCategoryDialogFragment"
        private const val CATEGORY_ID = "categoryId"
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String, categoryId: String) = SelectParentCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    val childId: String by lazy { arguments!!.getString(CHILD_ID)!! }
    val categoryId: String by lazy { arguments!!.getString(CATEGORY_ID)!! }

    val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    val database: Database by lazy { logic.database }
    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    val childCategoryEntries: LiveData<List<Category>> by lazy {
        database.category().getCategoriesByChildId(childId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        childCategoryEntries.observe(this, Observer { categories ->
            val ownCategory = categories.find { it.id == categoryId }
            val hasSubCategories = categories.find { it.parentCategoryId == categoryId } != null

            if (ownCategory == null || hasSubCategories) {
                dismissAllowingStateLoss()
            }
        })

        auth.authenticatedUser.observe(this, Observer {
            if (it?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = BottomSheetSelectionListBinding.inflate(inflater, container, false)

        binding.title = getString(R.string.category_settings_parent_category_title)

        val list = binding.list

        childCategoryEntries.observe(this, Observer { categories ->
            list.removeAllViews()

            val ownCategory = categories.find { it.id == categoryId }
            val ownParentCategory = categories.find { it.id == ownCategory?.parentCategoryId }

            fun buildRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
                    android.R.layout.simple_list_item_single_choice,
                    list,
                    false
            ) as CheckedTextView

            categories.forEach { category ->
                if (category.id != categoryId) {
                    val row = buildRow()

                    row.text = category.title
                    row.isChecked = category.id == ownCategory?.parentCategoryId
                    row.isEnabled = categories.find { it.id == category.parentCategoryId } == null
                    row.setOnClickListener {
                        if (!row.isChecked) {
                            auth.tryDispatchParentAction(
                                    SetParentCategory(
                                            categoryId = categoryId,
                                            parentCategory = category.id
                                    )
                            )
                        }

                        dismiss()
                    }

                    list.addView(row)
                }
            }

            buildRow().let { row ->
                row.setText(R.string.category_settings_parent_category_none)
                row.isChecked = ownParentCategory == null

                row.setOnClickListener {
                    if (!row.isChecked) {
                        auth.tryDispatchParentAction(
                                SetParentCategory(
                                        categoryId = categoryId,
                                        parentCategory = ""
                                )
                        )
                    }

                    dismiss()
                }

                list.addView(row)
            }
        })

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}