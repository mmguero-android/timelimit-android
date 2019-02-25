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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.viewpager.widget.ViewPager
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import kotlinx.android.synthetic.main.fragment_manage_child.*

class ManageChildFragment : Fragment(), FragmentWithCustomTitle {
    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(arguments!!) }
    private val adapter: PagerAdapter by lazy { PagerAdapter(childFragmentManager, params) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val child: LiveData<User?> by lazy { logic.database.user().getUserByIdLive(params.childId) }
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private var wereViewsCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_child, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AuthenticationFab.manageAuthenticationFab(
                fab = fab,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true),
                authenticatedUser = activity.getActivityViewModel().authenticatedUser,
                shouldHighlight = activity.getActivityViewModel().shouldHighlightAuthenticationButton
        )

        fab.setOnClickListener { activity.showAuthenticationScreen() }

        val navigation = Navigation.findNavController(view)

        // leave if the child does not exist
        if (!wereViewsCreated) {
            wereViewsCreated = true

            child.observe(this, Observer {
                if (it == null || it.type != UserType.Child) {
                    navigation.popBackStack(R.id.overviewFragment, false)
                }
            })
        }

        pager.adapter = adapter

        bottom_navigation_view.setOnNavigationItemSelectedListener {
            menuItem ->

            pager.currentItem = when (menuItem.itemId) {
                R.id.manage_child_tab_categories -> 0
                R.id.manage_child_tab_apps -> 1
                R.id.manage_child_tab_manage -> 2
                else -> 0
            }

            true
        }

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                // ignore
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // ignore
            }

            override fun onPageSelected(position: Int) {
                bottom_navigation_view.selectedItemId = when(position) {
                    0 -> R.id.manage_child_tab_categories
                    1 -> R.id.manage_child_tab_apps
                    2 -> R.id.manage_child_tab_manage
                    else -> throw IllegalStateException()
                }
            }
        })
    }

    override fun getCustomTitle() = child.map { it?.name }
}
