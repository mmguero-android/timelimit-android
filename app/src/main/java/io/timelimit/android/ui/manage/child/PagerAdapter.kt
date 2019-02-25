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
package io.timelimit.android.ui.manage.child

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.timelimit.android.ui.manage.child.advanced.ManageChildAdvancedFragment
import io.timelimit.android.ui.manage.child.apps.ChildAppsFragment
import io.timelimit.android.ui.manage.child.category.ManageChildCategoriesFragment

class PagerAdapter(fragmentManager: FragmentManager, private val params: ManageChildFragmentArgs): FragmentStatePagerAdapter(fragmentManager) {
    override fun getCount() = 3

    override fun getItem(position: Int) = when(position) {
        0 -> ManageChildCategoriesFragment.newInstance(params)
        1 -> ChildAppsFragment.newInstance(params)
        2 -> ManageChildAdvancedFragment.newInstance(params)
        else -> throw IllegalStateException()
    }
}
