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
package io.timelimit.android.ui.setup.privacy

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic

class PrivacyInfoDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "PrivacyInfoDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val logic = DefaultAppLogic.with(context!!)
        val usesDefaultServerLive = logic.database.config().getCustomServerUrlAsync().map { it.isEmpty() }

        val dialog = AlertDialog.Builder(context!!, theme)
                .setTitle(R.string.setup_privacy_connected_title)
                .setMessage(R.string.setup_privacy_connected_text_general_intro)
                .setPositiveButton(R.string.terms_btn_accept) { _, _ ->
                    targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, null)
                }
                .setNegativeButton(R.string.generic_cancel, null)
                .create()

        usesDefaultServerLive.observe(this, Observer { usesDefaultServer ->
            val messageParts = listOf(
                    getString(R.string.setup_privacy_connected_text_general_intro),
                    getString(
                            if (usesDefaultServer) R.string.setup_privacy_connected_text_default_server else R.string.setup_privacy_connected_text_custom_server
                    ),
                    getString(R.string.setup_privacy_connected_text_general_outro)
            )

            dialog.setMessage(messageParts.joinToString(separator = "\n"))
        })

        return dialog
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}