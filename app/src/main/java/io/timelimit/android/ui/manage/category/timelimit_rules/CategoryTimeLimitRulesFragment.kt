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
package io.timelimit.android.ui.manage.category.timelimit_rules

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.appsandrules.AppAndRuleItem
import io.timelimit.android.ui.manage.category.appsandrules.CategoryAppsAndRulesFragment

class CategoryTimeLimitRulesFragment: CategoryAppsAndRulesFragment() {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): CategoryTimeLimitRulesFragment {
            val result = CategoryTimeLimitRulesFragment()
            result.arguments = params.toBundle()
            return result
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(requireArguments()) }
    private val rules: LiveData<List<TimeLimitRule>> by lazy { database.timeLimitRules().getTimeLimitRulesByCategory(params.categoryId) }
    override val childId: String get() = params.childId
    override val categoryId: String get() = params.categoryId

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hasHiddenIntro = database.config().wereHintsShown(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)

        rules.map { rules ->
            rules.sortedBy { it.dayMask }.map { AppAndRuleItem.RuleEntry(it) }
        }.switchMap {
            val baseList = it + listOf(AppAndRuleItem.AddRuleItem)

            hasHiddenIntro.map { hasHiddenIntro ->
                if (hasHiddenIntro) {
                    baseList
                } else {
                    listOf(AppAndRuleItem.RulesIntro) + baseList
                }
            }
        }.observe(viewLifecycleOwner) { setListContent(it) }
    }
}
