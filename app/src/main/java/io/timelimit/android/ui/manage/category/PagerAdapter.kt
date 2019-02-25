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
package io.timelimit.android.ui.manage.category

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.timelimit.android.ui.manage.category.apps.CategoryAppsFragment
import io.timelimit.android.ui.manage.category.blocked_times.BlockedTimeAreasFragment
import io.timelimit.android.ui.manage.category.settings.CategorySettingsFragment
import io.timelimit.android.ui.manage.category.timelimit_rules.CategoryTimeLimitRulesFragment
import io.timelimit.android.ui.manage.category.usagehistory.UsageHistoryFragment

class PagerAdapter(fragmentManager: FragmentManager, private val params: ManageCategoryFragmentArgs): FragmentStatePagerAdapter(fragmentManager) {
    override fun getCount() = 5

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> CategoryAppsFragment.newInstance(params)
        1 -> CategoryTimeLimitRulesFragment.newInstance(params)
        2 -> BlockedTimeAreasFragment.newInstance(params)
        3 -> UsageHistoryFragment.newInstance(params)
        4 -> CategorySettingsFragment.newInstance(params)
        else -> throw IllegalStateException()
    }
}
