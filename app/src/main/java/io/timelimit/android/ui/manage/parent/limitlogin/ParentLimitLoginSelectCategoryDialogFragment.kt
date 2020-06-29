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

package io.timelimit.android.ui.manage.parent.limitlogin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.BottomSheetSelectionListBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.sync.actions.UpdateUserLimitLoginCategory
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment

class ParentLimitLoginSelectCategoryDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "ParentLimitLoginSelectCategoryDialogFragment"
        private const val USER_ID = "userId"

        fun newInstance(userId: String) = ParentLimitLoginSelectCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val userId = arguments!!.getString(USER_ID)!!
        val auth = (activity as ActivityViewModelHolder).getActivityViewModel()
        val logic = auth.logic
        val options = logic.database.userLimitLoginCategoryDao().getLimitLoginCategoryOptions(userId)
        val hasPremium = logic.fullVersion.shouldProvideFullVersionFunctions

        val binding = BottomSheetSelectionListBinding.inflate(inflater, container, false)

        binding.title = getString(R.string.parent_limit_login_title)

        val list = binding.list

        hasPremium.switchMap { a ->
            options.switchMap { b ->
                auth.authenticatedUser.map { c ->
                    Triple(a, b, c)
                }
            }
        }.observe(viewLifecycleOwner, Observer { (hasPremium, categoryList, user) ->
            if (user?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss(); return@Observer
            }

            val isUserItself = user.second.id == userId

            val hasSelection = categoryList.find { it.selected } != null

            list.removeAllViews()

            fun buildRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
                    android.R.layout.simple_list_item_single_choice,
                    list,
                    false
            ) as CheckedTextView

            categoryList.forEach { category ->
                val row = buildRow()

                row.text = getString(R.string.parent_limit_login_dialog_item, category.childTitle, category.categoryTitle)
                row.isChecked = category.selected
                row.setOnClickListener {
                    if (!hasPremium) {
                        RequiresPurchaseDialogFragment().show(parentFragmentManager)
                    } else if (!row.isChecked) {
                        if (isUserItself) {
                            auth.tryDispatchParentAction(
                                    UpdateUserLimitLoginCategory(
                                            userId = userId,
                                            categoryId = category.categoryId
                                    )
                            )

                            dismiss()
                        } else {
                            LimitLoginRestrictedToUserItselfDialogFragment().show(parentFragmentManager)
                        }
                    } else {
                        dismiss()
                    }
                }

                list.addView(row)
            }

            buildRow().let { row ->
                row.setText(R.string.parent_limit_login_dialog_no_selection)
                row.isChecked = !hasSelection
                row.setOnClickListener {
                    if (!row.isChecked) {
                        auth.tryDispatchParentAction(
                                UpdateUserLimitLoginCategory(
                                        userId = userId,
                                        categoryId = null
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