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
package io.timelimit.android.ui.setup.child

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.databinding.SetupRemoteChildFragmentBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.ui.overview.main.MainFragmentDirections

class SetupRemoteChildFragment : Fragment() {
    private val model: SetupRemoteChildViewModel by lazy {
        ViewModelProviders.of(this).get(SetupRemoteChildViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = SetupRemoteChildFragmentBinding.inflate(inflater, container, false)

        binding.btnOk.setOnClickListener {
            model.trySetup(binding.editCode.text.toString())
        }

        model.status.observe(this, Observer {
            status ->

            when (status) {
                SetupRemoteChildStatus.Idle -> binding.flipper.displayedChild = 0
                SetupRemoteChildStatus.Working -> binding.flipper.displayedChild = 1
                SetupRemoteChildStatus.CodeInvalid -> {
                    Snackbar.make(container!!, R.string.setup_remote_child_code_invalid, Snackbar.LENGTH_SHORT).show()

                    model.confirmError()
                }
                SetupRemoteChildStatus.NetworkError -> {
                    Snackbar.make(container!!, R.string.error_network, Snackbar.LENGTH_SHORT).show()

                    model.confirmError()
                }
                null -> {/* nothing to do */}
            }.let {  }
        })

        model.isSetupDone.observe(this, Observer {
            if (it!!) {
                val navigation = Navigation.findNavController(binding.root)

                navigation.popBackStack(R.id.overviewFragment, false)
                navigation.safeNavigate(
                        MainFragmentDirections.actionOverviewFragmentToSetupDeviceFragment(),
                        R.id.overviewFragment
                )
            }
        })

        return binding.root
    }
}
