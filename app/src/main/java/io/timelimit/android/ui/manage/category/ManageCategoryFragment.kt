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
package io.timelimit.android.ui.manage.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.category.apps.CategoryAppsFragment
import io.timelimit.android.ui.manage.category.blocked_times.BlockedTimeAreasFragment
import io.timelimit.android.ui.manage.category.settings.CategorySettingsFragment
import io.timelimit.android.ui.manage.category.timelimit_rules.CategoryTimeLimitRulesFragment
import io.timelimit.android.ui.manage.category.usagehistory.UsageHistoryFragment
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

        bottom_navigation_view.setOnNavigationItemReselectedListener { /* ignore */ }
        bottom_navigation_view.setOnNavigationItemSelectedListener { menuItem ->
            if (childFragmentManager.isStateSaved) {
                false
            } else {
                childFragmentManager.beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .replace(R.id.container, when (menuItem.itemId) {
                            R.id.manage_category_tab_apps -> CategoryAppsFragment.newInstance(params)
                            R.id.manage_category_tab_time_limit_rules -> CategoryTimeLimitRulesFragment.newInstance(params)
                            R.id.manage_category_tab_blocked_time_areas -> BlockedTimeAreasFragment.newInstance(params)
                            R.id.manage_category_tab_usage_log -> UsageHistoryFragment.newInstance(userId = params.childId, categoryId = params.categoryId)
                            R.id.manage_category_tab_settings -> CategorySettingsFragment.newInstance(params)
                            else -> throw IllegalStateException()
                        })
                        .commit()

                true
            }
        }

        if (childFragmentManager.findFragmentById(R.id.container) == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.container, CategoryAppsFragment.newInstance(params))
                    .commit()
        }

        if (!wereViewsCreated) {
            wereViewsCreated = true

            category.observe(this, Observer {
                if (it == null) {
                    navigation.popBackStack()
                }
            })
        }
    }

    override fun getCustomTitle(): LiveData<String?> = user.switchMap { user ->
        category.map { category ->
            "${category?.title} < ${user?.name} < ${getString(R.string.main_tab_overview)}" as String?
        }
    }
}
