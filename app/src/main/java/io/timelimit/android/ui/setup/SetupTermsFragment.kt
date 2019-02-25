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
package io.timelimit.android.ui.setup


import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentSetupTermsBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.setup.customserver.SelectCustomServerDialogFragment

class SetupTermsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupTermsBinding.inflate(inflater, container, false)

        binding.btnAccept.setOnClickListener {
            acceptTerms()
        }

        binding.customServerButton.setOnClickListener {
            SelectCustomServerDialogFragment().show(fragmentManager!!)
        }

        binding.termsText1.movementMethod = LinkMovementMethod.getInstance()
        binding.termsText2.movementMethod = LinkMovementMethod.getInstance()

        DefaultAppLogic.with(context!!).database.config().getCustomServerUrlAsync().observe(this, Observer {
            if (it.isEmpty()) {
                binding.customServerStatus.setText(R.string.custom_server_status_disabled)
            } else {
                binding.customServerStatus.setText(getString(R.string.custom_server_status_enabled, it))
            }
        })

        return binding.root
    }

    private fun acceptTerms() {
        Navigation.findNavController(view!!).safeNavigate(
                SetupTermsFragmentDirections.actionSetupTermsFragmentToSetupSelectModeFragment(),
                R.id.setupTermsFragment
        )
    }
}
