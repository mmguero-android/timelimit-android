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
package io.timelimit.android.ui.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.viewpager.widget.ViewPager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.manage.child.primarydevice.UpdatePrimaryDeviceDialogFragment
import io.timelimit.android.ui.util.SyncStatusModel
import kotlinx.android.synthetic.main.lock_activity.*

class LockActivity : AppCompatActivity(), ActivityViewModelHolder {
    companion object {
        private const val EXTRA_PACKAGE_NAME = "pkg"
        private const val EXTRA_ACTIVITY_NAME = "an"
        private const val LOGIN_DIALOG_TAG = "ldt"

        val currentInstances = mutableSetOf<LockActivity>()

        fun start(context: Context, packageName: String, activityName: String?) {
            context.startActivity(
                    Intent(context, LockActivity::class.java)
                            .putExtra(EXTRA_PACKAGE_NAME, packageName)
                            .apply {
                                if (activityName != null) {
                                    putExtra(EXTRA_ACTIVITY_NAME, activityName)
                                }
                            }
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    private val model: LockModel by viewModels()
    private val syncModel: SyncStatusModel by viewModels()
    private val activityModel: ActivityViewModel by viewModels()
    private var isResumed = false

    override var ignoreStop: Boolean = false

    private val blockedPackageName: String by lazy {
        intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
    }

    private val blockedActivityName: String? by lazy {
        if (intent.hasExtra(EXTRA_ACTIVITY_NAME))
            intent.getStringExtra(EXTRA_ACTIVITY_NAME)
        else
            null
    }

    private val showAuth = MutableLiveData<Boolean>().apply { value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val adapter = LockActivityAdapter(supportFragmentManager, this)

        setContentView(R.layout.lock_activity)

        if (savedInstanceState == null) {
            stopMediaPlayback()
        }

        syncModel.statusText.observe(this) { supportActionBar?.subtitle = it }

        currentInstances.add(this)

        model.init(blockedPackageName, blockedActivityName)

        pager.adapter = adapter

        model.content.observe(this) {
            if (isResumed && it is LockscreenContent.Blocked.BlockedCategory && it.reason == BlockingReason.RequiresCurrentDevice && !model.didOpenSetCurrentDeviceScreen) {
                model.didOpenSetCurrentDeviceScreen = true

                UpdatePrimaryDeviceDialogFragment
                        .newInstance(UpdatePrimaryDeviceRequestType.SetThisDevice)
                        .show(supportFragmentManager)
            }
        }

        AuthenticationFab.manageAuthenticationFab(
                fab = fab,
                shouldHighlight = activityModel.shouldHighlightAuthenticationButton,
                authenticatedUser = activityModel.authenticatedUser,
                activity = this,
                doesSupportAuth = showAuth
        )

        fab.setOnClickListener { showAuthenticationScreen() }

        pager.addOnPageChangeListener(object: ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                showAuth.value = position == 1
            }
        })

        tabs.setupWithViewPager(pager)

        model.content.observe(this) {
            val isTimeOver = it is LockscreenContent.Blocked.BlockedCategory && it.blockingHandling.activityBlockingReason == BlockingReason.TimeOver

            adapter.showTasksFragment = isTimeOver
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        currentInstances.remove(this)
    }

    override fun getActivityViewModel(): ActivityViewModel = activityModel

    override fun showAuthenticationScreen() {
        NewLoginFragment().showSafe(supportFragmentManager, LOGIN_DIALOG_TAG)
    }

    override fun onResume() {
        super.onResume()

        lockTaskModeWorkaround()
        isResumed = true
    }

    override fun onPause() {
        super.onPause()

        lockTaskModeWorkaround()
        isResumed = false
    }

    override fun onStart() {
        super.onStart()

        IsAppInForeground.reportStart()
    }

    override fun onStop() {
        super.onStop()

        if ((!isChangingConfigurations) && (!ignoreStop)) {
            getActivityViewModel().logOut()
        }

        IsAppInForeground.reportStop()
    }

    private fun lockTaskModeWorkaround() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val platformIntegration = DefaultAppLogic.with(this).platformIntegration
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
            else
                activityManager.isInLockTaskMode

            if (isLocked) {
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), true)
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), false)
            }
        }
    }

    private fun stopMediaPlayback() {
        val platformIntegration = DefaultAppLogic.with(this).platformIntegration
        platformIntegration.muteAudioIfPossible(blockedPackageName)
    }

    override fun onBackPressed() {
        // do nothing because going back would open the blocked app again
        // super.onBackPressed()
    }
}
