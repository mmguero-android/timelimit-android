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
import io.timelimit.android.data.extensions.getChildCategories
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.derived.UserRelatedData
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

    val userRelatedData: LiveData<UserRelatedData?> by lazy { database.derivedDataDao().getUserRelatedDataLive(childId) }

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

        binding.title = getString(R.string.category_settings_parent_category_title)

        val list = binding.list

        userRelatedData.observe(viewLifecycleOwner, Observer { userRelatedData ->
            val ownCategory = userRelatedData?.categoryById?.get(categoryId) ?: kotlin.run {
                dismissAllowingStateLoss()
                return@Observer
            }
            val ownParentCategory = userRelatedData.categoryById[ownCategory.category.parentCategoryId]
            val currentChildCategories = userRelatedData.getChildCategories(ownCategory.category.id)

            list.removeAllViews()

            fun buildRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
                    android.R.layout.simple_list_item_single_choice,
                    list,
                    false
            ) as CheckedTextView

            userRelatedData.sortedCategories().forEach { (_, category) ->
                if (category.category.id != categoryId) {
                    val row = buildRow()

                    row.text = category.category.title
                    row.isChecked = category.category.id == ownCategory.category.parentCategoryId
                    row.isEnabled = !currentChildCategories.contains(category.category.id)
                    row.setOnClickListener {
                        if (!row.isChecked) {
                            auth.tryDispatchParentAction(
                                    SetParentCategory(
                                            categoryId = categoryId,
                                            parentCategory = category.category.id
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