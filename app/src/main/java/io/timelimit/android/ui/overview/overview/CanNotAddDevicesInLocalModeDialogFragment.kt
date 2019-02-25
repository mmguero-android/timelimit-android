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
package io.timelimit.android.ui.overview.overview

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe

class CanNotAddDevicesInLocalModeDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "h"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context!!, theme)
                .setTitle(R.string.overview_add_device)
                .setMessage(R.string.overview_add_error_local_mode)
                .apply {
                    if (BuildConfig.hasServer) {
                        setNegativeButton(R.string.generic_cancel, null)
                        setPositiveButton(R.string.overview_add_device_migrate_to_connected) { _, _ ->
                            dismiss()

                            targetFragment.let { target ->
                                if (target is CanNotAddDevicesInLocalModeDialogFragmentListener) {
                                    target.migrateToConnectedMode()
                                }
                            }
                        }
                    } else {
                        setPositiveButton(R.string.generic_ok, null)
                    }
                }
                .create()
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}

interface CanNotAddDevicesInLocalModeDialogFragmentListener {
    fun migrateToConnectedMode()
}