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

package io.timelimit.android.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.timelimit.android.ui.manage.child.advanced.ManageChildAdvancedFragment
import io.timelimit.android.ui.manage.child.apps.ChildAppsFragment

abstract class ChildFragmentWrapper: SingleFragmentWrapper() {
    abstract val childId: String

    override val showAuthButton: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity.getActivityViewModel().database.user().getChildUserByIdLive(childId).observe(viewLifecycleOwner) {
            if (it == null) navigation.popBackStack()
        }
    }
}

class ChildAppsFragmentWrapper: ChildFragmentWrapper() {
    private val params by lazy { ChildAppsFragmentWrapperArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ChildAppsFragment.newInstance(childId = childId)
}

class ChildAdvancedFragmentWrapper: ChildFragmentWrapper() {
    private val params by lazy { ChildAdvancedFragmentWrapperArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ManageChildAdvancedFragment.newInstance(childId = childId)
}