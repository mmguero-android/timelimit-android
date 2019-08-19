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

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.ResetParentBlockedTimesAction
import io.timelimit.android.ui.main.ActivityViewModelHolder

class TryResetParentBlockedTimesDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "TryResetParentBlockedTimesDialogFragment"
        private const val PARENT_USER_ID = "parentUserId"

        fun newInstance(parentUserId: String) = TryResetParentBlockedTimesDialogFragment().apply {
            arguments = Bundle().apply {
                putString(PARENT_USER_ID, parentUserId)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parentUserId = arguments!!.getString(PARENT_USER_ID)!!
        val auth = (activity!! as ActivityViewModelHolder).getActivityViewModel()

        auth.authenticatedUser.observe(this, Observer {
            if (it?.second?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        return AlertDialog.Builder(context!!, theme)
                .setMessage(R.string.manage_parent_blocked_times_info)
                .setPositiveButton(R.string.manage_parent_blocked_action_reset) { _, _ ->
                    auth.tryDispatchParentAction(
                            ResetParentBlockedTimesAction(
                                    parentId = parentUserId
                            )
                    )
                }
                .setNegativeButton(R.string.generic_cancel, null)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}