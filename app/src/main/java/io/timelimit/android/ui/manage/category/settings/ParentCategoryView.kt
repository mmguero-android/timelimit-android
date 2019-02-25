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

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.data.Database
import io.timelimit.android.databinding.ManageParentCategoryBinding
import io.timelimit.android.ui.main.ActivityViewModel

object ParentCategoryView {
    fun bind(
            binding: ManageParentCategoryBinding,
            auth: ActivityViewModel,
            lifecycleOwner: LifecycleOwner,
            categoryId: String,
            childId: String,
            database: Database,
            fragmentManager: FragmentManager
    ) {
        database.category().getCategoriesByChildId(childId).observe(lifecycleOwner, Observer { categories ->
            val ownCategory = categories.find { it.id == categoryId }
            val parentCategory = categories.find { it.id == ownCategory?.parentCategoryId }
            val hasSubCategories = categories.find { it.parentCategoryId == categoryId } != null

            binding.parentCategoryTitle = parentCategory?.title
            binding.isParentCategory = hasSubCategories
        })

        binding.selectParentButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                SelectParentCategoryDialogFragment.newInstance(
                        childId = childId,
                        categoryId = categoryId
                ).show(fragmentManager)
            }
        }
    }
}