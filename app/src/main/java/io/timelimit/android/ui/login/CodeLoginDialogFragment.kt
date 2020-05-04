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
package io.timelimit.android.ui.login

import android.widget.Toast
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.manage.parent.key.ScanKeyDialogFragment
import io.timelimit.android.ui.manage.parent.key.ScannedKey

class CodeLoginDialogFragment: ScanKeyDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "CodeLoginDialogFragment"
    }

    override fun handleResult(key: ScannedKey?) {
        if (key == null) {
            Toast.makeText(context!!, R.string.manage_user_key_invalid, Toast.LENGTH_SHORT).show()
        } else {
            (targetFragment as NewLoginFragment).tryCodeLogin(key)
        }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}