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

package io.timelimit.android.ui.manage.child.tasks

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.sync.actions.MarkTaskPendingAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.getActivityViewModel

class ConfirmTaskDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "ConfirmTaskDialogFragment"
        private const val TASK_TITLE = "taskTitle"
        private const val TASK_ID = "taskId"

        fun newInstance(taskId: String, taskTitle: String) = ConfirmTaskDialogFragment().apply {
            arguments = Bundle().apply {
                putString(TASK_ID, taskId)
                putString(TASK_TITLE, taskTitle)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val taskId = requireArguments().getString(TASK_ID)!!
        val taskTitle = requireArguments().getString(TASK_TITLE)!!
        val logic = getActivityViewModel(requireActivity()).logic

        return AlertDialog.Builder(requireContext(), theme)
                .setTitle(taskTitle)
                .setMessage(R.string.lock_task_confirm_dialog)
                .setNegativeButton(R.string.generic_no, null)
                .setPositiveButton(R.string.generic_yes) { _, _ ->
                    runAsync {
                        ApplyActionUtil.applyAppLogicAction(
                                action = MarkTaskPendingAction(taskId = taskId),
                                appLogic = logic,
                                ignoreIfDeviceIsNotConfigured = true
                        )
                    }
                }
                .create()
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, DIALOG_TAG)
}