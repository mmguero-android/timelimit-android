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
package io.timelimit.android.ui.manage.category.apps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.appsandrules.AppAndRuleItem
import io.timelimit.android.ui.manage.category.appsandrules.CategoryAppsAndRulesFragment

class CategoryAppsFragment : CategoryAppsAndRulesFragment() {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): CategoryAppsFragment = CategoryAppsFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(requireArguments()) }
    private val model: CategoryAppsModel by viewModels()
    override val categoryId: String get() = params.categoryId
    override val childId: String get() = params.childId

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.init(params)
        model.appEntries.observe(viewLifecycleOwner) { setListContent(it + listOf(AppAndRuleItem.AddAppItem)) }
    }
}
