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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.viewpager.widget.ViewPager
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.manage.device.add.AddDeviceFragment
import io.timelimit.android.ui.overview.about.AboutFragmentParentHandlers
import io.timelimit.android.ui.overview.overview.CanNotAddDevicesInLocalModeDialogFragmentListener
import io.timelimit.android.ui.overview.overview.OverviewFragmentParentHandlers
import kotlinx.android.synthetic.main.fragment_main.*

class MainFragment : Fragment(), OverviewFragmentParentHandlers, AboutFragmentParentHandlers,
        CanNotAddDevicesInLocalModeDialogFragmentListener {

    private val adapter: PagerAdapter by lazy { PagerAdapter(childFragmentManager) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private lateinit var navigation: NavController
    private val showAuthButtonLive = MutableLiveData<Boolean>()
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private var wereViewsCreated = false
    private var didRedirectToUserScreen = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        navigation = Navigation.findNavController(container!!)

        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AuthenticationFab.manageAuthenticationFab(
                fab = fab,
                fragment = this,
                shouldHighlight = activity.getActivityViewModel().shouldHighlightAuthenticationButton,
                authenticatedUser = activity.getActivityViewModel().authenticatedUser,
                doesSupportAuth = showAuthButtonLive
        )

        fab.setOnClickListener { activity.showAuthenticationScreen() }

        if (!wereViewsCreated) {
            wereViewsCreated = true
            logic.isInitialized.switchMap { isInitialized ->
                if (isInitialized) {
                    logic.database.config().getOwnDeviceId().map { it == null }
                } else {
                    liveDataFromValue(false)
                }
            }.observe(this, Observer { shouldShowSetup ->
                if (shouldShowSetup == true) {
                    pager.post {
                        navigation.safeNavigate(
                                MainFragmentDirections.actionOverviewFragmentToSetupTermsFragment(),
                                R.id.overviewFragment
                        )
                    }
                } else {
                    if (savedInstanceState == null && !didRedirectToUserScreen) {
                        didRedirectToUserScreen = true

                        runAsync {
                            val user = logic.deviceUserEntry.waitForNullableValue()

                            if (user?.type == UserType.Child) {
                                if (fragmentManager?.isStateSaved == false) {
                                    openManageChildScreen(user.id)
                                }
                            }
                        }
                    }
                }
            })
        }

        pager.adapter = adapter

        bottom_navigation_view.setOnNavigationItemSelectedListener {
            menuItem ->

            pager?.currentItem = when(menuItem.itemId) {
                R.id.main_tab_overview -> 0
                R.id.main_tab_uninstall -> 1
                R.id.main_tab_about -> 2
                else -> throw IllegalStateException()
            }

            true
        }

        fun updateShowFab(selectedPage: Int) {
            showAuthButtonLive.value = when (selectedPage) {
                0 -> true
                1 -> !BuildConfig.storeCompilant
                2 -> false
                else -> throw IllegalStateException()
            }
        }

        updateShowFab(pager.currentItem)

        pager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                // ignore
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // ignore
            }

            override fun onPageSelected(position: Int) {
                updateShowFab(position)

                bottom_navigation_view.selectedItemId = when(pager.currentItem) {
                    0 -> R.id.main_tab_overview
                    1 -> R.id.main_tab_uninstall
                    2 -> R.id.main_tab_about
                    else -> throw IllegalStateException()
                }
            }
        })
    }

    override fun openAddDeviceScreen() {
        AddDeviceFragment().show(fragmentManager!!)
    }

    override fun openAddUserScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToAddUserFragment(),
                R.id.overviewFragment
        )
    }

    override fun openManageChildScreen(childId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageChildFragment(childId),
                R.id.overviewFragment
        )
    }

    override fun openManageDeviceScreen(deviceId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageDeviceFragment(deviceId),
                R.id.overviewFragment
        )
    }

    override fun onShowPurchaseScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToPurchaseFragment(),
                R.id.overviewFragment
        )
    }

    override fun openManageParentScreen(parentId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageParentFragment(parentId),
                R.id.overviewFragment
        )
    }

    override fun openSetupDeviceScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToSetupDeviceFragment(),
                R.id.overviewFragment
        )
    }

    override fun onShowDiagnoseScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToDiagnoseMainFragment(),
                R.id.overviewFragment
        )
    }

    override fun migrateToConnectedMode() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToMigrateToConnectedModeFragment(),
                R.id.overviewFragment
        )
    }
}
