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
package io.timelimit.android.ui.overview.main

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import io.timelimit.android.ui.overview.about.AboutFragment
import io.timelimit.android.ui.overview.overview.OverviewFragment
import io.timelimit.android.ui.overview.uninstall.UninstallFragment

class PagerAdapter(fragmentManager: FragmentManager): FragmentStatePagerAdapter(fragmentManager) {
    override fun getCount() = 3

    override fun getItem(position: Int) = when(position) {
        0 -> OverviewFragment()
        1 -> UninstallFragment()
        2 -> AboutFragment()
        else -> throw IllegalStateException()
    }
}
