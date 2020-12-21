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
package io.timelimit.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import io.timelimit.android.Application
import io.timelimit.android.R
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.parent.ManageParentFragmentArgs
import io.timelimit.android.ui.manage.parent.link.LinkParentMailFragment
import io.timelimit.android.ui.manage.parent.password.restore.RestoreParentPasswordFragment
import io.timelimit.android.ui.overview.main.MainFragment
import io.timelimit.android.ui.parentmode.ParentModeFragment
import io.timelimit.android.ui.payment.ActivityPurchaseModel
import io.timelimit.android.ui.setup.SetupTermsFragment
import io.timelimit.android.ui.setup.parent.SetupParentModeFragment
import io.timelimit.android.ui.util.SyncStatusModel
import org.solovyev.android.checkout.ActivityCheckout
import org.solovyev.android.checkout.Checkout

class MainActivity : AppCompatActivity(), ActivityViewModelHolder {
    companion object {
        private const val AUTH_DIALOG_TAG = "adt"
        const val ACTION_USER_OPTIONS = "OPEN_USER_OPTIONS"
        const val EXTRA_USER_ID = "userId"
    }

    private val currentNavigatorFragment = MutableLiveData<Fragment?>()
    private val application: Application by lazy { getApplication() as Application }
    private val checkout: ActivityCheckout by lazy { Checkout.forActivity(this, application.billing) }
    private val syncModel: SyncStatusModel by lazy {
        ViewModelProviders.of(this).get(SyncStatusModel::class.java)
    }
    val purchaseModel: ActivityPurchaseModel by lazy {
        ViewModelProviders.of(this).get(ActivityPurchaseModel::class.java).apply {
            setActivityCheckout(checkout)
        }
    }
    override var ignoreStop: Boolean = false
    override val showPasswordRecovery: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            NavHostFragment.create(R.navigation.nav_graph).let { navhost ->
                supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host, navhost)
                        .setPrimaryNavigationFragment(navhost)
                        .commitNow()
            }
        }

        // init the purchaseModel
        purchaseModel.getApplication<Application>()

        // prepare livedata
        val customTitle = currentNavigatorFragment.switchMap {
            if (it != null && it is FragmentWithCustomTitle) {
                it.getCustomTitle()
            } else {
                liveDataFromValue(null as String?)
            }
        }.ignoreUnchanged()

        val title = Transformations.map(customTitle) {
            if (it == null) {
                getString(R.string.app_name)
            } else {
                it
            }
        }

        // up button
        val shouldShowBackButtonForNavigatorFragment = currentNavigatorFragment.map { fragment ->
            (!(fragment is MainFragment)) && (!(fragment is SetupTermsFragment)) && (!(fragment is ParentModeFragment))
        }

        val shouldShowUpButton = shouldShowBackButtonForNavigatorFragment

        shouldShowUpButton.observe(this, Observer { supportActionBar!!.setDisplayHomeAsUpEnabled(it) })

        // init if not yet done
        DefaultAppLogic.with(this)

        val fragmentContainer = supportFragmentManager.findFragmentById(R.id.nav_host)!!
        val fragmentContainerManager = fragmentContainer.childFragmentManager

        fragmentContainerManager.registerFragmentLifecycleCallbacks(object: FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                super.onFragmentStarted(fm, f)

                if (!(f is DialogFragment)) {
                    currentNavigatorFragment.value = f
                }
            }

            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                super.onFragmentStopped(fm, f)

                if (currentNavigatorFragment.value === f) {
                    currentNavigatorFragment.value = null
                }
            }
        }, false)

        title.observe(this, Observer { setTitle(it) })
        syncModel.statusText.observe(this, Observer { supportActionBar!!.subtitle = it })

        handleParameters(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        purchaseModel.queryAndProcessPurchasesAsync()

        IsAppInForeground.reportStart()

        syncModel.handleStart()
    }

    override fun onStop() {
        super.onStop()

        if ((!isChangingConfigurations) && (!ignoreStop)) {
            getActivityViewModel().logOut()
        }

        IsAppInForeground.reportStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        purchaseModel.forgetActivityCheckout()
    }

    private fun handleParameters(intent: Intent?): Boolean {
        if (intent?.action == ACTION_USER_OPTIONS) {
            val userId = intent.getStringExtra(EXTRA_USER_ID)
            val valid = userId != null && try { IdGenerator.assertIdValid(userId); true } catch (ex: IllegalArgumentException) {false}

            if (userId != null && valid) {
                getNavController().popBackStack(R.id.overviewFragment, true)
                getNavController().handleDeepLink(
                        getNavController().createDeepLink()
                                .setDestination(R.id.manageParentFragment)
                                .setArguments(ManageParentFragmentArgs(userId).toBundle())
                                .createTaskStackBuilder()
                                .intents
                                .first()
                )

                return true
            }
        }

        return false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT == Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) {
            return
        }

        if (handleParameters(intent)) return

        val currentFragment = currentNavigatorFragment.value

        // at these screens, some users restart the App
        // if they want to continue after opening the mail
        // because they don't understand how to use the list of running Apps ...
        // Due to that, on the relevant screens, the App does not
        // go back to the start when opening it again
        if (
                currentFragment is SetupParentModeFragment ||
                currentFragment is RestoreParentPasswordFragment ||
                currentFragment is LinkParentMailFragment
        ) {
            return
        }

        getNavController().popBackStack(R.id.overviewFragment, true)
        getNavController().handleDeepLink(
                getNavController().createDeepLink()
                        .setDestination(R.id.overviewFragment)
                        .createTaskStackBuilder()
                        .intents
                        .first()
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        checkout.onActivityResult(requestCode, resultCode, data)
    }

    override fun getActivityViewModel(): ActivityViewModel {
        return ViewModelProviders.of(this).get(ActivityViewModel::class.java)
    }

    private fun getNavHostFragment(): NavHostFragment {
        return supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
    }

    private fun getNavController(): NavController {
        return getNavHostFragment().navController
    }

    override fun onBackPressed() {
        if (currentNavigatorFragment.value is SetupTermsFragment || currentNavigatorFragment.value is ParentModeFragment) {
            // hack to prevent the user from going to the launch screen of the App if it is not set up
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun showAuthenticationScreen() {
        if (supportFragmentManager.findFragmentByTag(AUTH_DIALOG_TAG) == null) {
            NewLoginFragment().showSafe(supportFragmentManager, AUTH_DIALOG_TAG)
        }
    }
}
