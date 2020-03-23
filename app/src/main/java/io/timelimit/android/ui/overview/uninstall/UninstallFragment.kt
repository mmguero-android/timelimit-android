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
    companion object {
        private const val STATUS_SHOW_BACKDOOR_BUTTON = "show_backdoor_button"
    }

    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private var showBackdoorButton = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showBackdoorButton = savedInstanceState?.getBoolean(STATUS_SHOW_BACKDOOR_BUTTON) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentUninstallBinding.inflate(inflater, container, false)

        binding.uninstall.isEnabled = binding.checkConfirm.isChecked
        binding.checkConfirm.setOnCheckedChangeListener { _, isChecked -> binding.uninstall.isEnabled = isChecked }

        binding.uninstall.setOnClickListener {
            val revokePermissions = binding.checkPermissions.isChecked

            if (BuildConfig.storeCompilant || auth.requestAuthenticationOrReturnTrue()) {
                DefaultAppLogic.with(context!!).appSetupLogic.resetAppCompletely(revokePermissions)
            } else {
                showBackdoorButton = true
                binding.showBackdoorButton = true
            }
        }

        binding.backdoorButton.setOnClickListener {
            BackdoorDialogFragment().show(parentFragmentManager)
        }

        binding.showBackdoorButton = showBackdoorButton

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATUS_SHOW_BACKDOOR_BUTTON, showBackdoorButton)
    }
}
