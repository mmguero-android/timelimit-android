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
package io.timelimit.android.ui.main

import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.and
import io.timelimit.android.livedata.invert
import io.timelimit.android.livedata.map
import io.timelimit.android.sync.actions.apply.ApplyActionParentAuthentication

object AuthenticationFab {
    private const val LOG_TAG = "AuthenticationFab"

    fun manageAuthenticationFab(
            fab: FloatingActionButton,
            shouldHighlight: MutableLiveData<Boolean>,
            authenticatedUser: LiveData<Pair<ApplyActionParentAuthentication, User>?>,
            doesSupportAuth: LiveData<Boolean>,
            fragment: Fragment
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "start managing FAB instance")
        }

        var tapTargetView: TapTargetView? = null

        val highlightObserver = Observer<Boolean> {
            if (it == true) {
                if (tapTargetView == null && fab.isAttachedToWindow) {
                    tapTargetView = TapTargetView.showFor(fragment.activity!!,
                            TapTarget.forView(fab, fragment.getString(R.string.authentication_required_overlay_title), fragment.getString(R.string.authentication_required_overlay_text))
                                    .cancelable(true)
                                    .tintTarget(true)
                                    .transparentTarget(false)
                                    .outerCircleColor(R.color.colorAccent)
                                    .icon(ContextCompat.getDrawable(fragment.context!!, R.drawable.ic_lock_open_white_24dp)),
                            object : TapTargetView.Listener() {
                                override fun onTargetClick(view: TapTargetView) {
                                    super.onTargetClick(view)

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "target clicked")
                                    }

                                    tapTargetView = null
                                    fab.callOnClick()
                                }

                                override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                    super.onTargetDismissed(view, userInitiated)

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "target dismissed")
                                    }

                                    tapTargetView = null
                                    shouldHighlight.value = false
                                }
                            })
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "dismissing tap target view; view == null: ${tapTargetView == null}")
                }

                tapTargetView?.let {
                    if (it.isLaidOut) {
                        it.dismiss(false)
                    }
                }

                tapTargetView = null
            }
        }

        val isParentAuthenticated = authenticatedUser.map { it != null && it.second.type == UserType.Parent }
        val shouldShowFab = (isParentAuthenticated.invert()).and(doesSupportAuth)

        val showFabObserver = Observer<Boolean> {
            if (it == true) {
                fab.show()
            } else {
                fab.hide()
            }
        }

        shouldHighlight.observe(fragment, highlightObserver)
        shouldShowFab.observe(fragment, showFabObserver)
    }
}
