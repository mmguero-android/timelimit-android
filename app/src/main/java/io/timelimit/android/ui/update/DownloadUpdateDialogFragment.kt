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
import android.app.ProgressDialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.update.UpdateIntegration

class DownloadUpdateDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "DownloadUpdateDialogFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val model = ViewModelProvider(this).get(DownloadUpdateDialogModel::class.java)

        model.status.observe(this, Observer { status ->
            when (status) {
                DownloadUpdateDialogModel.Status.Working -> {/* nothing to do */}
                DownloadUpdateDialogModel.Status.Success -> {
                    dismissAllowingStateLoss()
                    UpdateIntegration.installUpdate(activity!!)
                }
                DownloadUpdateDialogModel.Status.FailureHadChange -> {
                    Toast.makeText(context!!, R.string.update_toast_new_release, Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                }
                DownloadUpdateDialogModel.Status.Failure -> {
                    Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                }
            }.apply {/* require handling all paths */}
        })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = ProgressDialog(context!!, theme)

        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        dialog.setMessage(getString(R.string.update_downloading))

        return dialog
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}