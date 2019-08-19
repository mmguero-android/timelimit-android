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
package io.timelimit.android.ui.manage.parent.blockedtimes

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.withConfigCopiedToOtherDates
import io.timelimit.android.databinding.ManageParentBlockedTimesFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateParentBlockedTimesAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.category.blocked_times.*
import kotlinx.android.synthetic.main.fragment_blocked_time_areas.*
import java.util.*

class ManageParentBlockedTimesFragment : Fragment(), FragmentWithCustomTitle, CopyBlockedTimeAreasDialogFragmentListener {
    companion object {
        private const val MINUTES_PER_DAY = 60 * 24
        private const val MAX_BLOCKED_MINUTES_PER_DAY = 60 * 18 + 1
    }

    private val params: ManageParentBlockedTimesFragmentArgs by lazy {
        ManageParentBlockedTimesFragmentArgs.fromBundle(arguments!!)
    }

    private val authActivity: ActivityViewModelHolder by lazy {
        activity!! as ActivityViewModelHolder
    }

    private val auth: ActivityViewModel by lazy {
        authActivity.getActivityViewModel()
    }

    private val parent: LiveData<User?> by lazy {
        DefaultAppLogic.with(context!!).database.user().getParentUserByIdLive(params.parentUserId)
    }

    override fun getCustomTitle(): LiveData<String?> = parent.map { it?.name }

    override fun onCopyBlockedTimeAreasConfirmed(sourceDay: Int, targetDays: Set<Int>) {
        parent.value?.blockedTimes?.let { current ->
            updateBlockedTimes(current, current.withConfigCopiedToOtherDates(sourceDay, targetDays))
        }
    }

    private fun validateBlockedTimeAreas(newMask: BitSet): Boolean {
        for (day in 0 until 7) {
            var blocked = 0

            for (minute in 0 until MINUTES_PER_DAY) {
                if (newMask[day * MINUTES_PER_DAY + minute]) {
                    blocked++
                }
            }

            if (blocked >= MAX_BLOCKED_MINUTES_PER_DAY) {
                return false
            }
        }

        return true
    }

    private fun updateBlockedTimes(oldMask: ImmutableBitmask, newMask: ImmutableBitmask) {
        if (!validateBlockedTimeAreas(newMask.dataNotToModify)) {
            Snackbar.make(coordinator, R.string.manage_parent_lockout_hour_rule, Snackbar.LENGTH_LONG).show()

            return
        }

        if (
                auth.tryDispatchParentAction(
                        UpdateParentBlockedTimesAction(
                                parentId = params.parentUserId,
                                blockedTimes = newMask
                        )
                )
        ) {
            Snackbar.make(coordinator, R.string.blocked_time_areas_snackbar_modified, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.generic_undo) {
                        auth.tryDispatchParentAction(
                                UpdateParentBlockedTimesAction(
                                        parentId = params.parentUserId,
                                        blockedTimes = oldMask
                                )
                        )
                    }
                    .show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ManageParentBlockedTimesFragmentBinding.inflate(inflater, container, false)

        // auth button
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                fragment = this,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                doesSupportAuth = liveDataFromValue(true)
        )

        binding.fab.setOnClickListener { authActivity.showAuthenticationScreen() }

        // dispatching
        fun requestAuthenticationOrReturnTrue(): Boolean {
            if (!auth.requestAuthenticationOrReturnTrue()) {
                return false
            }

            val authenticatedUser = auth.authenticatedUser.value?.second?.id ?: return false
            val targetUser = params.parentUserId

            if (authenticatedUser == targetUser) {
                return true
            } else {
                TryResetParentBlockedTimesDialogFragment.newInstance(parentUserId = params.parentUserId).show(fragmentManager!!)

                return false
            }
        }

        // UI
        binding.btnHelp.setOnClickListener {
            BlockedTimeAreasHelpDialog.newInstance(forUser = true).show(fragmentManager!!)
        }

        binding.btnCopyToOtherDays.setOnClickListener {
            if (requestAuthenticationOrReturnTrue()) {
                CopyBlockedTimeAreasDialogFragment.newInstance(this@ManageParentBlockedTimesFragment).show(fragmentManager!!)
            }
        }

        BlockedTimeAreasLogic.init(
                recycler = binding.recycler,
                daySpinner = binding.spinnerDay,
                detailedModeCheckbox = binding.detailedMode,
                requestAuthenticationOrReturnTrue = { requestAuthenticationOrReturnTrue() },
                updateBlockedTimes = { a, b -> updateBlockedTimes(a, b) },
                currentData = parent.map { it?.blockedTimes },
                lifecycleOwner = this
        )

        return binding.root
    }
}
