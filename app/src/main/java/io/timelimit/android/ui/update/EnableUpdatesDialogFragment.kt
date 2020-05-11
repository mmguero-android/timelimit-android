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

package io.timelimit.android.ui.update

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.update.UpdateIntegration
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.update.UpdateUtil
import io.timelimit.android.work.CheckUpdateWorker

class EnableUpdatesDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "EnableUpdatesDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(context!!, theme)
            .setMessage(getString(R.string.update_privacy, UpdateIntegration.CONFIG_URL))
            .setPositiveButton(R.string.update_privacy_enable) { _, _ ->
                UpdateUtil.enableChecks(context!!)
            }
            .setNegativeButton(R.string.generic_cancel) { _, _ ->
                UpdateUtil.disableChecks(context!!)
            }
            .create()

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}