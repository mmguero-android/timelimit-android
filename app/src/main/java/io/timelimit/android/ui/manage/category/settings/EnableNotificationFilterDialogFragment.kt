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

package io.timelimit.android.ui.manage.category.settings

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.UpdateCategoryBlockAllNotificationsAction
import io.timelimit.android.ui.main.ActivityViewModelHolder

class EnableNotificationFilterDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "EnableNotificationFilterDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String) = EnableNotificationFilterDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val auth = activity as ActivityViewModelHolder
        val childId = arguments!!.getString(CHILD_ID)!!
        val categoryId = arguments!!.getString(CATEGORY_ID)!!

        auth.getActivityViewModel().authenticatedUserOrChild.observe(this, Observer {
            if (it == null || (it.second.type != UserType.Parent && it.second.id != childId)) {
                dismissAllowingStateLoss()
            }
        })

        return AlertDialog.Builder(requireContext(), theme)
                .setTitle(R.string.category_notification_filter_title)
                .setMessage(R.string.category_notifications_filter_dialog)
                .setNegativeButton(R.string.generic_cancel, null)
                .setPositiveButton(R.string.generic_enable) { _, _ ->
                    auth.getActivityViewModel().tryDispatchParentAction(
                            action = UpdateCategoryBlockAllNotificationsAction(
                                    categoryId = categoryId,
                                    blocked = true
                            ),
                            allowAsChild = true
                    )
                }
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}