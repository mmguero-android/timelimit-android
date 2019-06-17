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
package io.timelimit.android.ui.authentication

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.extensions.showSafe

class SelectMailDialogFragment: DialogFragment() {
    companion object {
        private const val OPTIONS = "options"
        private const val DIALOG_TAG = "SelectMailDialogFragment"

        fun newInstance(options: List<String>, target: AuthenticateByMailFragment) = SelectMailDialogFragment().apply {
            setTargetFragment(target, 0)

            arguments = Bundle().apply {
                putStringArray(OPTIONS, options.toTypedArray())
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = arguments!!.getStringArray(OPTIONS)!!

        return AlertDialog.Builder(context!!, theme)
                .setItems(options) { _, which ->
                    val item = options[which]

                    (targetFragment as AuthenticateByMailFragment).model.sendAuthMessage(item)
                }
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}