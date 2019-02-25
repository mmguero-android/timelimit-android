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
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import kotlinx.android.synthetic.main.fragment_manage_category.*

class ManageCategoryFragment : Fragment(), FragmentWithCustomTitle {
    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val category: LiveData<Category?> by lazy {
        logic.database.category()
                .getCategoryByChildIdAndId(params.childId, params.categoryId)
    }
    private val user: LiveData<User?> by lazy {
        logic.database.user().getUserByIdLive(params.childId)
    }
    private val adapter: PagerAdapter by lazy { PagerAdapter(childFragmentManager, params) }
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private var wereViewsCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_category, container, false)
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

        pager.adapter = adapter

        bottom_navigation_view.setOnNavigationItemSelectedListener {
            menuItem ->

            pager?.currentItem = when(menuItem.itemId) {
                R.id.manage_category_tab_apps -> 0
                R.id.manage_category_tab_time_limit_rules -> 1
                R.id.manage_category_tab_blocked_time_areas -> 2
                R.id.manage_category_tab_usage_log -> 3
                R.id.manage_category_tab_settings -> 4
                else -> throw IllegalStateException()
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
                    0 -> R.id.manage_category_tab_apps
                    1 -> R.id.manage_category_tab_time_limit_rules
                    2 -> R.id.manage_category_tab_blocked_time_areas
                    3 -> R.id.manage_category_tab_usage_log
                    4 -> R.id.manage_category_tab_settings
                    else -> throw IllegalStateException()
                }
            }
        })

        if (!wereViewsCreated) {
            wereViewsCreated = true

            category.observe(this, Observer {
                if (it == null) {
                    navigation.popBackStack()
                }
            })
        }
    }

    override fun getCustomTitle(): LiveData<String?> = mergeLiveData(user, category).map {
        (user, category) ->

        "${category?.title} - ${user?.name}"
    }
}
