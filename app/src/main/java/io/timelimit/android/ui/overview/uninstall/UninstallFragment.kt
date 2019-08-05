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
package io.timelimit.android.ui.overview.uninstall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.timelimit.android.BuildConfig
import io.timelimit.android.databinding.FragmentUninstallBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.backdoor.BackdoorDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel

class UninstallFragment : Fragment() {
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentUninstallBinding.inflate(inflater, container, false)

        binding.uninstall.isEnabled = binding.checkConfirm.isChecked
        binding.checkConfirm.setOnCheckedChangeListener { _, isChecked -> binding.uninstall.isEnabled = isChecked }

        binding.uninstall.setOnClickListener { reset(revokePermissions = binding.checkPermissions.isChecked) }

        binding.checkConfirm.setOnLongClickListener {
            BackdoorDialogFragment().show(fragmentManager!!)

            true
        }

        return binding.root
    }

    private fun reset(revokePermissions: Boolean) {
        if (BuildConfig.storeCompilant || auth.requestAuthenticationOrReturnTrue()) {
            DefaultAppLogic.with(context!!).appSetupLogic.resetAppCompletely(revokePermissions)
        }
    }
}
